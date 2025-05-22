package me.koyere.lagxpert.utils;

import me.koyere.lagxpert.LagXpert; // For logging warnings, if needed
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag; // For more generic material matching, e.g., SHULKER_BOXES
import org.bukkit.block.Block;
import org.bukkit.block.BlockState; // For Tile Entities
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity; // For counting all living entities

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class for analyzing chunk data, such as counting entities and blocks.
 * Provides both specific and general-purpose counting methods, with emphasis on performance.
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
     * This is an efficient operation.
     *
     * @param chunk The chunk to scan. Must not be null and should be loaded.
     * @return Total count of mobs matching the predefined list. Returns 0 if chunk is null or not loaded.
     */
    public static int countSpecificPredefinedMobsInChunk(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) {
            return 0;
        }
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
     * This is an efficient operation.
     *
     * @param chunk The chunk to scan. Must not be null and should be loaded.
     * @return The total number of living entities. Returns 0 if chunk is null or not loaded.
     */
    public static int countAllLivingEntitiesInChunk(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) {
            return 0;
        }
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity) {
                count++;
            }
        }
        return count;
    }

    /**
     * Efficiently counts blocks that are Tile Entities of a specific material within a given chunk.
     * This is the recommended method for counting blocks like Hoppers, Chests, Furnaces, etc.
     *
     * @param chunk    The chunk to scan. Must not be null and should be loaded.
     * @param material The material of the Tile Entity to count. Must not be null.
     * @return The number of Tile Entities of the specified material. Returns 0 if chunk/material is null or chunk not loaded.
     */
    public static int countTileEntitiesInChunk(Chunk chunk, Material material) {
        if (chunk == null || !chunk.isLoaded() || material == null) {
            return 0;
        }
        int count = 0;
        for (BlockState blockState : chunk.getTileEntities()) {
            if (blockState.getType() == material) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts all blocks of a specific material within a given chunk by iterating through all block locations.
     * WARNING: This method is resource-intensive and can cause performance issues (lag spikes)
     * if called frequently or on many chunks, as it iterates through every block in the chunk's vertical sections.
     * Prefer {@link #countTileEntitiesInChunk(Chunk, Material)} for blocks that are Tile Entities.
     *
     * @param chunk    The chunk to scan. Must not be null and should be loaded.
     * @param material The material of the block to count. Must not be null.
     * @return The number of blocks of the specified material. Returns 0 if chunk/material is null or chunk not loaded.
     */
    public static int countAllBlocksOfTypeSlow(Chunk chunk, Material material) {
        if (chunk == null || !chunk.isLoaded() || material == null) {
            return 0;
        }
        // Consider adding a log message here if debug mode is enabled, to track usage of this slow method.
        // if (ConfigManager.isDebugEnabled()) {
        //     LagXpert.getInstance().getLogger().warning("[ChunkUtils] Slow block counting used for " + material.name() + " in chunk " + chunk.getX() + "," + chunk.getZ());
        // }

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
}