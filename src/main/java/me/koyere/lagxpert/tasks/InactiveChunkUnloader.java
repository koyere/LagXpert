package me.koyere.lagxpert.tasks;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.system.ChunkManager;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Task that automatically unloads inactive chunks to improve server performance.
 * Works in conjunction with ChunkManager to identify safe chunks for unloading
 * based on activity tracking, protection rules, and configurable thresholds.
 */
public class InactiveChunkUnloader extends BukkitRunnable {

    // Statistics tracking
    private static final AtomicInteger totalUnloadCycles = new AtomicInteger(0);
    private static final AtomicInteger totalChunksEvaluated = new AtomicInteger(0);
    private static final AtomicInteger totalChunksUnloaded = new AtomicInteger(0);
    private static final AtomicInteger totalChunksSkipped = new AtomicInteger(0);

    // Performance tracking
    private volatile long lastCycleTime = 0L;
    private volatile long averageCycleTime = 0L;
    private volatile long maxCycleTime = 0L;

    @Override
    public void run() {
        if (!ConfigManager.isChunkManagementModuleEnabled() || !ConfigManager.isAutoUnloadEnabled()) {
            return;
        }

        long cycleStartTime = System.currentTimeMillis();
        totalUnloadCycles.incrementAndGet();

        if (ConfigManager.isChunkDebugEnabled()) {
            LagXpert.getInstance().getLogger().info("[InactiveChunkUnloader] Starting unload cycle...");
        }

        Map<String, Integer> unloadResults = new HashMap<>();
        int totalEvaluated = 0;
        int totalUnloaded = 0;
        int totalSkipped = 0;

        for (World world : Bukkit.getWorlds()) {
            if (!shouldProcessWorld(world)) {
                continue;
            }

            UnloadResult result = processWorldChunks(world);
            unloadResults.put(world.getName(), result.unloaded);

            totalEvaluated += result.evaluated;
            totalUnloaded += result.unloaded;
            totalSkipped += result.skipped;
        }

        // Update statistics
        totalChunksEvaluated.addAndGet(totalEvaluated);
        totalChunksUnloaded.addAndGet(totalUnloaded);
        totalChunksSkipped.addAndGet(totalSkipped);

        // Calculate cycle time
        long cycleTime = System.currentTimeMillis() - cycleStartTime;
        lastCycleTime = cycleTime;
        updateAverageCycleTime(cycleTime);
        if (cycleTime > maxCycleTime) {
            maxCycleTime = cycleTime;
        }

        // Record unload operation for ChunkManager statistics
        if (totalUnloaded > 0) {
            long estimatedMemorySaved = totalUnloaded * 1024L; // Rough estimate
            ChunkManager.recordChunkUnload(totalUnloaded, estimatedMemorySaved);
        }

        // Broadcast results if enabled and threshold met
        broadcastResults(unloadResults, totalUnloaded);

        // Log results
        logResults(totalEvaluated, totalUnloaded, totalSkipped, cycleTime, unloadResults);
    }

    /**
     * Data class for storing unload operation results.
     */
    private static class UnloadResult {
        final int evaluated;
        final int unloaded;
        final int skipped;
        final List<String> skipReasons;

        UnloadResult(int evaluated, int unloaded, int skipped, List<String> skipReasons) {
            this.evaluated = evaluated;
            this.unloaded = unloaded;
            this.skipped = skipped;
            this.skipReasons = skipReasons;
        }
    }

    /**
     * Processes chunks in a specific world for potential unloading.
     */
    private UnloadResult processWorldChunks(World world) {
        List<Chunk> loadedChunks = Arrays.asList(world.getLoadedChunks());
        List<Chunk> candidates = new ArrayList<>();
        List<String> skipReasons = new ArrayList<>();

        int evaluated = 0;
        int skipped = 0;
        int maxUnloads = ConfigManager.getMaxUnloadsPerCycle();
        int minChunksToKeep = ConfigManager.getMinChunksPerWorld();

        // Pre-filter chunks to avoid unloading too many
        if (loadedChunks.size() <= minChunksToKeep) {
            skipReasons.add("World " + world.getName() + " has too few chunks loaded (" +
                    loadedChunks.size() + " <= " + minChunksToKeep + ")");
            return new UnloadResult(0, 0, loadedChunks.size(), skipReasons);
        }

        // Evaluate each chunk for unloading eligibility
        for (Chunk chunk : loadedChunks) {
            evaluated++;

            if (candidates.size() >= maxUnloads) {
                skipped++;
                continue; // Already have enough candidates
            }

            // Check if chunk is safe to unload
            if (ChunkManager.isSafeToUnload(chunk)) {
                // Additional checks specific to unloader
                if (isEligibleForUnloading(chunk, world)) {
                    candidates.add(chunk);
                } else {
                    skipped++;
                    skipReasons.add("Chunk " + chunk.getX() + "," + chunk.getZ() + " failed additional eligibility checks");
                }
            } else {
                skipped++;
                skipReasons.add("Chunk " + chunk.getX() + "," + chunk.getZ() + " not safe to unload");
            }
        }

        // Perform actual unloading
        int unloaded = performChunkUnloading(candidates, world);

        return new UnloadResult(evaluated, unloaded, skipped, skipReasons);
    }

