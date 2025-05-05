package me.koyere.lagxpert.api;

import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * Public API for external plugins to access LagXpert functionality.
 */
public class LagXpertAPI {

    /**
     * Returns the number of living entities in a given chunk.
     */
    public static int countMobsInChunk(Chunk chunk) {
        int count = 0;
        for (Entity e : chunk.getEntities()) {
            if (e instanceof LivingEntity) count++;
        }
        return count;
    }

    /**
     * Returns the number of blocks of a specific type in the chunk.
     */
    public static int countBlocksInChunk(Chunk chunk, Material material) {
        int count = 0;
        for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() == material) count++;
                }
            }
        }
        return count;
    }

    /**
     * Returns the maximum allowed living entities per chunk.
     */
    public static int getMobLimit() {
        return ConfigManager.getMaxMobsPerChunk();
    }

    /**
     * Returns the maximum allowed hoppers per chunk.
     */
    public static int getHopperLimit() {
        return ConfigManager.getMaxHoppersPerChunk();
    }

    /**
     * Returns the maximum allowed chests per chunk.
     */
    public static int getChestLimit() {
        return ConfigManager.getMaxChestsPerChunk();
    }

    /**
     * Returns the maximum allowed furnaces per chunk.
     */
    public static int getFurnaceLimit() {
        return ConfigManager.getMaxFurnacesPerChunk();
    }
}
