package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Smart chunk management system that tracks chunk activity, manages loading/unloading,
 * and optimizes chunk operations for improved server performance.
 * Provides intelligent chunk preloading and safe unloading with protection mechanisms.
 */
public class ChunkManager {

    // Chunk activity tracking
    private static final Map<String, ChunkActivityData> chunkActivity = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastPlayerActivity = new ConcurrentHashMap<>();

    // Statistics tracking
    private static final AtomicInteger totalChunksUnloaded = new AtomicInteger(0);
    private static final AtomicInteger totalChunksPreloaded = new AtomicInteger(0);
    private static final AtomicLong totalMemorySaved = new AtomicLong(0);

    // Important block types that prevent chunk unloading
    private static final Set<Material> IMPORTANT_BLOCKS = new HashSet<>();

    static {
        // Initialize important blocks that prevent unloading
        IMPORTANT_BLOCKS.add(Material.SPAWNER);
        IMPORTANT_BLOCKS.add(Material.BEACON);
        IMPORTANT_BLOCKS.add(Material.CONDUIT);
        IMPORTANT_BLOCKS.add(Material.ENDER_CHEST);
        // Add more as configured
    }

    /**
     * Data class for tracking chunk activity and metadata.
     */
    public static class ChunkActivityData {
        private final String chunkKey;
        private final long creationTime;
        private volatile long lastPlayerVisit;
        private volatile long lastBlockChange;
        private volatile long lastEntityActivity;
        private volatile int playerVisitCount;
        private volatile int blockChangeCount;
        private volatile boolean hasImportantBlocks;
        private volatile boolean hasNamedEntities;
        private volatile boolean hasActiveRedstone;
        private volatile int blockDiversity;

        public ChunkActivityData(String chunkKey) {
            this.chunkKey = chunkKey;
            this.creationTime = System.currentTimeMillis();
            this.lastPlayerVisit = this.creationTime;
            this.lastBlockChange = 0;
            this.lastEntityActivity = 0;
            this.playerVisitCount = 1;
            this.blockChangeCount = 0;
            this.hasImportantBlocks = false;
            this.hasNamedEntities = false;
            this.hasActiveRedstone = false;
            this.blockDiversity = 0;
        }

        // Getters
        public String getChunkKey() { return chunkKey; }
        public long getCreationTime() { return creationTime; }
        public long getLastPlayerVisit() { return lastPlayerVisit; }
        public long getLastBlockChange() { return lastBlockChange; }
        public long getLastEntityActivity() { return lastEntityActivity; }
        public int getPlayerVisitCount() { return playerVisitCount; }
        public int getBlockChangeCount() { return blockChangeCount; }
        public boolean hasImportantBlocks() { return hasImportantBlocks; }
        public boolean hasNamedEntities() { return hasNamedEntities; }
        public boolean hasActiveRedstone() { return hasActiveRedstone; }
        public int getBlockDiversity() { return blockDiversity; }

        // Activity recording methods
        public void recordPlayerVisit() {
            this.lastPlayerVisit = System.currentTimeMillis();
            this.playerVisitCount++;
        }

        public void recordBlockChange() {
            this.lastBlockChange = System.currentTimeMillis();
            this.blockChangeCount++;
        }

        public void recordEntityActivity() {
            this.lastEntityActivity = System.currentTimeMillis();
        }

        public void setHasImportantBlocks(boolean hasImportantBlocks) {
            this.hasImportantBlocks = hasImportantBlocks;
        }

        public void setHasNamedEntities(boolean hasNamedEntities) {
            this.hasNamedEntities = hasNamedEntities;
        }

        public void setHasActiveRedstone(boolean hasActiveRedstone) {
            this.hasActiveRedstone = hasActiveRedstone;
        }

        public void setBlockDiversity(int blockDiversity) {
            this.blockDiversity = blockDiversity;
        }

        /**
         * Gets the time since last activity (most recent of player visit, block change, or entity activity).
         */
        public long getTimeSinceLastActivity() {
            long currentTime = System.currentTimeMillis();
            long lastActivity = Math.max(lastPlayerVisit, Math.max(lastBlockChange, lastEntityActivity));
            return currentTime - lastActivity;
        }

