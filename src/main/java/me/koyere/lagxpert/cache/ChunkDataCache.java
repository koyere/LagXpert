package me.koyere.lagxpert.cache;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance cache system for chunk analysis data.
 * Caches chunk scan results to avoid repeated expensive operations
 * like entity counting and block iteration.
 * Thread-safe implementation with automatic expiry and cleanup.
 */
public class ChunkDataCache {

    private static final Map<String, ChunkData> cache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 30000; // 30 seconds cache duration
    private static final long CLEANUP_INTERVAL = 60000; // Cleanup every minute
    private static long lastCleanup = System.currentTimeMillis();
    
    // Atomic counters for real-time block tracking (performance optimization)
    // Format: "worldname_chunkX_chunkZ_material" -> count
    private static final Map<String, AtomicInteger> atomicCounters = new ConcurrentHashMap<>();

    /**
     * Immutable data structure holding cached chunk analysis results.
     * Contains entity counts, block counts, and timestamp for expiry checking.
     */
    public static class ChunkData {
        private final int livingEntities;
        private final Map<Material, Integer> blockCounts;
        private final Map<String, Integer> customCounts; // For special counting (like all shulker boxes)
        private final long timestamp;
        private final boolean isComplete; // Flag to indicate if scan was complete

        public ChunkData(int livingEntities, Map<Material, Integer> blockCounts, Map<String, Integer> customCounts, boolean isComplete) {
            this.livingEntities = livingEntities;
            this.blockCounts = new ConcurrentHashMap<>(blockCounts);
            this.customCounts = new ConcurrentHashMap<>(customCounts);
            this.timestamp = System.currentTimeMillis();
            this.isComplete = isComplete;
        }

        public int getLivingEntities() { return livingEntities; }
        public Map<Material, Integer> getBlockCounts() { return new ConcurrentHashMap<>(blockCounts); }
        public Map<String, Integer> getCustomCounts() { return new ConcurrentHashMap<>(customCounts); }
        public long getTimestamp() { return timestamp; }
        public boolean isComplete() { return isComplete; }

        /**
         * Checks if this cached data has expired based on the configured expiry time.
         * @return true if the data is still valid, false if expired
         */
        public boolean isValid() {
            return (System.currentTimeMillis() - timestamp) < CACHE_EXPIRY_MS;
        }

        /**
         * Gets the count for a specific material from the cached block counts.
         * @param material The material to get count for
         * @return The count of blocks of this material, or 0 if not found
         */
        public int getBlockCount(Material material) {
            return blockCounts.getOrDefault(material, 0);
        }

        /**
         * Gets a custom count by key (e.g., "all_shulker_boxes", "all_chests").
         * @param key The custom count key
         * @return The count for this key, or 0 if not found
         */
        public int getCustomCount(String key) {
            return customCounts.getOrDefault(key, 0);
        }
    }

