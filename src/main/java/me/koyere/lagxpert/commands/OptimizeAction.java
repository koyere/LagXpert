package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.monitoring.TPSMonitor;
import me.koyere.lagxpert.system.*;
import me.koyere.lagxpert.tasks.EntityCleanupTask;
import me.koyere.lagxpert.tasks.InactiveChunkUnloader;
import me.koyere.lagxpert.tasks.ItemCleanerTask;
import me.koyere.lagxpert.utils.ChunkUtils;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles the /lagxpert optimize command.
 * Runs a full optimization pass across all corrective systems
 * and reports before/after metrics.
 */
public class OptimizeAction {

    /** Name of the cache phase, which is housekeeping rather than a removal. */
    private static final String CACHE_PHASE = "Cache Clear";

    /**
     * Result container for a single optimization phase.
     */
    public static class PhaseResult {
        final String name;
        final int count;
        final long durationMs;

        PhaseResult(String name, int count, long durationMs) {
            this.name = name;
            this.count = count;
            this.durationMs = durationMs;
        }
    }

    /**
     * Executes the full optimization pass.
     *
     * @param sender The command sender (player or console)
     * @return true if optimization completed
     */
    public static boolean execute(CommandSender sender) {
        if (!sender.hasPermission("lagxpert.admin")
                && !sender.hasPermission("lagxpert.admin.optimize")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
            return true;
        }

        sender.sendMessage(MessageManager.color("&8&m------------------------------------------"));
        sender.sendMessage(MessageManager.color("&b&lLagXpert Optimize &8- &7Full optimization pass"));
        sender.sendMessage(MessageManager.color("&8&m------------------------------------------"));

        // Snapshot BEFORE. The entity count is gathered region-safely and therefore
        // asynchronously, so the rest of the pass runs inside its callback.
        double tpsBefore = TPSMonitor.getCurrentTPS();
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        int chunksBefore = getTotalLoadedChunks();

        countEntities(entitiesBefore ->
                runPhases(sender, tpsBefore, memBefore, chunksBefore, entitiesBefore));
        return true;
    }

    /**
     * Runs the five optimization phases in order, then reports.
     */
    private static void runPhases(CommandSender sender, double tpsBefore, long memBefore,
                                  int chunksBefore, int entitiesBefore) {

        long totalStart = System.currentTimeMillis();
        Map<String, PhaseResult> results = new LinkedHashMap<>();

        // The cleanup phases sweep chunk by chunk and therefore complete
        // asynchronously. They are chained so each begins only after the previous
        // one has finished, which keeps the reported counts attributable to the
        // right phase and avoids three concurrent sweeps competing for the same
        // per-cycle budgets.
        //
        // Phase 1: Smart Mob Removal (synchronous, per chunk)
        results.put("Mob Removal", runMobCleanup(sender));

        // Phase 2: Entity Cleanup (asynchronous sweep)
        runEntityCleanup(sender, entityResult -> {
            results.put("Entity Cleanup", entityResult);

            // Phase 3: Item Cleanup (asynchronous sweep)
            runItemCleanup(sender, itemResult -> {
                results.put("Item Cleanup", itemResult);

                // Phase 4: Chunk Unloading
                results.put("Chunk Unload", runChunkUnload(sender));
                // Phase 5: Cache Clear
                results.put("Cache Clear", runCacheClear(sender));

                // The "after" entity count is also gathered region-safely.
                countEntities(entitiesAfter -> reportResults(sender, results, totalStart,
                        tpsBefore, memBefore, chunksBefore, entitiesBefore, entitiesAfter));
            });
        });
    }

