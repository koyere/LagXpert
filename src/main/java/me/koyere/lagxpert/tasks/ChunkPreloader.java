package me.koyere.lagxpert.tasks;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.system.ChunkManager;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Task that preloads chunks around players to improve performance and reduce lag.
 * Works in conjunction with ChunkManager to analyze player movement patterns
 * and intelligently preload chunks in the direction players are moving.
 */
public class ChunkPreloader extends BukkitRunnable {

    // Statistics tracking
    private static final AtomicInteger totalPreloadCycles = new AtomicInteger(0);
    private static final AtomicInteger totalChunksEvaluated = new AtomicInteger(0);
    private static final AtomicInteger totalChunksPreloaded = new AtomicInteger(0);
    private static final AtomicInteger totalChunksSkipped = new AtomicInteger(0);

    // Player movement tracking for directional preloading
    private final Map<String, PlayerMovementData> playerMovementHistory = new HashMap<>();

    // Performance tracking
    private volatile long lastCycleTime = 0L;
    private volatile long averageCycleTime = 0L;
    private volatile long maxCycleTime = 0L;

    /**
     * Data class for tracking player movement patterns.
     */
    private static class PlayerMovementData {
        private final String playerName;
        private volatile Location lastLocation;
        private volatile Vector lastDirection;
        private volatile double lastSpeed;
        private volatile long lastUpdate;
        private volatile boolean isMoving;

        public PlayerMovementData(String playerName, Location initialLocation) {
            this.playerName = playerName;
            this.lastLocation = initialLocation.clone();
            this.lastDirection = new Vector(0, 0, 0);
            this.lastSpeed = 0.0;
            this.lastUpdate = System.currentTimeMillis();
            this.isMoving = false;
        }

        public void updateMovement(Location newLocation) {
            if (lastLocation != null && newLocation.getWorld().equals(lastLocation.getWorld())) {
                long currentTime = System.currentTimeMillis();
                double timeDiff = (currentTime - lastUpdate) / 1000.0; // seconds

                if (timeDiff > 0) {
                    // Calculate movement vector and speed
                    Vector movement = newLocation.toVector().subtract(lastLocation.toVector());
                    double distance = movement.length();
                    lastSpeed = distance / timeDiff; // blocks per second

                    // Update direction (normalized movement vector)
                    if (distance > 0.1) { // Only update direction if significant movement
                        lastDirection = movement.normalize();
                        isMoving = lastSpeed >= ConfigManager.getMinMovementSpeed();
                    } else {
                        isMoving = false;
                    }
                }
            }

            lastLocation = newLocation.clone();
            lastUpdate = System.currentTimeMillis();
        }

        // Getters
        public String getPlayerName() { return playerName; }
        public Location getLastLocation() { return lastLocation != null ? lastLocation.clone() : null; }
        public Vector getLastDirection() { return lastDirection.clone(); }
        public double getLastSpeed() { return lastSpeed; }
        public long getLastUpdate() { return lastUpdate; }
        public boolean isMoving() { return isMoving; }
    }

    /**
     * Data class for storing preload operation results.
     */
    private static class PreloadResult {
        final int evaluated;
        final int preloaded;
        final int skipped;
        final List<String> skipReasons;

        PreloadResult(int evaluated, int preloaded, int skipped, List<String> skipReasons) {
            this.evaluated = evaluated;
            this.preloaded = preloaded;
            this.skipped = skipped;
            this.skipReasons = skipReasons;
        }
    }

