package me.koyere.lagxpert.listeners;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.api.events.ChunkOverloadEvent;
import me.koyere.lagxpert.cache.ChunkDataCache;
import me.koyere.lagxpert.system.AlertCooldownManager;
import me.koyere.lagxpert.tasks.AsyncChunkAnalyzer;
import me.koyere.lagxpert.utils.ChunkUtils;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Listens for block placements and restricts storage-related blocks
 * if their per-chunk limits are exceeded. Alerts players on placement
 * if limits are reached or nearly reached, subject to alert configurations and cooldowns.
 * Now includes cache invalidation and async processing for improved performance.
 */
public class StorageListener implements Listener {

    // Helper class (replaces record) to store configuration for each limited block type
    private static final class BlockLimitConfig {
        private final Material material;
        private final Supplier<Integer> limitSupplier;
        private final String bypassPermissionSuffix;
        private final String limitMessageKey;
        private final String nearLimitMessageKey;
        private final String overloadCause; // Used for event cause and part of alert key
        private final boolean isTileEntity;

        public BlockLimitConfig(Material material, Supplier<Integer> limitSupplier, String bypassPermissionSuffix,
                                String limitMessageKey, String nearLimitMessageKey, String overloadCause, boolean isTileEntity) {
            this.material = material;
            this.limitSupplier = limitSupplier;
            this.bypassPermissionSuffix = bypassPermissionSuffix;
            this.limitMessageKey = limitMessageKey;
            this.nearLimitMessageKey = nearLimitMessageKey;
            this.overloadCause = overloadCause;
            this.isTileEntity = isTileEntity;
        }

        // Getters
        public Material getMaterial() { return material; }
        public Supplier<Integer> getLimitSupplier() { return limitSupplier; }
        public String getBypassPermissionSuffix() { return bypassPermissionSuffix; }
        public String getLimitMessageKey() { return limitMessageKey; }
        public String getNearLimitMessageKey() { return nearLimitMessageKey; }
        public String getOverloadCause() { return overloadCause; }
        public boolean isTileEntity() { return isTileEntity; }
    }

    private static final Map<Material, BlockLimitConfig> limitedBlocks = new EnumMap<>(Material.class);

    static {
        addLimitedBlock(Material.HOPPER, ConfigManager::getMaxHoppersPerChunk, "hoppers", "limits.hopper", "hoppers", true);
        addLimitedBlock(Material.CHEST, ConfigManager::getMaxChestsPerChunk, "chests", "limits.chest", "chests", true);
        addLimitedBlock(Material.TRAPPED_CHEST, ConfigManager::getMaxChestsPerChunk, "chests", "limits.chest", "chests", true);
        addLimitedBlock(Material.FURNACE, ConfigManager::getMaxFurnacesPerChunk, "furnaces", "limits.furnace", "furnaces", true);
        addLimitedBlock(Material.BLAST_FURNACE, ConfigManager::getMaxBlastFurnacesPerChunk, "blast_furnaces", "limits.blast_furnace", "blast_furnaces", true);
        addLimitedBlock(Material.SMOKER, ConfigManager::getMaxSmokersPerChunk, "smokers", "limits.smoker", "smokers", true);
        addLimitedBlock(Material.BARREL, ConfigManager::getMaxBarrelsPerChunk, "barrels", "limits.barrel", "barrels", true);
        addLimitedBlock(Material.DROPPER, ConfigManager::getMaxDroppersPerChunk, "droppers", "limits.dropper", "droppers", true);
        addLimitedBlock(Material.DISPENSER, ConfigManager::getMaxDispensersPerChunk, "dispensers", "limits.dispenser", "dispensers", true);
        addLimitedBlock(Material.SHULKER_BOX, ConfigManager::getMaxShulkerBoxesPerChunk, "shulker_boxes", "limits.shulker_box", "shulker_boxes", true);
        addLimitedBlock(Material.TNT, ConfigManager::getMaxTntPerChunk, "tnt", "limits.tnt", "tnt", false);
        addLimitedBlock(Material.PISTON, ConfigManager::getMaxPistonsPerChunk, "pistons", "limits.piston", "pistons", false);
        addLimitedBlock(Material.STICKY_PISTON, ConfigManager::getMaxPistonsPerChunk, "pistons", "limits.piston", "pistons", false);
        addLimitedBlock(Material.OBSERVER, ConfigManager::getMaxObserversPerChunk, "observers", "limits.observer", "observers", false);
    }

