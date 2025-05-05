package me.koyere.lagxpert.utils;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class for analyzing chunks (blocks and entities).
 */
public class ChunkUtils {

    // Types de entidades válidas para conteo
    private static final List<EntityType> VALID_MOBS = Arrays.asList(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
            EntityType.SPIDER, EntityType.COW, EntityType.SHEEP, EntityType.PIG,
            EntityType.CHICKEN, EntityType.VILLAGER
    );

    /**
     * Counts the number of valid entities in the given chunk.
     *
     * @param chunk the chunk to scan
     * @return total count of valid mobs (hostile + passive)
     */
    public static int countEntitiesInChunk(Chunk chunk) {
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (VALID_MOBS.contains(entity.getType())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts how many blocks of the given type exist in the chunk.
     *
     * @param chunk  the chunk to scan
     * @param type   the material name to count (e.g., "HOPPER")
     * @return number of blocks matching the material
     */
    public static int countBlocksInChunk(Chunk chunk, String type) {
        int count = 0;
        Material target = Material.matchMaterial(type.toUpperCase());

        if (target == null) return 0;

        for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() == target) {
                        count++;
                    }
                }
            }
        }

        return count;
    }
}