    @Override
    public void run() {
        if (!ConfigManager.isChunkManagementModuleEnabled() || !ConfigManager.isChunkPreloadEnabled()) {
            return;
        }

        long cycleStartTime = System.currentTimeMillis();
        totalPreloadCycles.incrementAndGet();

        if (ConfigManager.isChunkDebugEnabled()) {
            LagXpert.getInstance().getLogger().info("[ChunkPreloader] Starting preload cycle...");
        }

        Map<String, Integer> preloadResults = new HashMap<>();
        int totalEvaluated = 0;
        int totalPreloaded = 0;
        int totalSkipped = 0;

        for (World world : Bukkit.getWorlds()) {
            if (!shouldProcessWorld(world)) {
                continue;
            }

            PreloadResult result = processWorldPlayers(world);
            preloadResults.put(world.getName(), result.preloaded);

            totalEvaluated += result.evaluated;
            totalPreloaded += result.preloaded;
            totalSkipped += result.skipped;
        }

        // Update statistics
        totalChunksEvaluated.addAndGet(totalEvaluated);
        totalChunksPreloaded.addAndGet(totalPreloaded);
        totalChunksSkipped.addAndGet(totalSkipped);

        // Calculate cycle time
        long cycleTime = System.currentTimeMillis() - cycleStartTime;
        lastCycleTime = cycleTime;
        updateAverageCycleTime(cycleTime);
        if (cycleTime > maxCycleTime) {
            maxCycleTime = cycleTime;
        }

        // Record preload operation for ChunkManager statistics
        if (totalPreloaded > 0) {
            ChunkManager.recordChunkPreload(totalPreloaded);
        }

        // Broadcast results if enabled and threshold met
        broadcastResults(preloadResults, totalPreloaded);

        // Log results
        logResults(totalEvaluated, totalPreloaded, totalSkipped, cycleTime, preloadResults);

        // Cleanup old movement data
        cleanupOldMovementData();
    }

    /**
     * Processes players in a specific world for chunk preloading.
     */
    private PreloadResult processWorldPlayers(World world) {
        List<Player> players = world.getPlayers();
        List<String> skipReasons = new ArrayList<>();

        int evaluated = 0;
        int preloaded = 0;
        int skipped = 0;
        int maxPreloadsPerCycle = ConfigManager.getMaxPreloadsPerCycle();

        for (Player player : players) {
            if (preloaded >= maxPreloadsPerCycle) {
                skipped++;
                skipReasons.add("Max preloads per cycle reached");
                continue;
            }

            // Update player movement data
            updatePlayerMovement(player);

            // Get chunks to preload for this player
            List<Chunk> candidates = getPreloadCandidatesForPlayer(player);
            evaluated += candidates.size();

            // Preload chunks
            int playerPreloaded = 0;
            for (Chunk chunk : candidates) {
                if (preloaded + playerPreloaded >= maxPreloadsPerCycle) {
                    skipped++;
                    break;
                }

                if (preloadChunk(chunk, player)) {
                    playerPreloaded++;
                } else {
                    skipped++;
                    skipReasons.add("Failed to preload chunk " + chunk.getX() + "," + chunk.getZ());
                }
            }

            preloaded += playerPreloaded;

            // Send preload message to player if configured and chunks were preloaded
            if (playerPreloaded > 0 && ConfigManager.isChunkDebugEnabled()) {
                String message = ConfigManager.getChunksPreloadedMessage()
                        .replace("{count}", String.valueOf(playerPreloaded))
                        .replace("{player}", player.getName());
                player.sendMessage(MessageManager.color(message));
            }
        }

        return new PreloadResult(evaluated, preloaded, skipped, skipReasons);
    }

    /**
     * Updates movement tracking data for a player.
     */
    private void updatePlayerMovement(Player player) {
        String playerKey = player.getUniqueId().toString();
        Location currentLocation = player.getLocation();

        PlayerMovementData movementData = playerMovementHistory.get(playerKey);
        if (movementData == null) {
            movementData = new PlayerMovementData(player.getName(), currentLocation);
            playerMovementHistory.put(playerKey, movementData);
        } else {
            movementData.updateMovement(currentLocation);
        }

        // Record player activity in ChunkManager
        ChunkManager.recordPlayerActivity(player, currentLocation.getChunk());
    }

    /**
     * Gets chunks that should be preloaded for a specific player.
     */
    private List<Chunk> getPreloadCandidatesForPlayer(Player player) {
        List<Chunk> candidates = new ArrayList<>();
        World world = player.getWorld();
        Chunk playerChunk = player.getLocation().getChunk();
        int radius = ConfigManager.getPreloadRadius();

        PlayerMovementData movementData = playerMovementHistory.get(player.getUniqueId().toString());

        if (ConfigManager.isDirectionalPreloadingEnabled() && movementData != null && movementData.isMoving()) {
            // Directional preloading based on player movement
            candidates.addAll(getDirectionalPreloadCandidates(player, playerChunk, movementData));
        } else {
            // Standard radial preloading
            candidates.addAll(getRadialPreloadCandidates(world, playerChunk, radius));
        }

        return candidates;
    }