    /**
     * Generates a unique cache key for a chunk.
     * Format: "worldname_chunkX_chunkZ"
     * @param chunk The chunk to generate key for
     * @return Unique string key for the chunk
     */
    private static String generateChunkKey(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) {
            return "invalid_chunk";
        }
        return chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
    }

    /**
     * Generates a cache key for a chunk using world name and coordinates.
     * @param worldName The name of the world
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @return Unique string key for the chunk
     */
    private static String generateChunkKey(String worldName, int chunkX, int chunkZ) {
        return worldName + "_" + chunkX + "_" + chunkZ;
    }

    /**
     * Retrieves cached chunk data if available and still valid.
     * Automatically triggers cleanup if needed.
     * @param chunk The chunk to get cached data for
     * @return ChunkData if available and valid, null otherwise
     */
    public static ChunkData getCachedData(Chunk chunk) {
        if (chunk == null) {
            return null;
        }

        // Trigger periodic cleanup
        performPeriodicCleanup();

        String key = generateChunkKey(chunk);
        ChunkData data = cache.get(key);

        if (data != null && data.isValid()) {
            return data;
        } else if (data != null) {
            // Data exists but is expired, remove it
            cache.remove(key);
        }

        return null;
    }

    /**
     * Stores chunk analysis results in the cache.
     * @param chunk The chunk this data belongs to
     * @param livingEntities Number of living entities in the chunk
     * @param blockCounts Map of material types to their counts
     * @param customCounts Map of custom count keys to their values
     * @param isComplete Whether the scan was complete or partial
     */
    public static void cacheData(Chunk chunk, int livingEntities, Map<Material, Integer> blockCounts,
                                 Map<String, Integer> customCounts, boolean isComplete) {
        if (chunk == null || blockCounts == null) {
            return;
        }

        String key = generateChunkKey(chunk);
        ChunkData data = new ChunkData(livingEntities, blockCounts,
                customCounts != null ? customCounts : new ConcurrentHashMap<>(),
                isComplete);
        cache.put(key, data);
    }

    /**
     * Invalidates cached data for a specific chunk.
     * Should be called when chunk data changes (e.g., blocks placed/broken, entities spawned/killed).
     * Note: This preserves atomic counters as they are maintained separately for performance.
     * @param chunk The chunk to invalidate cache for
     */
    public static void invalidateChunk(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        String key = generateChunkKey(chunk);
        cache.remove(key);
        // Note: We don't reset atomic counters here as they are maintained independently
        // for real-time tracking. Use resetAllAtomicCounters() if needed.
    }

    /**
     * Invalidates cached data for a chunk by world name and coordinates.
     * @param worldName The world name
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     */
    public static void invalidateChunk(String worldName, int chunkX, int chunkZ) {
        String key = generateChunkKey(worldName, chunkX, chunkZ);
        cache.remove(key);
    }

    /**
     * Invalidates all cached data for chunks in a specific world.
     * Useful when world-specific settings change.
     * @param world The world to invalidate cache for
     */
    public static void invalidateWorld(World world) {
        if (world == null) {
            return;
        }
        String worldName = world.getName();
        cache.entrySet().removeIf(entry -> entry.getKey().startsWith(worldName + "_"));
        // Also clear atomic counters for this world
        clearWorldAtomicCounters(world);
    }

    /**
     * Clears all cached data.
     * Should be called during plugin reload or configuration changes.
     */
    public static void clearAll() {
        cache.clear();
        clearAllAtomicCounters();
    }

    /**
     * Performs cleanup of expired cache entries if enough time has passed.
     * Called automatically during cache operations.
     */
    private static void performPeriodicCleanup() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanup > CLEANUP_INTERVAL) {
            cache.entrySet().removeIf(entry -> !entry.getValue().isValid());
            lastCleanup = currentTime;
        }
    }

    /**
     * Forces immediate cleanup of all expired cache entries.
     * Can be called manually if needed.
     */
    public static void forceCleanup() {
        cache.entrySet().removeIf(entry -> !entry.getValue().isValid());
        lastCleanup = System.currentTimeMillis();
    }

    /**
     * Gets current cache statistics for monitoring purposes.
     * @return Map containing cache statistics
     */
    public static Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("total_entries", cache.size());

        long currentTime = System.currentTimeMillis();
        long validEntries = cache.values().stream()
                .mapToLong(data -> data.isValid() ? 1 : 0)
                .sum();

        stats.put("valid_entries", validEntries);
        stats.put("expired_entries", cache.size() - validEntries);
        stats.put("cache_hit_potential", cache.isEmpty() ? 0.0 : (double) validEntries / cache.size());
        stats.put("last_cleanup", lastCleanup);
        stats.put("next_cleanup_in_ms", Math.max(0, (lastCleanup + CLEANUP_INTERVAL) - currentTime));

        return stats;
    }

    /**
     * Checks if a chunk has valid cached data without retrieving it.
     * @param chunk The chunk to check
     * @return true if valid cached data exists for this chunk
     */
    public static boolean hasCachedData(Chunk chunk) {
        if (chunk == null) {
            return false;
        }
        ChunkData data = cache.get(generateChunkKey(chunk));
        return data != null && data.isValid();
    }
    
    // ==================== ATOMIC COUNTER METHODS ====================
    // These methods provide high-performance real-time block counting
    // without needing expensive chunk scans for frequently accessed materials
    
    /**
     * Generates a unique key for atomic counter tracking.
     * Format: "worldname_chunkX_chunkZ_material"
     * @param chunk The chunk
     * @param material The material being tracked
     * @return Unique counter key
     */
    private static String generateCounterKey(Chunk chunk, Material material) {
        if (chunk == null || material == null) {
            return "invalid_counter";
        }
        return generateChunkKey(chunk) + "_" + material.name();
    }
    
    /**
     * Increments the atomic counter for a specific material in a chunk.
     * Used when blocks are placed to maintain accurate real-time counts.
     * Thread-safe and high-performance alternative to chunk scanning.
     * @param chunk The chunk where the block was placed
     * @param material The material that was placed
     * @return The new count after increment
     */
    public static int incrementAtomicCounter(Chunk chunk, Material material) {
        if (chunk == null || material == null) {
            return 0;
        }
        
        String key = generateCounterKey(chunk, material);
        AtomicInteger counter = atomicCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
        
        // If we have no base count, we need to initialize from actual chunk data
        if (counter.get() == 0) {
            initializeAtomicCounter(chunk, material);
        }
        
        return counter.incrementAndGet();
    }
    
    /**
     * Decrements the atomic counter for a specific material in a chunk.
     * Used when blocks are broken to maintain accurate real-time counts.
     * @param chunk The chunk where the block was broken
     * @param material The material that was broken
     * @return The new count after decrement (minimum 0)
     */
    public static int decrementAtomicCounter(Chunk chunk, Material material) {
        if (chunk == null || material == null) {
            return 0;
        }
        
        String key = generateCounterKey(chunk, material);
        AtomicInteger counter = atomicCounters.get(key);
        
        if (counter == null) {
            // Initialize counter if it doesn't exist
            initializeAtomicCounter(chunk, material);
            counter = atomicCounters.get(key);
        }
        
        // Ensure counter never goes below 0
        int newValue;
        int currentValue;
        do {
            currentValue = counter.get();
            newValue = Math.max(0, currentValue - 1);
        } while (!counter.compareAndSet(currentValue, newValue));
        
        return newValue;
    }
    
    /**
     * Gets the current atomic counter value for a material in a chunk.
     * If no counter exists, initializes it from actual chunk data.
     * @param chunk The chunk to check
     * @param material The material to count
     * @return Current count of the material in the chunk
     */
    public static int getAtomicCounter(Chunk chunk, Material material) {
        if (chunk == null || material == null) {
            return 0;
        }
        
        String key = generateCounterKey(chunk, material);
        AtomicInteger counter = atomicCounters.get(key);
        
        if (counter == null) {
            initializeAtomicCounter(chunk, material);
            counter = atomicCounters.get(key);
        }
        
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * Initializes atomic counter from actual chunk data.
     * This is called when we need accurate count but no counter exists yet.
     * @param chunk The chunk to scan
     * @param material The material to count
     */
    private static void initializeAtomicCounter(Chunk chunk, Material material) {
        if (chunk == null || material == null) {
            return;
        }
        
        String key = generateCounterKey(chunk, material);
        
        // Check if we have cached data first
        ChunkData cachedData = getCachedData(chunk);
        if (cachedData != null) {
            int count = cachedData.getBlockCount(material);
            atomicCounters.put(key, new AtomicInteger(count));
            return;
        }
        
        // If no cached data, do a quick scan just for this material
        int count = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
                    if (chunk.getBlock(x, y, z).getType() == material) {
                        count++;
                    }
                }
            }
        }
        
        atomicCounters.put(key, new AtomicInteger(count));
    }
    
    /**
     * Resets atomic counter for a specific material in a chunk.
     * Used when chunk data changes significantly or cache is invalidated.
     * @param chunk The chunk to reset
     * @param material The material counter to reset
     */
    public static void resetAtomicCounter(Chunk chunk, Material material) {
        if (chunk == null || material == null) {
            return;
        }
        
        String key = generateCounterKey(chunk, material);
        atomicCounters.remove(key);
    }
    
    /**
     * Resets all atomic counters for a chunk.
     * Called when chunk is completely invalidated.
     * @param chunk The chunk to reset all counters for
     */
    public static void resetAllAtomicCounters(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        
        String chunkKey = generateChunkKey(chunk);
        atomicCounters.entrySet().removeIf(entry -> entry.getKey().startsWith(chunkKey + "_"));
    }
    
    /**
     * Clears all atomic counters for a world.
     * Called during world invalidation.
     * @param world The world to clear counters for
     */
    public static void clearWorldAtomicCounters(World world) {
        if (world == null) {
            return;
        }
        
        String worldPrefix = world.getName() + "_";
        atomicCounters.entrySet().removeIf(entry -> entry.getKey().startsWith(worldPrefix));
    }
    
    /**
     * Clears all atomic counters.
     * Called during plugin reload or shutdown.
     */
    public static void clearAllAtomicCounters() {
        atomicCounters.clear();
    }
}