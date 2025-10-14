package me.koyere.lagxpert.listeners;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.system.RecentlyBrokenBlocksTracker;
import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener for the enhanced item cleaner system.
 * Tracks when players break blocks to provide grace periods for dropped items.
 * This prevents the item cleaner from removing items that players just broke
 * but haven't collected yet.
 */
public class ItemCleanerListener implements Listener {
    
    /**
     * Handles block break events to track recently broken blocks.
     * This creates a grace period during which items dropped from broken blocks
     * will not be cleaned up by the item cleaner.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // Only track if item cleaner module and tracking are enabled
        if (!ConfigManager.isItemCleanerModuleEnabled() || !ConfigManager.isBrokenBlockTrackingEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material material = block.getType();
        
        // Skip if player is in creative mode (no drops)
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        
        // Skip blocks that don't typically drop items
        if (isNonDroppableBlock(material)) {
            return;
        }
        
        // Get grace period from configuration (with fallback to default)
        long gracePeriodMs = RecentlyBrokenBlocksTracker.getGracePeriodForMaterial(material);
        if (gracePeriodMs <= 0) {
            return;
        }

        // Record the broken block with grace period
        RecentlyBrokenBlocksTracker.recordBrokenBlock(player, material, block.getLocation(), gracePeriodMs);
        
        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info(
                "[ItemCleanerListener] Recorded broken block: " + material + 
                " by " + player.getName() + 
                " at " + block.getX() + "," + block.getY() + "," + block.getZ() + 
                " (grace period: " + (gracePeriodMs / 1000) + "s)"
            );
        }
    }
    
    /**
     * Cleans up broken block records when a player leaves the server.
     * This prevents memory leaks and ensures records don't persist unnecessarily.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Clear all broken block records for this player
        if (ConfigManager.isBrokenBlockTrackingEnabled()) {
            RecentlyBrokenBlocksTracker.clearPlayerRecords(player.getUniqueId());
        }
        
        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info(
                "[ItemCleanerListener] Cleared broken block records for " + player.getName()
            );
        }
    }
    
    /**
     * Determines if a block typically doesn't drop items when broken.
     * These blocks don't need grace period tracking.
     * 
     * @param material The material to check
     * @return true if the block doesn't drop items
     */
    private boolean isNonDroppableBlock(Material material) {
        switch (material) {
            // Liquids and gases
            case WATER:
            case LAVA:
            case AIR:
            
            // Fire and light sources that don't drop
            case FIRE:
            
            // Bedrock and other unbreakable blocks
            case BEDROCK:
            case BARRIER:
            
            // Portal blocks
            case NETHER_PORTAL:
            case END_PORTAL:
            case END_PORTAL_FRAME:
            
            // Crops and plants that have special drop rules
            case WHEAT:
            case CARROTS:
            case POTATOES:
            case BEETROOTS:
            
            // Redstone components (handled separately)
            case REDSTONE_WIRE:
            case TRIPWIRE:
            
            // Spawners (special case - valuable but no standard drops)
            case SPAWNER:
                return true;
                
            default:
                return false;
        }
    }
    
    /**
     * Gets the appropriate grace period for a specific material.
     * Different materials may need different grace periods based on how
     * long players typically take to collect them.
     * 
     * @param material The material that was broken
     * @return Grace period in milliseconds
     */
    // Grace periods are now fully driven by configuration via ConfigManager/RecentlyBrokenBlocksTracker
}