    /**
     * Gets chunks to preload in the direction the player is moving.
     */
    private List<Chunk> getDirectionalPreloadCandidates(Player player, Chunk playerChunk, PlayerMovementData movementData) {
        List<Chunk> candidates = new ArrayList<>();
        World world = player.getWorld();
        Vector direction = movementData.getLastDirection();
        int radius = ConfigManager.getPreloadRadius();

        // Calculate weighted preloading - more chunks in movement direction
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x == 0 && z == 0) continue; // Skip player's current chunk

                // Calculate direction weight (favor chunks in movement direction)
                Vector chunkDirection = new Vector(x, 0, z).normalize();
                double directionWeight = direction.dot(chunkDirection);

                // Only preload chunks with positive direction weight (in front of player)
                if (directionWeight > 0.3) { // Threshold for "in front"
                    int targetX = playerChunk.getX() + x;
                    int targetZ = playerChunk.getZ() + z;

                    if (!world.isChunkLoaded(targetX, targetZ)) {
                        try {
                            Chunk candidate = world.getChunkAt(targetX, targetZ);
                            candidates.add(candidate);
                        } catch (Exception e) {
                            if (ConfigManager.isDebugEnabled()) {
                                LagXpert.getInstance().getLogger().warning(
                                        "[ChunkPreloader] Failed to get chunk at " + targetX + "," + targetZ + ": " + e.getMessage()
                                );
                            }
                        }
                    }
                }
            }
        }

        return candidates;
    }

    /**
     * Gets chunks to preload in a radial pattern around the player.
     */
    private List<Chunk> getRadialPreloadCandidates(World world, Chunk playerChunk, int radius) {
        List<Chunk> candidates = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x == 0 && z == 0) continue; // Skip player's current chunk

                int targetX = playerChunk.getX() + x;
                int targetZ = playerChunk.getZ() + z;

                if (!world.isChunkLoaded(targetX, targetZ)) {
                    try {
                        Chunk candidate = world.getChunkAt(targetX, targetZ);
                        candidates.add(candidate);
                    } catch (Exception e) {
                        if (ConfigManager.isDebugEnabled()) {
                            LagXpert.getInstance().getLogger().warning(
                                    "[ChunkPreloader] Failed to get chunk at " + targetX + "," + targetZ + ": " + e.getMessage()
                            );
                        }
                    }
                }
            }
        }

        return candidates;
    }

    /**
     * Attempts to preload a specific chunk.
     */
    private boolean preloadChunk(Chunk chunk, Player player) {
        try {
            // Check if chunk is already loaded
            if (chunk.isLoaded()) {
                return false; // Already loaded, no need to preload
            }

            // Load the chunk
            boolean loaded = chunk.load(false); // Generate if needed

            if (loaded) {
                if (ConfigManager.shouldLogChunkOperations()) {
                    LagXpert.getInstance().getLogger().info(
                            "[ChunkPreloader] Preloaded chunk " + chunk.getX() + "," + chunk.getZ() +
                                    " in world " + chunk.getWorld().getName() + " for player " + player.getName()
                    );
                }
                return true;
            } else {
                if (ConfigManager.isChunkDebugEnabled()) {
                    LagXpert.getInstance().getLogger().warning(
                            "[ChunkPreloader] Failed to preload chunk " + chunk.getX() + "," + chunk.getZ() +
                                    " in world " + chunk.getWorld().getName()
                    );
                }
                return false;
            }

        } catch (Exception e) {
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().warning(
                        "[ChunkPreloader] Error preloading chunk " + chunk.getX() + "," + chunk.getZ() +
                                ": " + e.getMessage()
                );
            }
            return false;
        }
    }

    /**
     * Determines if a world should be processed for chunk preloading.
     */
    private boolean shouldProcessWorld(World world) {
        // Skip if world has no players
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
     * Cleans up old movement data for players who have logged off.
     */
    private void cleanupOldMovementData() {
        long currentTime = System.currentTimeMillis();
        long maxAge = 300000; // 5 minutes

        playerMovementHistory.entrySet().removeIf(entry -> {
            PlayerMovementData data = entry.getValue();
            return (currentTime - data.getLastUpdate()) > maxAge;
        });
    }

    /**
     * Broadcasts preload results if configured.
     */
    private void broadcastResults(Map<String, Integer> preloadResults, int totalPreloaded) {
        if (!ConfigManager.shouldBroadcastChunkOperations()) {
            return;
        }

        if (totalPreloaded < ConfigManager.getChunkBroadcastThreshold()) {
            return;
        }

        // Only broadcast to admins for preloading (less spam)
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("lagxpert.admin")) {
                String message = MessageManager.color("&7[ChunkPreloader] Preloaded &e" + totalPreloaded + "&7 chunks for improved performance.");
                player.sendMessage(message);
            }
        }
    }

    /**
     * Logs the results of the preload cycle.
     */
    private void logResults(int evaluated, int preloaded, int skipped, long cycleTime, Map<String, Integer> worldResults) {
        if (ConfigManager.shouldLogChunkOperations() || ConfigManager.isChunkDebugEnabled()) {
            StringBuilder logMessage = new StringBuilder();
            logMessage.append("[ChunkPreloader] Cycle completed in ").append(cycleTime).append("ms. ");
            logMessage.append("Evaluated: ").append(evaluated).append(", ");
            logMessage.append("Preloaded: ").append(preloaded).append(", ");
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
                    "[ChunkPreloader] Performance: Cycle " + totalPreloadCycles.get() +
                            ", Time: " + cycleTime + "ms (Avg: " + averageCycleTime + "ms, Max: " + maxCycleTime + "ms)"
            );
        }
    }

    /**
     * Updates the running average cycle time.
     */
    private void updateAverageCycleTime(long cycleTime) {
        int cycles = totalPreloadCycles.get();
        if (cycles == 1) {
            averageCycleTime = cycleTime;
        } else {
            averageCycleTime = (averageCycleTime * (cycles - 1) + cycleTime) / cycles;
        }
    }

    /**
     * Gets comprehensive statistics about the chunk preloader.
     */
    public static Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("total_preload_cycles", totalPreloadCycles.get());
        stats.put("total_chunks_evaluated", totalChunksEvaluated.get());
        stats.put("total_chunks_preloaded", totalChunksPreloaded.get());
        stats.put("total_chunks_skipped", totalChunksSkipped.get());

        // Calculate success rate
        int evaluated = totalChunksEvaluated.get();
        int preloaded = totalChunksPreloaded.get();
        double preloadRate = evaluated > 0 ? (double) preloaded / evaluated * 100.0 : 0.0;
        stats.put("preload_success_rate_percent", preloadRate);

        // Performance metrics
        if (instance != null) {
            stats.put("last_cycle_time_ms", instance.lastCycleTime);
            stats.put("average_cycle_time_ms", instance.averageCycleTime);
            stats.put("max_cycle_time_ms", instance.maxCycleTime);
            stats.put("tracked_players", instance.playerMovementHistory.size());
        }

        return stats;
    }

    /**
     * Resets all statistics.
     */
    public static void resetStatistics() {
        totalPreloadCycles.set(0);
        totalChunksEvaluated.set(0);
        totalChunksPreloaded.set(0);
        totalChunksSkipped.set(0);

        if (instance != null) {
            instance.lastCycleTime = 0L;
            instance.averageCycleTime = 0L;
            instance.maxCycleTime = 0L;
            instance.playerMovementHistory.clear();
        }
    }

    // Static reference for statistics access
    private static ChunkPreloader instance;

    /**
     * Starts the chunk preloader task.
     */
    public static void start() {
        if (instance != null && !instance.isCancelled()) {
            instance.cancel();
        }

        instance = new ChunkPreloader();
        long interval = ConfigManager.getPreloadCycleIntervalTicks();
        long initialDelay = Math.min(interval, 100L); // Start after 5 seconds or interval, whichever is smaller

        instance.runTaskTimer(LagXpert.getInstance(), initialDelay, interval);

        if (ConfigManager.isChunkDebugEnabled()) {
            LagXpert.getInstance().getLogger().info(
                    "[ChunkPreloader] Started with interval: " + interval + " ticks (" + (interval/20) + " seconds)"
            );
        }
    }

    /**
     * Stops the chunk preloader task.
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
    public static ChunkPreloader getInstance() {
        return instance;
    }

    /**
     * Manually triggers a chunk preload cycle (for testing or admin commands).
     */
    public static void triggerManualPreload() {
        if (instance != null) {
            Bukkit.getScheduler().runTaskAsynchronously(LagXpert.getInstance(), () -> {
                instance.run();
            });
        }
    }

    /**
     * Gets movement data for a specific player.
     */
    public static PlayerMovementData getPlayerMovementData(Player player) {
        if (instance != null) {
            return instance.playerMovementHistory.get(player.getUniqueId().toString());
        }
        return null;
    }
}