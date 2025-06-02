package me.koyere.lagxpert.utils;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.cache.ChunkDataCache;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for analyzing chunk data, such as counting entities and blocks.
 * Provides both specific and general-purpose counting methods, with emphasis on performance.
 * Now includes cache integration for improved performance on repeated chunk scans.
 */
public class ChunkUtils {

    // A predefined list of specific entity types for targeted counting.
    // This list can be modified or made configurable if needed.
    private static final Set<EntityType> PREDEFINED_MOB_TYPES_TO_COUNT = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
            EntityType.SPIDER, EntityType.COW, EntityType.SHEEP, EntityType.PIG,
            EntityType.CHICKEN, EntityType.VILLAGER
            // Add more types if needed
    )));

    /**
     * Counts the number of entities in the given chunk that match a predefined list of mob types.
     * See {@link #PREDEFINED_MOB_TYPES_TO_COUNT}.
     * This is an efficient operation that uses cache when available.
     *
     * @param chunk The chunk to scan. Must not be null and should be loaded.
     * @return Total count of mobs matching the predefined list. Returns 0 if chunk is null or not loaded.
     */
    public static int countSpecificPredefinedMobsInChunk(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) {
            return 0;
        }

        // Check cache first for living entities, then filter for predefined types
        ChunkDataCache.ChunkData cachedData = ChunkDataCache.getCachedData(chunk);
        if (cachedData != null && cachedData.isComplete()) {
            // Cache doesn't store predefined mob counts specifically, so we need to scan entities
            // This is still faster than full chunk analysis
            int count = 0;
            for (Entity entity : chunk.getEntities()) {
                if (PREDEFINED_MOB_TYPES_TO_COUNT.contains(entity.getType())) {
                    count++;
                }
            }
            return count;
        }

        // No cache available, perform direct count
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (PREDEFINED_MOB_TYPES_TO_COUNT.contains(entity.getType())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts all living entities (any type extending LivingEntity) within the given chunk.
     * This is an efficient operation that uses cache when available.
     *
     * @param chunk The chunk to scan. Must not be null and should be loaded.
     * @return The total number of living entities. Returns 0 if chunk is null or not loaded.
     */
    public static int countAllLivingEntitiesInChunk(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) {
            return 0;
        }

        // Check cache first
        ChunkDataCache.ChunkData cachedData = ChunkDataCache.getCachedData(chunk);
        if (cachedData != null && cachedData.isComplete()) {
            return cachedData.getLivingEntities();
        }

        // No cache available, perform count and cache result
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity) {
                count++;
            }
        }

        // Cache the result with minimal block data for living entity queries
        Map<Material, Integer> emptyBlockCounts = new EnumMap<>(Material.class);
        Map<String, Integer> customCounts = new ConcurrentHashMap<>();
        ChunkDataCache.cacheData(chunk, count, emptyBlockCounts, customCounts, false);

        return count;
    }

    /**
     * Efficiently counts blocks that are Tile Entities of a specific material within a given chunk.
     * This is the recommended method for counting blocks like Hoppers, Chests, Furnaces, etc.
     * Uses cache when available for improved performance.
     *
     * @param chunk    The chunk to scan. Must not be null and should be loaded.
     * @param material The material of the Tile Entity to count. Must not be null.
     * @return The number of Tile Entities of the specified material. Returns 0 if chunk/material is null or chunk not loaded.
     */
    public static int countTileEntitiesInChunk(Chunk chunk, Material material) {
        if (chunk == null || !chunk.isLoaded() || material == null) {
            return 0;
        }

        // Check cache first
        ChunkDataCache.ChunkData cachedData = ChunkDataCache.getCachedData(chunk);
        if (cachedData != null && cachedData.isComplete()) {
            return cachedData.getBlockCount(material);
        }

        // No cache available, perform count
        int count = 0;
        for (BlockState blockState : chunk.getTileEntities()) {
            if (blockState.getType() == material) {
                count++;
            }
        }
        return count;
    }

    /**
     * Performs a complete chunk analysis and caches all results.
     * This method scans for all relevant blocks and entities, storing the results in cache
     * for future quick retrieval.
     *
     * @param chunk The chunk to analyze completely
     * @return ChunkDataCache.ChunkData containing all analysis results
     */
    public static ChunkDataCache.ChunkData performCompleteChunkAnalysis(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) {
            return null;
        }

        // Check if we already have complete cached data
        ChunkDataCache.ChunkData cachedData = ChunkDataCache.getCachedData(chunk);
        if (cachedData != null && cachedData.isComplete()) {
            return cachedData;
        }

        // Perform complete analysis
        int livingEntities = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity) {
                livingEntities++;
            }
        }

        // Count all tile entities by material
        Map<Material, Integer> blockCounts = new EnumMap<>(Material.class);
        Map<String, Integer> customCounts = new ConcurrentHashMap<>();

        for (BlockState blockState : chunk.getTileEntities()) {
            Material type = blockState.getType();
            blockCounts.put(type, blockCounts.getOrDefault(type, 0) + 1);

            // Special handling for shulker boxes (all colors)
            if (Tag.SHULKER_BOXES.isTagged(type)) {
                customCounts.put("all_shulker_boxes", customCounts.getOrDefault("all_shulker_boxes", 0) + 1);
            }
        }

        // Calculate combined counts for related materials
        int allChests = blockCounts.getOrDefault(Material.CHEST, 0) + blockCounts.getOrDefault(Material.TRAPPED_CHEST, 0);
        customCounts.put("all_chests", allChests);

        int allFurnaces = blockCounts.getOrDefault(Material.FURNACE, 0) +
                blockCounts.getOrDefault(Material.BLAST_FURNACE, 0) +
                blockCounts.getOrDefault(Material.SMOKER, 0);
        customCounts.put("all_furnaces", allFurnaces);

        int allPistons = blockCounts.getOrDefault(Material.PISTON, 0) + blockCounts.getOrDefault(Material.STICKY_PISTON, 0);
        customCounts.put("all_pistons", allPistons);

        int allDroppersDispensers = blockCounts.getOrDefault(Material.DROPPER, 0) + blockCounts.getOrDefault(Material.DISPENSER, 0);
        customCounts.put("all_droppers_dispensers", allDroppersDispensers);

        // Cache the complete results
        ChunkDataCache.cacheData(chunk, livingEntities, blockCounts, customCounts, true);

        return ChunkDataCache.getCachedData(chunk);
    }

    /**
     * Counts all blocks of a specific material within a given chunk by iterating through all block locations.
     * WARNING: This method is resource-intensive and can cause performance issues (lag spikes)
     * if called frequently or on many chunks, as it iterates through every block in the chunk's vertical sections.
     * Prefer {@link #countTileEntitiesInChunk(Chunk, Material)} for blocks that are Tile Entities.
     * This method now uses cache when available.
     *
     * @param chunk    The chunk to scan. Must not be null and should be loaded.
     * @param material The material of the block to count. Must not be null.
     * @return The number of blocks of the specified material. Returns 0 if chunk/material is null or chunk not loaded.
     */
    public static int countAllBlocksOfTypeSlow(Chunk chunk, Material material) {
        if (chunk == null || !chunk.isLoaded() || material == null) {
            return 0;
        }

        // Check cache first
        ChunkDataCache.ChunkData cachedData = ChunkDataCache.getCachedData(chunk);
        if (cachedData != null && cachedData.isComplete()) {
            return cachedData.getBlockCount(material);
        }

        // Log warning for slow operation
        if (ConfigManager.isDebugEnabled() && LagXpert.getInstance() != null) {
            LagXpert.getInstance().getLogger().warning("[ChunkUtils] Slow block counting used for " + material.name() + " in chunk " + chunk.getX() + "," + chunk.getZ());
        }

        int count = 0;
        int minHeight = chunk.getWorld().getMinHeight();
        int maxHeight = chunk.getWorld().getMaxHeight();

        for (int x = 0; x < 16; x++) { // Iterate chunk X (0-15 relative to chunk corner)
            for (int z = 0; z < 16; z++) { // Iterate chunk Z (0-15 relative to chunk corner)
                for (int y = minHeight; y < maxHeight; y++) { // Iterate world Y height
                    if (chunk.getBlock(x, y, z).getType() == material) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    /**
     * Counts all shulker boxes (any color) within the given chunk.
     * Uses cache when available for improved performance.
     *
     * @param chunk The chunk to scan.
     * @return The total number of shulker boxes in the chunk.
     */
    public static int countAllShulkerBoxesInChunk(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) {
            return 0;
        }

        // Check cache first
        ChunkDataCache.ChunkData cachedData = ChunkDataCache.getCachedData(chunk);
        if (cachedData != null && cachedData.isComplete()) {
            return cachedData.getCustomCount("all_shulker_boxes");
        }

        // No cache available, perform count
        int count = 0;
        for (BlockState blockState : chunk.getTileEntities()) {
            if (Tag.SHULKER_BOXES.isTagged(blockState.getType())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Invalidates cache for a chunk when its data changes.
     * Should be called from listeners when blocks are placed/broken or entities spawn/die.
     *
     * @param chunk The chunk whose cache should be invalidated
     */
    public static void invalidateChunkCache(Chunk chunk) {
        ChunkDataCache.invalidateChunk(chunk);
    }

    /**
     * Gets cache statistics for monitoring purposes.
     *
     * @return Map containing cache performance statistics
     */
    public static Map<String, Object> getCacheStatistics() {
        return ChunkDataCache.getCacheStats();
    }

    /**
     * Clears all cached chunk data.
     * Useful during plugin reload or configuration changes.
     */
    public static void clearAllCache() {
        ChunkDataCache.clearAll();
    }
}