    /**
     * Prints the phase table and before/after comparison once every phase has
     * finished, then records the audit entry.
     */
    private static void reportResults(CommandSender sender, Map<String, PhaseResult> results,
                                      long totalStart, double tpsBefore, long memBefore,
                                      int chunksBefore, int entitiesBefore, int entitiesAfter) {

        long totalDuration = System.currentTimeMillis() - totalStart;

        // Snapshot AFTER
        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        int chunksAfter = getTotalLoadedChunks();

        // Display results
        sender.sendMessage("");
        sender.sendMessage(MessageManager.color("&e&lResults:"));

        // Only phases that actually removed something from the world count as
        // corrective actions. Clearing an internal cache is housekeeping, not
        // optimization: counting its entries used to inflate the total to numbers
        // like "141 actions" on a server where nothing was removed at all.
        int totalActions = 0;
        for (Map.Entry<String, PhaseResult> entry : results.entrySet()) {
            PhaseResult pr = entry.getValue();
            boolean counts = !CACHE_PHASE.equals(pr.name);

            if (pr.count > 0) {
                sender.sendMessage(MessageManager.color(
                        "  &7\u2022 &f" + pr.name + ": &a" + pr.count +
                                (counts ? "" : " &8(cache entries, not removals)") +
                                " &7(" + pr.durationMs + "ms)"));
            } else {
                sender.sendMessage(MessageManager.color(
                        "  &7\u2022 &f" + pr.name + ": &7nothing to remove"));
            }

            if (counts) {
                totalActions += pr.count;
            }
        }

        sender.sendMessage("");
        sender.sendMessage(MessageManager.color("&e&lBefore \u2192 After:"));

        // Colour by whether the value actually improved. Painting every "after"
        // value green regardless of direction made a worse result look like a win.
        sender.sendMessage(MessageManager.color(
                "  &7Chunks: &f" + chunksBefore + " &7\u2192 " +
                        deltaColor(chunksAfter, chunksBefore, true) + chunksAfter +
                        " &8(" + signed(chunksAfter - chunksBefore) + ")"));
        sender.sendMessage(MessageManager.color(
                "  &7Entities: &f" + entitiesBefore + " &7\u2192 " +
                        deltaColor(entitiesAfter, entitiesBefore, true) + entitiesAfter +
                        " &8(" + signed(entitiesAfter - entitiesBefore) + ")"));
        // Heap usage is reported neutrally, not as success or failure. Removing
        // objects makes them garbage but does not collect them, so used memory
        // routinely rises during a pass and only drops at the next GC. Colouring
        // that red made a normal outcome look like a regression.
        sender.sendMessage(MessageManager.color(
                "  &7Memory: &f" + (memBefore / 1024 / 1024) + "MB &7\u2192 &f" +
                        (memAfter / 1024 / 1024) + "MB &8(drops at next GC, not instantly)"));

        // TPS is a rolling average, so it cannot move within the span of this
        // command. Saying so is more useful than showing a meaningless delta.
        sender.sendMessage(MessageManager.color(String.format(
                "  &7TPS: &f%.2f &8(rolling average, allow a minute)", tpsBefore)));

        sender.sendMessage("");
        if (totalActions > 0) {
            sender.sendMessage(MessageManager.color(
                    "&aOptimization complete. &7Removed &e" + totalActions +
                            " &7object(s) in &e" + totalDuration + "ms"));
        } else {
            sender.sendMessage(MessageManager.color(
                    "&7Optimization complete. &fThere was nothing removable. &8(" + totalDuration + "ms)"));
            explainWhyNothingWasRemoved(sender);
        }

        // Log to audit trail
        ActionLogger.getInstance().log(
                ActionLogger.ActionType.MANUAL_OPTIMIZE,
                null, null,
                "Full optimization: " + totalActions + " actions in " + totalDuration + "ms",
                totalActions, "player:" + sender.getName(), true, totalDuration);

        sender.sendMessage(MessageManager.color("&8&m------------------------------------------"));
    }