    /**
     * Additional eligibility checks specific to the unloader.
     */
    private boolean isEligibleForUnloading(Chunk chunk, World world) {
        // Check if chunk is near world border (more aggressive unloading)
        if (ConfigManager.isBorderChunksEnabled() && ConfigManager.shouldAggressiveBorderUnload()) {
            if (isNearWorldBorder(chunk, world)) {
                return true; // Border chunks get priority for unloading
            }
        }

        // Check minimum chunks per world after this unload would occur
        int currentLoaded = world.getLoadedChunks().length;
        int minRequired = ConfigManager.getMinChunksPerWorld();

        if (currentLoaded <= minRequired) {
            return false; // Would go below minimum
        }

        // Check if there are too many players nearby (more conservative)
        if (hasMultiplePlayersNearby(chunk)) {
            return false; // Multiple players could return soon
        }

        return true;
    }

    /**
     * Checks if a chunk is near the world border.
     */
    private boolean isNearWorldBorder(Chunk chunk, World world) {
        if (!ConfigManager.isBorderChunksEnabled()) {
            return false;
        }

        try {
            org.bukkit.WorldBorder border = world.getWorldBorder();
            org.bukkit.Location center = border.getCenter();
            double size = border.getSize() / 2.0;
            int borderDistance = ConfigManager.getBorderDistanceChunks();

            // Convert chunk coordinates to block coordinates
            int chunkCenterX = chunk.getX() * 16 + 8;
            int chunkCenterZ = chunk.getZ() * 16 + 8;

            // Calculate distance to border
            double distanceX = Math.abs(chunkCenterX - center.getX());
            double distanceZ = Math.abs(chunkCenterZ - center.getZ());

            // Check if within border distance threshold
            double borderDistanceBlocks = borderDistance * 16.0;
            return (distanceX > (size - borderDistanceBlocks)) || (distanceZ > (size - borderDistanceBlocks));

        } catch (Exception e) {
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().warning(
                        "[InactiveChunkUnloader] Error checking world border for chunk: " + e.getMessage()
                );
            }
            return false;
        }
    }

    /**
     * Checks if there are multiple players nearby who might return.
     */
    private boolean hasMultiplePlayersNearby(Chunk chunk) {
        int nearbyRadius = ConfigManager.getPlayerActivityRadius() * 2; // Wider radius for this check
        int nearbyPlayers = 0;

        for (Player player : chunk.getWorld().getPlayers()) {
            Chunk playerChunk = player.getLocation().getChunk();
            int distanceX = Math.abs(chunk.getX() - playerChunk.getX());
            int distanceZ = Math.abs(chunk.getZ() - playerChunk.getZ());

            if (distanceX <= nearbyRadius && distanceZ <= nearbyRadius) {
                nearbyPlayers++;
                if (nearbyPlayers >= 2) {
                    return true; // Multiple players nearby
                }
            }
        }

        return false;
    }

    /**
     * Performs the actual unloading of chunks.
     */
    private int performChunkUnloading(List<Chunk> candidates, World world) {
        int successfulUnloads = 0;

        for (Chunk chunk : candidates) {
            try {
                // Double-check that the chunk is still safe to unload
                if (!chunk.isLoaded()) {
                    continue; // Already unloaded
                }

                if (!ChunkManager.isSafeToUnload(chunk)) {
                    continue; // No longer safe
                }

                // Perform the unload
                boolean unloaded = world.unloadChunk(chunk.getX(), chunk.getZ(), true);

                if (unloaded) {
                    successfulUnloads++;

                    if (ConfigManager.shouldLogChunkOperations()) {
                        LagXpert.getInstance().getLogger().info(
                                "[InactiveChunkUnloader] Unloaded chunk " + chunk.getX() + "," + chunk.getZ() +
                                        " in world " + world.getName()
                        );
                    }
                } else {
                    if (ConfigManager.isChunkDebugEnabled()) {
                        LagXpert.getInstance().getLogger().warning(
                                "[InactiveChunkUnloader] Failed to unload chunk " + chunk.getX() + "," + chunk.getZ() +
                                        " in world " + world.getName()
                        );
                    }
                }

            } catch (Exception e) {
                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().warning(
                            "[InactiveChunkUnloader] Error unloading chunk " + chunk.getX() + "," + chunk.getZ() +
                                    ": " + e.getMessage()
                    );
                }
            }
        }

        return successfulUnloads;
    }

    /**
     * Determines if a world should be processed for chunk unloading.
     */
    private boolean shouldProcessWorld(World world) {
        // Skip if world has no players and no automatic processing
        if (world.getPlayers().isEmpty()) {
            return false;
        }

        // Could add per-world configuration checks here
        if (ConfigManager.isPerWorldSettingsEnabled()) {
            // Future: implement per-world settings
        }

        return true;
    }

    /**
     * Broadcasts unload results if configured.
     */
    private void broadcastResults(Map<String, Integer> unloadResults, int totalUnloaded) {
        if (!ConfigManager.shouldBroadcastChunkOperations()) {
            return;
        }

        if (totalUnloaded < ConfigManager.getChunkBroadcastThreshold()) {
            return;
        }

        String message = MessageManager.color(ConfigManager.getChunksUnloadedMessage().replace("{count}", String.valueOf(totalUnloaded)));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("lagxpert.admin")) {
                player.sendMessage(message);
            }
        }

        // Also log to console
        LagXpert.getInstance().getLogger().info(message.replaceAll("§[0-9a-fk-or]", ""));
    }

    /**
     * Logs the results of the unload cycle.
     */
    private void logResults(int evaluated, int unloaded, int skipped, long cycleTime, Map<String, Integer> worldResults) {
        if (ConfigManager.shouldLogChunkOperations() || ConfigManager.isChunkDebugEnabled()) {
            StringBuilder logMessage = new StringBuilder();
            logMessage.append("[InactiveChunkUnloader] Cycle completed in ").append(cycleTime).append("ms. ");
            logMessage.append("Evaluated: ").append(evaluated).append(", ");
            logMessage.append("Unloaded: ").append(unloaded).append(", ");
            logMessage.append("Skipped: ").append(skipped);

            if (ConfigManager.shouldIncludeChunkCoordinates() && !worldResults.isEmpty()) {
                logMessage.append(". Per-world results: ");
                worldResults.forEach((world, count) ->
                        logMessage.append(world).append(": ").append(count).append(" "));
            }

            LagXpert.getInstance().getLogger().info(logMessage.toString());
        }

        // Log performance metrics
        if (ConfigManager.shouldLogChunkPerformance()) {
            LagXpert.getInstance().getLogger().info(
                    "[InactiveChunkUnloader] Performance: Cycle " + totalUnloadCycles.get() +
                            ", Time: " + cycleTime + "ms (Avg: " + averageCycleTime + "ms, Max: " + maxCycleTime + "ms)"
            );
        }
    }

    /**
     * Updates the running average cycle time.
     */
    private void updateAverageCycleTime(long cycleTime) {
        int cycles = totalUnloadCycles.get();
        if (cycles == 1) {
            averageCycleTime = cycleTime;
        } else {
            averageCycleTime = (averageCycleTime * (cycles - 1) + cycleTime) / cycles;
        }
    }

    /**
     * Gets comprehensive statistics about the chunk unloader.
     */
    public static Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("total_unload_cycles", totalUnloadCycles.get());
        stats.put("total_chunks_evaluated", totalChunksEvaluated.get());
        stats.put("total_chunks_unloaded", totalChunksUnloaded.get());
        stats.put("total_chunks_skipped", totalChunksSkipped.get());

        // Calculate success rate
        int evaluated = totalChunksEvaluated.get();
        int unloaded = totalChunksUnloaded.get();
        double unloadRate = evaluated > 0 ? (double) unloaded / evaluated * 100.0 : 0.0;
        stats.put("unload_success_rate_percent", unloadRate);

        // Performance metrics
        if (instance != null) {
            stats.put("last_cycle_time_ms", instance.lastCycleTime);
            stats.put("average_cycle_time_ms", instance.averageCycleTime);
            stats.put("max_cycle_time_ms", instance.maxCycleTime);
        }

        return stats;
    }

    /**
     * Resets all statistics.
     */
    public static void resetStatistics() {
        totalUnloadCycles.set(0);
        totalChunksEvaluated.set(0);
        totalChunksUnloaded.set(0);
        totalChunksSkipped.set(0);

        if (instance != null) {
            instance.lastCycleTime = 0L;
            instance.averageCycleTime = 0L;
            instance.maxCycleTime = 0L;
        }
    }

    // Static reference for statistics access
    private static InactiveChunkUnloader instance;

    /**
     * Starts the inactive chunk unloader task.
     */
    public static void start() {
        if (instance != null && !instance.isCancelled()) {
            instance.cancel();
        }

        instance = new InactiveChunkUnloader();
        long interval = ConfigManager.getUnloadCycleIntervalTicks();
        long initialDelay = Math.min(interval, 200L); // Start after 10 seconds or interval, whichever is smaller

        instance.runTaskTimer(LagXpert.getInstance(), initialDelay, interval);

        if (ConfigManager.isChunkDebugEnabled()) {
            LagXpert.getInstance().getLogger().info(
                    "[InactiveChunkUnloader] Started with interval: " + interval + " ticks (" + (interval/20) + " seconds)"
            );
        }
    }

    /**
     * Stops the inactive chunk unloader task.
     */
    public static void stop() {
        if (instance != null) {
            instance.cancel();
            instance = null;
        }
    }

    /**
     * Gets the current instance for statistics access.
     */
    public static InactiveChunkUnloader getInstance() {
        return instance;
    }

    /**
     * Manually triggers a chunk unload cycle (for testing or admin commands).
     */
    public static void triggerManualUnload() {
        if (instance != null) {
            Bukkit.getScheduler().runTaskAsynchronously(LagXpert.getInstance(), () -> {
                instance.run();
            });
        }
    }
}