package me.koyere.lagxpert.listeners;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.api.events.ChunkOverloadEvent;
import me.koyere.lagxpert.system.AlertCooldownManager; // IMPORT ADDED for Alert Cooldown
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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Listens for block placements and restricts storage-related blocks
 * if their per-chunk limits are exceeded. Alerts players on placement
 * if limits are reached or nearly reached, subject to alert configurations and cooldowns.
 * Optimized to count Tile Entities efficiently where applicable.
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
        Block blockBeingPlaced = event.getBlock(); // Renamed for clarity vs. block in chunk
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
                return;
            }

            int currentCount;
            if (config.isTileEntity()) {
                currentCount = countTileEntitiesInChunk(chunk, type);
            } else {
                currentCount = countAllBlocksOfTypeInChunk(chunk, type);
            }

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
            }
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
            // For SHULKER_BOX, we rely on the specific colored shulker material if defined, or general if used
            case TNT: return ConfigManager.shouldAlertOnTntLimitReached();
            case PISTON: case STICKY_PISTON: return ConfigManager.shouldAlertOnPistonsLimitReached();
            case OBSERVER: return ConfigManager.shouldAlertOnObserversLimitReached();
            default:
                // If it's any shulker box, use the generic shulker box alert toggle
                if (material.name().contains("SHULKER_BOX")) { // More robust check for all shulker types
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
                if (material.name().contains("SHULKER_BOX")) { // More robust check for all shulker types
                    return ConfigManager.shouldWarnOnShulkerBoxesNearLimit();
                }
                return true;
        }
    }

    private int countTileEntitiesInChunk(Chunk chunk, Material material) {
        int count = 0;
        if (material == null || chunk == null || !chunk.isLoaded()) return 0;
        for (BlockState blockState : chunk.getTileEntities()) {
            if (blockState.getType() == material) {
                count++;
            }
        }
        return count;
    }

    private int countAllBlocksOfTypeInChunk(Chunk chunk, Material material) {
        int count = 0;
        if (material == null || chunk == null || !chunk.isLoaded()) return 0;
        if (ConfigManager.isDebugEnabled() && LagXpert.getInstance() != null) {
            LagXpert.getInstance().getLogger().info("[LagXpert] StorageListener: Performing slow block scan for " + material.name() +
                    " in chunk " + chunk.getX() + "," + chunk.getZ());
        }
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

    private void fireChunkOverloadEvent(Chunk chunk, String cause) {
        Bukkit.getPluginManager().callEvent(new ChunkOverloadEvent(chunk, cause));
    }
}