    private static void addLimitedBlock(Material material, Supplier<Integer> limitSupplier, String permSuffix,
                                        String msgKey, String cause, boolean isTile) {
        limitedBlocks.put(material, new BlockLimitConfig(material, limitSupplier, "lagxpert.bypass." + permSuffix,
                msgKey, "limits.near-limit", cause, isTile));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStoragePlace(BlockPlaceEvent event) {
        if (!ConfigManager.isStorageModuleEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        Block blockBeingPlaced = event.getBlock();
        Material type = blockBeingPlaced.getType();
        Chunk chunk = blockBeingPlaced.getChunk();

        BlockLimitConfig config = limitedBlocks.get(type);

        if (config != null) {
            if (player.hasPermission(config.getBypassPermissionSuffix())) {
                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().info(
                            "Placement of " + type + " by " + player.getName() + " at chunk " +
                                    chunk.getX() + "," + chunk.getZ() + " bypassed due to permission."
                    );
                }
                // Still invalidate cache even for bypass to keep data accurate
                invalidateChunkCache(chunk);
                return;
            }

            // Get current count using cache-optimized methods
            int currentCount = getCurrentCount(chunk, config);
            int limit = config.getLimitSupplier().get();
            int newCount = currentCount + 1;

            if (newCount > limit && limit > 0) {
                event.setCancelled(true);
                fireChunkOverloadEvent(chunk, config.getOverloadCause() + "_limit_exceeded_placement");

                if (ConfigManager.isAlertsModuleEnabled() && shouldShowLimitReachedAlert(config.getMaterial())) {
                    // Generate a unique key for this alert: type_limit_chunk_world_x_z
                    String alertKey = AlertCooldownManager.generateAlertKey(config.getOverloadCause() + "_limit", chunk);
                    if (AlertCooldownManager.canSendAlert(player, alertKey)) {
                        player.sendMessage(MessageManager.getPrefixedMessage(config.getLimitMessageKey()));
                    }
                }

                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().info(
                            "Cancelled placement of " + type + " by " + player.getName() + " at chunk " +
                                    chunk.getX() + "," + chunk.getZ() + ". Current in chunk: " + currentCount +
                                    ", New count would be: " + newCount + ", Limit: " + limit
                    );
                }

            } else if (newCount >= (int) (limit * 0.8) && limit > 0) { // Near limit warning
                if (ConfigManager.isAlertsModuleEnabled() && shouldShowNearLimitWarning(config.getMaterial())) {
                    // Generate a unique key for this alert: type_near_limit_chunk_world_x_z
                    String alertKey = AlertCooldownManager.generateAlertKey(config.getOverloadCause() + "_near_limit", chunk);
                    if (AlertCooldownManager.canSendAlert(player, alertKey)) {
                        Map<String, Object> placeholders = new HashMap<>();
                        placeholders.put("type", type.toString().toLowerCase().replace("_", " "));
                        placeholders.put("used", String.valueOf(newCount));
                        placeholders.put("max", String.valueOf(limit));
                        player.sendMessage(MessageManager.getPrefixedFormattedMessage(config.getNearLimitMessageKey(), placeholders));
                    }
                }

                // Block was placed successfully, update atomic counters and invalidate cache
                updateCountersAfterPlacement(chunk, config);
                invalidateChunkCache(chunk);

                // Optionally trigger async re-analysis of the chunk for future cache hits
                scheduleAsyncReanalysis(chunk);
            } else {
                // Block was placed successfully, update atomic counters and invalidate cache
                updateCountersAfterPlacement(chunk, config);
                invalidateChunkCache(chunk);

                // Optionally trigger async re-analysis of the chunk for future cache hits
                scheduleAsyncReanalysis(chunk);
            }
        }
    }

