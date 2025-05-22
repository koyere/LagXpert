package me.koyere.lagxpert.api;

import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag; // For handling block tags like SHULKER_BOXES
import org.bukkit.block.Block;
import org.bukkit.block.BlockState; // For Tile Entities
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * Public API for external plugins to access LagXpert's data and configured limits.
 * Provides methods to count entities/blocks and retrieve configured thresholds.
 */
public class LagXpertAPI {

    /**
     * Counts the number of living entities within the given chunk.
     * This is an efficient operation.
     *
     * @param chunk The chunk to scan.
     * @return The total number of living entities in the chunk.
     */
    public static int countLivingEntitiesInChunk(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) {
            return 0; // Or throw IllegalArgumentException
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
     * This method is recommended for counting blocks like Hoppers, Chests, Furnaces, etc.
     *
     * @param chunk    The chunk to scan.
     * @param material The material of the Tile Entity to count.
     * @return The number of Tile Entities of the specified material.
     * Returns 0 if the chunk is null, not loaded, or material is null.
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
     * Counts all shulker boxes (any color) within the given chunk.
     * This method efficiently iterates through Tile Entities.
     *
     * @param chunk The chunk to scan.
     * @return The total number of shulker boxes in the chunk.
     * Returns 0 if the chunk is null or not loaded.
     */
    public static int countAllShulkerBoxesInChunk(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) {
            return 0;
        }
        int count = 0;
        for (BlockState blockState : chunk.getTileEntities()) {
            if (Tag.SHULKER_BOXES.isTagged(blockState.getType())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts all blocks of a specific material within a given chunk by iterating through all block locations.
     * WARNING: This method can be very performance-intensive, especially if called frequently or on many chunks.
     * It iterates through every block in the chunk's vertical sections.
     * Prefer {@link #countTileEntitiesInChunk(Chunk, Material)} for blocks that are Tile Entities.
     *
     * @param chunk    The chunk to scan.
     * @param material The material of the block to count.
     * @return The number of blocks of the specified material.
     * Returns 0 if the chunk is null, not loaded, or material is null.
     */
    public static int countAllBlocksOfTypeSlow(Chunk chunk, Material material) {
        if (chunk == null || !chunk.isLoaded() || material == null) {
            return 0;
        }
        // LagXpert.getInstance().getLogger().warning("[LagXpertAPI] countAllBlocksOfTypeSlow called for " + material.name() + ". This is a slow operation!");
        int count = 0;
        int minHeight = chunk.getWorld().getMinHeight();
        int maxHeight = chunk.getWorld().getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minHeight; y < maxHeight; y++) {
                    if (chunk.getBlock(x, y, z).getType() == material) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // --- Getters for Configured Limits ---

    /**
     * Returns the maximum allowed living entities per chunk.
     * @return The configured limit.
     */
    public static int getMobLimit() {
        return ConfigManager.getMaxMobsPerChunk();
    }

    /**
     * Returns the maximum allowed hoppers per chunk.
     * @return The configured limit.
     */
    public static int getHopperLimit() {
        return ConfigManager.getMaxHoppersPerChunk();
    }

    /**
     * Returns the maximum allowed chests (including trapped chests) per chunk.
     * @return The configured limit.
     */
    public static int getChestLimit() {
        return ConfigManager.getMaxChestsPerChunk();
    }

    /**
     * Returns the maximum allowed furnaces per chunk.
     * @return The configured limit.
     */
    public static int getFurnaceLimit() {
        return ConfigManager.getMaxFurnacesPerChunk();
    }

    /**
     * Returns the maximum allowed blast furnaces per chunk.
     * @return The configured limit.
     */
    public static int getBlastFurnaceLimit() {
        return ConfigManager.getMaxBlastFurnacesPerChunk();
    }

    /**
     * Returns the maximum allowed smokers per chunk.
     * @return The configured limit.
     */
    public static int getSmokerLimit() {
        return ConfigManager.getMaxSmokersPerChunk();
    }

    /**
     * Returns the maximum allowed barrels per chunk.
     * @return The configured limit.
     */
    public static int getBarrelLimit() {
        return ConfigManager.getMaxBarrelsPerChunk();
    }

    /**
     * Returns the maximum allowed droppers per chunk.
     * @return The configured limit.
     */
    public static int getDropperLimit() {
        return ConfigManager.getMaxDroppersPerChunk();
    }

    /**
     * Returns the maximum allowed dispensers per chunk.
     * @return The configured limit.
     */
    public static int getDispenserLimit() {
        return ConfigManager.getMaxDispensersPerChunk();
    }

    /**
     * Returns the maximum allowed shulker boxes per chunk.
     * @return The configured limit.
     */
    public static int getShulkerBoxLimit() {
        return ConfigManager.getMaxShulkerBoxesPerChunk();
    }

    /**
     * Returns the maximum allowed TNT blocks per chunk.
     * @return The configured limit.
     */
    public static int getTntLimit() {
        return ConfigManager.getMaxTntPerChunk();
    }

    /**
     * Returns the maximum allowed pistons (including sticky pistons) per chunk.
     * @return The configured limit.
     */
    public static int getPistonLimit() {
        return ConfigManager.getMaxPistonsPerChunk();
    }

    /**
     * Returns the maximum allowed observers per chunk.
     * @return The configured limit.
     */
    public static int getObserverLimit() {
        return ConfigManager.getMaxObserversPerChunk();
    }

    /**
     * Generic method to get a limit for a specific material, if managed by LagXpert.
     * This can be expanded to cover all materials with configured limits.
     *
     * @param material The material to get the limit for.
     * @return The configured limit for the material, or -1 if not specifically managed or material is null.
     */
    public static int getLimitForMaterial(Material material) {
        if (material == null) return -1;
        switch (material) {
            case HOPPER: return getHopperLimit();
            case CHEST: case TRAPPED_CHEST: return getChestLimit();
            case FURNACE: return getFurnaceLimit();
            case BLAST_FURNACE: return getBlastFurnaceLimit();
            case SMOKER: return getSmokerLimit();
            case BARREL: return getBarrelLimit();
            case DROPPER: return getDropperLimit();
            case DISPENSER: return getDispenserLimit();
            // For shulker boxes, Tag.SHULKER_BOXES.isTagged(material) might be better if called with a specific shulker color
            // or use the generic getShulkerBoxLimit() for the overall limit.
            // This switch is best for exact material matches.
            case SHULKER_BOX: // Or any specific shulker material if they share the same limit config
                if (Tag.SHULKER_BOXES.isTagged(material)) return getShulkerBoxLimit(); // If called with a specific color
                return getShulkerBoxLimit(); // Fallback for the generic Material.SHULKER_BOX
            case TNT: return getTntLimit();
            case PISTON: case STICKY_PISTON: return getPistonLimit();
            case OBSERVER: return getObserverLimit();
            // Add cases for other specific materials that have limits
            default:
                // If it's a shulker box color, return the shulker box limit
                if (Tag.SHULKER_BOXES.isTagged(material)) {
                    return getShulkerBoxLimit();
                }
                return -1; // Indicates no specific limit managed for this material via this generic getter
        }
    }
}