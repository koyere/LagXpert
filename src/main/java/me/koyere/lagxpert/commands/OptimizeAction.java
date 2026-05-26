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
        if (!sender.hasPermission("lagxpert.admin.optimize")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
            return true;
        }

        sender.sendMessage(MessageManager.color("&8&m------------------------------------------"));
        sender.sendMessage(MessageManager.color("&b&lLagXpert Optimize &8- &7Full optimization pass"));
        sender.sendMessage(MessageManager.color("&8&m------------------------------------------"));

        // Snapshot BEFORE
        double tpsBefore = TPSMonitor.getCurrentTPS();
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        int chunksBefore = getTotalLoadedChunks();
        int entitiesBefore = getTotalEntities();

        long totalStart = System.currentTimeMillis();
        Map<String, PhaseResult> results = new LinkedHashMap<>();

        // Phase 1: Smart Mob Removal
        results.put("Mob Removal", runMobCleanup(sender));
        // Phase 2: Entity Cleanup
        results.put("Entity Cleanup", runEntityCleanup(sender));
        // Phase 3: Item Cleanup
        results.put("Item Cleanup", runItemCleanup(sender));
        // Phase 4: Chunk Unloading
        results.put("Chunk Unload", runChunkUnload(sender));
        // Phase 5: Cache Clear
        results.put("Cache Clear", runCacheClear(sender));

        long totalDuration = System.currentTimeMillis() - totalStart;

        // Snapshot AFTER
        double tpsAfter = TPSMonitor.getCurrentTPS();
        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        int chunksAfter = getTotalLoadedChunks();
        int entitiesAfter = getTotalEntities();

        // Display results
        sender.sendMessage("");
        sender.sendMessage(MessageManager.color("&e&lResults:"));
        int totalActions = 0;
        for (PhaseResult pr : results.values()) {
            if (pr.count > 0) {
                sender.sendMessage(MessageManager.color(
                        "  &7• &f" + pr.name + ": &a" + pr.count +
                                " &7(" + pr.durationMs + "ms)"));
            } else {
                sender.sendMessage(MessageManager.color(
                        "  &7• &f" + pr.name + ": &7none needed"));
            }
            totalActions += pr.count;
        }

        sender.sendMessage("");
        sender.sendMessage(MessageManager.color("&e&lBefore → After:"));
        sender.sendMessage(MessageManager.color(String.format(
                "  &7TPS: &f%.1f &7→ &a%.1f", tpsBefore, tpsAfter)));
        sender.sendMessage(MessageManager.color(String.format(
                "  &7Memory: &f%dMB &7→ &a%dMB",
                memBefore / 1024 / 1024, memAfter / 1024 / 1024)));
        sender.sendMessage(MessageManager.color(String.format(
                "  &7Chunks: &f%d &7→ &a%d", chunksBefore, chunksAfter)));
        sender.sendMessage(MessageManager.color(String.format(
                "  &7Entities: &f%d &7→ &a%d", entitiesBefore, entitiesAfter)));

        sender.sendMessage("");
        sender.sendMessage(MessageManager.color(
                "&aOptimization complete! &7Total actions: &e" + totalActions +
                        " &7in &e" + totalDuration + "ms"));

        // Log to audit trail
        ActionLogger.getInstance().log(
                ActionLogger.ActionType.MANUAL_OPTIMIZE,
                null, null,
                "Full optimization: " + totalActions + " actions in " + totalDuration + "ms",
                totalActions, "player:" + sender.getName(), true, totalDuration);

        sender.sendMessage(MessageManager.color("&8&m------------------------------------------"));
        return true;
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

    private static PhaseResult runEntityCleanup(CommandSender sender) {
        long start = System.currentTimeMillis();
        int removed = 0;
        if (ConfigManager.isEntityCleanupModuleEnabled()) {
            sender.sendMessage(MessageManager.color("&7Running entity cleanup..."));
            // Force entity cleanup immediately
            removed = EntityCleanupTask.runImmediate();
        }
        long duration = System.currentTimeMillis() - start;
        return new PhaseResult("Entity Cleanup", removed, duration);
    }

    private static PhaseResult runItemCleanup(CommandSender sender) {
        long start = System.currentTimeMillis();
        int removed = 0;
        if (ConfigManager.isItemCleanerModuleEnabled()) {
            sender.sendMessage(MessageManager.color("&7Running item cleanup..."));
            removed = ItemCleanerTask.runManualCleanupAllWorlds(null);
        }
        long duration = System.currentTimeMillis() - start;
        return new PhaseResult("Item Cleanup", removed, duration);
    }

    private static PhaseResult runChunkUnload(CommandSender sender) {
        long start = System.currentTimeMillis();
        int unloaded = 0;
        if (ConfigManager.isChunkManagementModuleEnabled() && ConfigManager.isAutoUnloadEnabled()) {
            sender.sendMessage(MessageManager.color("&7Running chunk unload..."));
            InactiveChunkUnloader.triggerManualUnload();
            // Can't easily count how many were unloaded from static methods,
            // but we performed the cycle
        }
        long duration = System.currentTimeMillis() - start;
        return new PhaseResult("Chunk Unload", unloaded, duration);
    }

    private static PhaseResult runCacheClear(CommandSender sender) {
        long start = System.currentTimeMillis();
        sender.sendMessage(MessageManager.color("&7Clearing caches..."));
        ChunkUtils.clearAllCache();
        long duration = System.currentTimeMillis() - start;
        return new PhaseResult("Cache Clear", 1, duration);
    }

    private static int getTotalLoadedChunks() {
        int count = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            count += world.getLoadedChunks().length;
        }
        return count;
    }

    private static int getTotalEntities() {
        int count = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            count += world.getEntities().size();
        }
        return count;
    }
}