    /**
     * Gets the current count of a specific block type in a chunk using cache-optimized methods.
     * Now uses atomic counters for better performance and accuracy.
     */
    private int getCurrentCount(Chunk chunk, BlockLimitConfig config) {
        // For TNT and other high-frequency blocks, use atomic counters for best performance
        if (config.getMaterial() == Material.TNT || 
            config.getMaterial() == Material.PISTON || 
            config.getMaterial() == Material.STICKY_PISTON) {
            
            if (config.getOverloadCause().equals("pistons")) {
                // For pistons, we need both regular and sticky pistons
                return ChunkDataCache.getAtomicCounter(chunk, Material.PISTON) +
                       ChunkDataCache.getAtomicCounter(chunk, Material.STICKY_PISTON);
            } else {
                // Single material atomic counter
                return ChunkDataCache.getAtomicCounter(chunk, config.getMaterial());
            }
        }
        
        // Check cache first for complete data (for other materials)
        ChunkDataCache.ChunkData cachedData = ChunkDataCache.getCachedData(chunk);
        if (cachedData != null && cachedData.isComplete()) {
            // Use cached data for counting
            switch (config.getOverloadCause()) {
                case "chests":
                    return cachedData.getCustomCount("all_chests");
                case "shulker_boxes":
                    return cachedData.getCustomCount("all_shulker_boxes");
                case "pistons":
                    return cachedData.getCustomCount("all_pistons");
                default:
                    return cachedData.getBlockCount(config.getMaterial());
            }
        }

        // No cache available, use direct counting methods
        if (config.isTileEntity()) {
            if (config.getOverloadCause().equals("chests")) {
                return ChunkUtils.countTileEntitiesInChunk(chunk, Material.CHEST) +
                        ChunkUtils.countTileEntitiesInChunk(chunk, Material.TRAPPED_CHEST);
            } else if (config.getOverloadCause().equals("shulker_boxes")) {
                return ChunkUtils.countAllShulkerBoxesInChunk(chunk);
            } else {
                return ChunkUtils.countTileEntitiesInChunk(chunk, config.getMaterial());
            }
        } else {
            if (config.getOverloadCause().equals("pistons")) {
                return ChunkUtils.countAllBlocksOfTypeSlow(chunk, Material.PISTON) +
                        ChunkUtils.countAllBlocksOfTypeSlow(chunk, Material.STICKY_PISTON);
            } else {
                return ChunkUtils.countAllBlocksOfTypeSlow(chunk, config.getMaterial());
            }
        }
    }

    /**
     * Invalidates the cache for a chunk and schedules async re-analysis.
     */
    private void invalidateChunkCache(Chunk chunk) {
        ChunkUtils.invalidateChunkCache(chunk);
    }

    /**
     * Schedules asynchronous re-analysis of a chunk to populate cache for future use.
     * This is done with low priority to avoid impacting server performance.
     */
    private void scheduleAsyncReanalysis(Chunk chunk) {
        // Only schedule if the chunk is still loaded and we're not overwhelming the async system
        if (chunk.isLoaded() && !AsyncChunkAnalyzer.isActive()) {
            Bukkit.getScheduler().runTaskLater(LagXpert.getInstance(), () -> {
                if (chunk.isLoaded()) {
                    AsyncChunkAnalyzer.analyzeAndCache(chunk, result -> {
                        if (ConfigManager.isDebugEnabled() && result.isSuccess()) {
                            LagXpert.getInstance().getLogger().info(
                                    "[StorageListener] Re-cached chunk " + chunk.getX() + "," + chunk.getZ() +
                                            " after block placement in " + result.getAnalysisTimeMs() + "ms"
                            );
                        }
                    });
                }
            }, 20L); // Wait 1 second before re-analyzing to allow for multiple block placements
        }
    }

    private boolean shouldShowLimitReachedAlert(Material material) {
        if (material == null) return false;
        switch (material) {
            case HOPPER: return ConfigManager.shouldAlertOnHoppersLimitReached();
            case CHEST: case TRAPPED_CHEST: return ConfigManager.shouldAlertOnChestsLimitReached();
            case FURNACE: return ConfigManager.shouldAlertOnFurnacesLimitReached();
            case BLAST_FURNACE: return ConfigManager.shouldAlertOnBlastFurnacesLimitReached();
            case SMOKER: return ConfigManager.shouldAlertOnSmokersLimitReached();
            case BARREL: return ConfigManager.shouldAlertOnBarrelsLimitReached();
            case DROPPER: return ConfigManager.shouldAlertOnDroppersLimitReached();
            case DISPENSER: return ConfigManager.shouldAlertOnDispensersLimitReached();
            case TNT: return ConfigManager.shouldAlertOnTntLimitReached();
            case PISTON: case STICKY_PISTON: return ConfigManager.shouldAlertOnPistonsLimitReached();
            case OBSERVER: return ConfigManager.shouldAlertOnObserversLimitReached();
            default:
                // If it's any shulker box, use the generic shulker box alert toggle
                if (material.name().contains("SHULKER_BOX")) {
                    return ConfigManager.shouldAlertOnShulkerBoxesLimitReached();
                }
                return true;
        }
    }