        /**
         * Checks if the chunk is considered inactive based on configured thresholds.
         */
        public boolean isInactive(long inactivityThresholdMs) {
            return getTimeSinceLastActivity() > inactivityThresholdMs;
        }
    }

    /**
     * Records player activity in a chunk.
     */
    public static void recordPlayerActivity(Player player, Chunk chunk) {
        if (!ConfigManager.isChunkActivityTrackingEnabled()) {
            return;
        }

        String chunkKey = generateChunkKey(chunk);
        String playerKey = player.getUniqueId().toString();

        // Update player activity
        lastPlayerActivity.put(playerKey, System.currentTimeMillis());

        // Update chunk activity
        ChunkActivityData activity = chunkActivity.computeIfAbsent(chunkKey, k -> new ChunkActivityData(k));
        activity.recordPlayerVisit();

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info(
                    "[ChunkManager] Player " + player.getName() + " activity recorded in chunk " +
                            chunk.getX() + "," + chunk.getZ() + " (" + chunk.getWorld().getName() + ")"
            );
        }
    }

    /**
     * Records block change activity in a chunk.
     */
    public static void recordBlockChange(Chunk chunk) {
        if (!ConfigManager.isChunkActivityTrackingEnabled() || !ConfigManager.shouldTrackBlockChanges()) {
            return;
        }

        String chunkKey = generateChunkKey(chunk);
        ChunkActivityData activity = chunkActivity.computeIfAbsent(chunkKey, k -> new ChunkActivityData(k));
        activity.recordBlockChange();
    }

    /**
     * Records entity activity in a chunk.
     */
    public static void recordEntityActivity(Chunk chunk) {
        if (!ConfigManager.isChunkActivityTrackingEnabled() || !ConfigManager.shouldTrackEntityChanges()) {
            return;
        }

        String chunkKey = generateChunkKey(chunk);
        ChunkActivityData activity = chunkActivity.computeIfAbsent(chunkKey, k -> new ChunkActivityData(k));
        activity.recordEntityActivity();
    }

    /**
     * Analyzes a chunk to determine its protection status and characteristics.
     */
    public static void analyzeChunk(Chunk chunk) {
        if (!chunk.isLoaded()) {
            return;
        }

        String chunkKey = generateChunkKey(chunk);
        ChunkActivityData activity = chunkActivity.computeIfAbsent(chunkKey, k -> new ChunkActivityData(k));

        // Check for important blocks
        boolean hasImportantBlocks = scanForImportantBlocks(chunk);
        activity.setHasImportantBlocks(hasImportantBlocks);

        // Check for named entities
        boolean hasNamedEntities = scanForNamedEntities(chunk);
        activity.setHasNamedEntities(hasNamedEntities);

        // Calculate block diversity
        int blockDiversity = calculateBlockDiversity(chunk);
        activity.setBlockDiversity(blockDiversity);

        // Check for active redstone (simplified check)
        boolean hasActiveRedstone = scanForActiveRedstone(chunk);
        activity.setHasActiveRedstone(hasActiveRedstone);
    }

    /**
     * Scans a chunk for important blocks that prevent unloading.
     */
    private static boolean scanForImportantBlocks(Chunk chunk) {
        if (!ConfigManager.shouldProtectImportantBlocks()) {
            return false;
        }

        try {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (IMPORTANT_BLOCKS.contains(block.getType())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().warning(
                        "[ChunkManager] Error scanning chunk for important blocks: " + e.getMessage()
                );
            }
        }

        return false;
    }

    /**
     * Scans a chunk for named entities.
     */
    private static boolean scanForNamedEntities(Chunk chunk) {
        if (!ConfigManager.shouldProtectNamedEntities()) {
            return false;
        }

        try {
            for (Entity entity : chunk.getEntities()) {
                if (entity.getCustomName() != null && !entity.getCustomName().isEmpty()) {
                    return true;
                }
            }
        } catch (Exception e) {
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().warning(
                        "[ChunkManager] Error scanning chunk for named entities: " + e.getMessage()
                );
            }
        }

        return false;
    }

    /**
     * Calculates block diversity in a chunk to detect player structures.
     */
    private static int calculateBlockDiversity(Chunk chunk) {
        if (!ConfigManager.shouldProtectPlayerStructures()) {
            return 0;
        }

        Set<Material> uniqueBlocks = new HashSet<>();

        try {
            // Sample blocks to avoid performance impact
            for (int x = 0; x < 16; x += 2) {
                for (int z = 0; z < 16; z += 2) {
                    for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y += 4) {
                        Block block = chunk.getBlock(x, y, z);
                        if (block.getType() != Material.AIR && block.getType() != Material.STONE &&
                                block.getType() != Material.DIRT && block.getType() != Material.GRASS_BLOCK) {
                            uniqueBlocks.add(block.getType());
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().warning(
                        "[ChunkManager] Error calculating block diversity: " + e.getMessage()
                );
            }
        }

        return uniqueBlocks.size();
    }

    /**
     * Scans for active redstone in a chunk (simplified check).
     */
    private static boolean scanForActiveRedstone(Chunk chunk) {
        if (!ConfigManager.shouldProtectActiveRedstone()) {
            return false;
        }

        try {
            // Simple check for powered redstone components
            for (int x = 0; x < 16; x += 4) {
                for (int z = 0; z < 16; z += 4) {
                    for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y += 8) {
                        Block block = chunk.getBlock(x, y, z);
                        if (block.getType() == Material.REDSTONE_WIRE && block.getBlockPower() > 0) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().warning(
                        "[ChunkManager] Error scanning for active redstone: " + e.getMessage()
                );
            }
        }

        return false;
    }

    /**
     * Determines if a chunk is safe to unload based on activity and protection rules.
     */
    public static boolean isSafeToUnload(Chunk chunk) {
        if (!chunk.isLoaded()) {
            return false;
        }

        String chunkKey = generateChunkKey(chunk);
        ChunkActivityData activity = chunkActivity.get(chunkKey);

        if (activity == null) {
            // No activity data, analyze the chunk first
            analyzeChunk(chunk);
            activity = chunkActivity.get(chunkKey);
            if (activity == null) {
                return false; // Failed to analyze
            }
        }

        // Check activity threshold
        long inactivityThresholdMs = ConfigManager.getChunkInactivityThresholdMinutes() * 60 * 1000L;
        if (!activity.isInactive(inactivityThresholdMs)) {
            return false; // Still active
        }

        // Check protection rules
        if (activity.hasImportantBlocks() || activity.hasNamedEntities() || activity.hasActiveRedstone()) {
            return false; // Protected by safeguards
        }

        // Check if it's a player structure
        if (activity.getBlockDiversity() >= ConfigManager.getStructureDiversityThreshold()) {
            return false; // Likely a player structure
        }

        // Check for nearby players
        if (hasNearbyPlayers(chunk, ConfigManager.getPlayerActivityRadius())) {
            return false; // Players nearby
        }

        return true;
    }

    /**
     * Checks if there are players within the specified radius of a chunk.
     */
    private static boolean hasNearbyPlayers(Chunk chunk, int radiusChunks) {
        World world = chunk.getWorld();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        for (Player player : world.getPlayers()) {
            Chunk playerChunk = player.getLocation().getChunk();
            int playerChunkX = playerChunk.getX();
            int playerChunkZ = playerChunk.getZ();

            int distanceX = Math.abs(chunkX - playerChunkX);
            int distanceZ = Math.abs(chunkZ - playerChunkZ);

            if (distanceX <= radiusChunks && distanceZ <= radiusChunks) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets chunks that are candidates for preloading around a player.
     */
    public static List<Chunk> getPreloadCandidates(Player player) {
        List<Chunk> candidates = new ArrayList<>();

        if (!ConfigManager.isChunkPreloadEnabled()) {
            return candidates;
        }

        World world = player.getWorld();
        Chunk playerChunk = player.getLocation().getChunk();
        int radius = ConfigManager.getPreloadRadius();

        // Calculate player movement direction if directional preloading is enabled
        Location playerLoc = player.getLocation();
        boolean useDirectional = ConfigManager.isDirectionalPreloadingEnabled();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x == 0 && z == 0) continue; // Skip player's current chunk

                int targetX = playerChunk.getX() + x;
                int targetZ = playerChunk.getZ() + z;

                // Check if chunk is already loaded
                if (world.isChunkLoaded(targetX, targetZ)) {
                    continue;
                }

                // If directional preloading is enabled, prioritize chunks in movement direction
                if (useDirectional) {
                    // This would require velocity tracking - simplified for now
                    // Could be enhanced to track player movement patterns
                }

                try {
                    Chunk candidate = world.getChunkAt(targetX, targetZ);
                    candidates.add(candidate);
                } catch (Exception e) {
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().warning(
                                "[ChunkManager] Failed to get chunk at " + targetX + "," + targetZ + ": " + e.getMessage()
                        );
                    }
                }
            }
        }

        return candidates;
    }

    /**
     * Cleans up old activity data to prevent memory leaks.
     */
    public static void cleanupOldActivity() {
        long currentTime = System.currentTimeMillis();
        long maxAgeMs = ConfigManager.getMaxActivityAgeHours() * 60 * 60 * 1000L;

        chunkActivity.entrySet().removeIf(entry -> {
            ChunkActivityData activity = entry.getValue();
            return (currentTime - activity.getCreationTime()) > maxAgeMs;
        });

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info(
                    "[ChunkManager] Activity cleanup completed. Active entries: " + chunkActivity.size()
            );
        }
    }

    /**
     * Generates a unique key for a chunk.
     */
    private static String generateChunkKey(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) {
            return "invalid_chunk";
        }
        return chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
    }

    /**
     * Gets comprehensive chunk management statistics.
     */
    public static Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("total_chunks_unloaded", totalChunksUnloaded.get());
        stats.put("total_chunks_preloaded", totalChunksPreloaded.get());
        stats.put("total_memory_saved_bytes", totalMemorySaved.get());
        stats.put("active_chunk_tracking", chunkActivity.size());
        stats.put("player_activity_tracking", lastPlayerActivity.size());

        // Calculate activity statistics
        long currentTime = System.currentTimeMillis();
        int activeChunks = 0;
        int inactiveChunks = 0;
        int protectedChunks = 0;

        long inactivityThresholdMs = ConfigManager.getChunkInactivityThresholdMinutes() * 60 * 1000L;

        for (ChunkActivityData activity : chunkActivity.values()) {
            if (activity.isInactive(inactivityThresholdMs)) {
                inactiveChunks++;
            } else {
                activeChunks++;
            }

            if (activity.hasImportantBlocks() || activity.hasNamedEntities() ||
                    activity.hasActiveRedstone() ||
                    activity.getBlockDiversity() >= ConfigManager.getStructureDiversityThreshold()) {
                protectedChunks++;
            }
        }

        stats.put("active_chunks", activeChunks);
        stats.put("inactive_chunks", inactiveChunks);
        stats.put("protected_chunks", protectedChunks);

        return stats;
    }

    /**
     * Records successful chunk unload operation.
     */
    public static void recordChunkUnload(int count, long memorySaved) {
        totalChunksUnloaded.addAndGet(count);
        totalMemorySaved.addAndGet(memorySaved);
    }

    /**
     * Records successful chunk preload operation.
     */
    public static void recordChunkPreload(int count) {
        totalChunksPreloaded.addAndGet(count);
    }

    /**
     * Resets all chunk management statistics.
     */
    public static void resetStatistics() {
        totalChunksUnloaded.set(0);
        totalChunksPreloaded.set(0);
        totalMemorySaved.set(0);
        chunkActivity.clear();
        lastPlayerActivity.clear();
    }

    /**
     * Gets activity data for a specific chunk.
     */
    public static ChunkActivityData getChunkActivity(Chunk chunk) {
        String chunkKey = generateChunkKey(chunk);
        return chunkActivity.get(chunkKey);
    }

    /**
     * Forces analysis of all loaded chunks (expensive operation).
     */
    public static void analyzeAllLoadedChunks() {
        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info("[ChunkManager] Starting analysis of all loaded chunks...");
        }

        int analyzed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                analyzeChunk(chunk);
                analyzed++;
            }
        }

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info("[ChunkManager] Analyzed " + analyzed + " chunks.");
        }
    }
}