    private static PhaseResult runMobCleanup(CommandSender sender) {
        long start = System.currentTimeMillis();
        int removed = 0;
        if (ConfigManager.isMobsModuleEnabled() && ConfigManager.isAutoMobRemovalEnabled()) {
            sender.sendMessage(MessageManager.color("&7Running mob cleanup..."));
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                    removed += SmartMobManager.getInstance().processChunkImmediately(chunk);
                }
            }
        }
        long duration = System.currentTimeMillis() - start;
        return new PhaseResult("Mob Removal", removed, duration);
    }

    /**
     * Runs the entity cleanup phase, reporting its result through the callback
     * once the chunk-by-chunk sweep has completed.
     */
    private static void runEntityCleanup(CommandSender sender,
                                         java.util.function.Consumer<PhaseResult> onComplete) {
        if (!ConfigManager.isEntityCleanupModuleEnabled()) {
            onComplete.accept(new PhaseResult("Entity Cleanup", 0, 0L));
            return;
        }

        sender.sendMessage(MessageManager.color("&7Running entity cleanup..."));
        long start = System.currentTimeMillis();

        EntityCleanupTask.sweep(removed -> onComplete.accept(new PhaseResult(
                "Entity Cleanup", removed, System.currentTimeMillis() - start)));
    }

    /**
     * Runs the item cleanup phase, reporting its result through the callback
     * once the chunk-by-chunk sweep has completed.
     */
    private static void runItemCleanup(CommandSender sender,
                                       java.util.function.Consumer<PhaseResult> onComplete) {
        if (!ConfigManager.isItemCleanerModuleEnabled()) {
            onComplete.accept(new PhaseResult("Item Cleanup", 0, 0L));
            return;
        }

        sender.sendMessage(MessageManager.color("&7Running item cleanup..."));
        long start = System.currentTimeMillis();

        ItemCleanerTask.runManualCleanupAllWorlds(null, removed -> onComplete.accept(
                new PhaseResult("Item Cleanup", removed, System.currentTimeMillis() - start)));
    }

    private static PhaseResult runChunkUnload(CommandSender sender) {
        long start = System.currentTimeMillis();
        int unloaded = 0;
        if (ConfigManager.isChunkManagementModuleEnabled() && ConfigManager.isAutoUnloadEnabled()) {
            sender.sendMessage(MessageManager.color("&7Running chunk unload..."));
            // Runs inline rather than via triggerManualUnload(), which dispatches
            // asynchronously and therefore cannot report a count.
            unloaded = InactiveChunkUnloader.runImmediate();
        }
        long duration = System.currentTimeMillis() - start;
        return new PhaseResult("Chunk Unload", unloaded, duration);
    }

    private static PhaseResult runCacheClear(CommandSender sender) {
        long start = System.currentTimeMillis();
        sender.sendMessage(MessageManager.color("&7Clearing caches..."));

        // Report the number of cached entries actually dropped rather than a
        // hardcoded 1, which used to make "total actions" never read zero.
        int cleared = 0;
        try {
            Object entries = ChunkUtils.getCacheStatistics().get("total_entries");
            if (entries instanceof Number) {
                cleared = ((Number) entries).intValue();
            }
        } catch (Exception ignored) {
            // Statistics are informational only.
        }

        ChunkUtils.clearAllCache();
        long duration = System.currentTimeMillis() - start;

        if (cleared > 0) {
            ActionLogger.getInstance().log(
                    ActionLogger.ActionType.CACHE_CLEARED,
                    null, null,
                    "Chunk analysis cache cleared during manual optimization",
                    cleared, "player:" + sender.getName(), true, duration);
        }

        return new PhaseResult("Cache Clear", cleared, duration);
    }

    /**
     * Explains why an optimization pass removed nothing.
     *
     * "Nothing needed doing" is misleading when {@code /lagxpert diagnose} is
     * simultaneously reporting problem chunks. The distinction is that optimize
     * only removes things the plugin owns the right to remove: excess mobs, broken
     * or abandoned entities, ground items, and inactive chunks. It deliberately
     * never deletes player-placed blocks, so a chunk that is over its furnace or
     * hopper limit cannot be "optimized" away — it needs either a stricter limit
     * or a human decision.
     *
     * Rather than leaving the operator to work that out, this reads the last
     * diagnostics report and says what the outstanding problems actually are.
     */
    private static void explainWhyNothingWasRemoved(CommandSender sender) {
        LagDiagnosticsEngine.DiagnosticsReport report =
                LagDiagnosticsEngine.getInstance().getLastReport();

        if (report == null || report.getRankedChunks().isEmpty()) {
            sender.sendMessage(MessageManager.color(
                    "&7Nothing was over its limits, so there was nothing to clean up."));
            sender.sendMessage(MessageManager.color(
                    "&7If the server still feels slow, run &e/lagxpert diagnose&7."));
            return;
        }

        // Tally which metrics are over limit across the report.
        Map<String, Integer> blockViolations = new LinkedHashMap<>();
        int chunksWithViolations = 0;

        for (LagDiagnosticsEngine.ChunkDiagnostic chunk : report.getRankedChunks()) {
            java.util.List<LagDiagnosticsEngine.MetricFinding> violations = chunk.getViolations();
            if (!violations.isEmpty()) {
                chunksWithViolations++;
            }
            for (LagDiagnosticsEngine.MetricFinding violation : violations) {
                // Mobs and loose entities are removable; blocks are not.
                if (!violation.getMetric().equals("mobs") && !violation.getMetric().equals("entities")) {
                    blockViolations.merge(violation.getMetric(), 1, Integer::sum);
                }
            }
        }

        if (blockViolations.isEmpty()) {
            sender.sendMessage(MessageManager.color(
                    "&7Everything removable was already within limits."));
            sender.sendMessage(MessageManager.color(
                    "&7Run &e/lagxpert diagnose &7for the full picture."));
            return;
        }

        sender.sendMessage(MessageManager.color(
                "&e&lWhy nothing was removed:"));
        sender.sendMessage(MessageManager.color(
                "&7Optimize only removes excess mobs, broken entities, ground items"));
        sender.sendMessage(MessageManager.color(
                "&7and inactive chunks. It never deletes player-placed blocks."));
        sender.sendMessage("");
        sender.sendMessage(MessageManager.color(
                "&7Your outstanding problems are block-based, in &f" +
                        chunksWithViolations + " &7chunk(s):"));

        int shown = 0;
        for (Map.Entry<String, Integer> entry : blockViolations.entrySet()) {
            if (shown >= 5) {
                break;
            }
            sender.sendMessage(MessageManager.color(
                    "  &7\u2022 &c" + entry.getKey() + " &7over limit in &f" +
                            entry.getValue() + " &7chunk(s)"));
            shown++;
        }

        sender.sendMessage("");
        sender.sendMessage(MessageManager.color(
                "&7Fix those by lowering the limit (&e/lagxpert profile aggressive&7),"));
        sender.sendMessage(MessageManager.color(
                "&7or find and deal with the builds: &e/lagxpert diagnose"));
    }

    /**
     * Picks a colour based on whether a value moved in the desired direction.
     *
     * @param lowerIsBetter true when a decrease represents an improvement
     */
    private static String deltaColor(long after, long before, boolean lowerIsBetter) {
        if (after == before) {
            return "&7";
        }
        boolean improved = lowerIsBetter ? after < before : after > before;
        return improved ? "&a" : "&c";
    }

    private static String signed(long delta) {
        return delta > 0 ? "+" + delta : String.valueOf(delta);
    }

    private static int getTotalLoadedChunks() {
        int count = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            count += world.getLoadedChunks().length;
        }
        return count;
    }

    /**
     * Counts entities across all worlds, region-safely.
     *
     * Walks loaded chunks under region dispatch instead of calling
     * {@code world.getEntities()}, which crosses region boundaries and is unsafe
     * on Folia. Delivered asynchronously as a result.
     */
    private static void countEntities(java.util.function.IntConsumer onComplete) {
        me.koyere.lagxpert.utils.RegionizedSweeper.countEntities(
                Bukkit.getWorlds(), total -> onComplete.accept(total));
    }
}