    private boolean shouldShowNearLimitWarning(Material material) {
        if (material == null) return false;
        switch (material) {
            case HOPPER: return ConfigManager.shouldWarnOnHoppersNearLimit();
            case CHEST: case TRAPPED_CHEST: return ConfigManager.shouldWarnOnChestsNearLimit();
            case FURNACE: return ConfigManager.shouldWarnOnFurnacesNearLimit();
            case BLAST_FURNACE: return ConfigManager.shouldWarnOnBlastFurnacesNearLimit();
            case SMOKER: return ConfigManager.shouldWarnOnSmokersNearLimit();
            case BARREL: return ConfigManager.shouldWarnOnBarrelsNearLimit();
            case DROPPER: return ConfigManager.shouldWarnOnDroppersNearLimit();
            case DISPENSER: return ConfigManager.shouldWarnOnDispensersNearLimit();
            case TNT: return ConfigManager.shouldWarnOnTntNearLimit();
            case PISTON: case STICKY_PISTON: return ConfigManager.shouldWarnOnPistonsNearLimit();
            case OBSERVER: return ConfigManager.shouldWarnOnObserversNearLimit();
            default:
                if (material.name().contains("SHULKER_BOX")) {
                    return ConfigManager.shouldWarnOnShulkerBoxesNearLimit();
                }
                return true;
        }
    }

    private void fireChunkOverloadEvent(Chunk chunk, String cause) {
        Bukkit.getPluginManager().callEvent(new ChunkOverloadEvent(chunk, cause));
    }
    
    /**
     * Updates atomic counters after a block is successfully placed.
     * This maintains accurate real-time counts for performance-critical materials.
     */
    private void updateCountersAfterPlacement(Chunk chunk, BlockLimitConfig config) {
        // Update atomic counters for tracked materials
        if (config.getMaterial() == Material.TNT || 
            config.getMaterial() == Material.PISTON || 
            config.getMaterial() == Material.STICKY_PISTON) {
            
            ChunkDataCache.incrementAtomicCounter(chunk, config.getMaterial());
            
            if (ConfigManager.isDebugEnabled()) {
                int newCount = ChunkDataCache.getAtomicCounter(chunk, config.getMaterial());
                LagXpert.getInstance().getLogger().info(
                    "[StorageListener] Atomic counter updated for " + config.getMaterial() + 
                    " in chunk " + chunk.getX() + "," + chunk.getZ() + 
                    ". New count: " + newCount
                );
            }
        }
    }
    
    /**
     * Handles block breaking events to maintain accurate atomic counters.
     * This prevents counters from becoming out of sync when blocks are broken.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!ConfigManager.isStorageModuleEnabled()) {
            return;
        }
        
        Block block = event.getBlock();
        Material type = block.getType();
        Chunk chunk = block.getChunk();
        
        // Check if this is a tracked material
        BlockLimitConfig config = limitedBlocks.get(type);
        if (config != null) {
            // Update atomic counters for tracked materials
            if (type == Material.TNT || type == Material.PISTON || type == Material.STICKY_PISTON) {
                ChunkDataCache.decrementAtomicCounter(chunk, type);
                
                if (ConfigManager.isDebugEnabled()) {
                    int newCount = ChunkDataCache.getAtomicCounter(chunk, type);
                    LagXpert.getInstance().getLogger().info(
                        "[StorageListener] Atomic counter decremented for " + type + 
                        " in chunk " + chunk.getX() + "," + chunk.getZ() + 
                        ". New count: " + newCount
                    );
                }
            }
            
            // Always invalidate cache when blocks are broken
            invalidateChunkCache(chunk);
        }
    }
}