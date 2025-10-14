package me.koyere.lagxpert.listeners;

import me.koyere.lagxpert.system.ChunkManager;
import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Captures lightweight chunk activity events for the smart chunk management system.
 */
public class ChunkActivityListener implements Listener {

    private boolean isTrackingActive() {
        return ConfigManager.isChunkManagementModuleEnabled() && ConfigManager.isChunkActivityTrackingEnabled();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isTrackingActive() || !ConfigManager.shouldTrackBlockChanges()) {
            return;
        }
        ChunkManager.recordBlockChange(event.getBlockPlaced().getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isTrackingActive() || !ConfigManager.shouldTrackBlockChanges()) {
            return;
        }
        ChunkManager.recordBlockChange(event.getBlock().getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!isTrackingActive() || !ConfigManager.shouldTrackEntityChanges()) {
            return;
        }
        ChunkManager.recordEntityActivity(event.getLocation().getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isTrackingActive() || !ConfigManager.shouldTrackEntityChanges()) {
            return;
        }
        Entity entity = event.getEntity();
        ChunkManager.recordEntityActivity(entity.getLocation().getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isTrackingActive() || !ConfigManager.shouldTrackPlayerVisits()) {
            return;
        }

        Chunk fromChunk = event.getFrom().getChunk();
        Chunk toChunk = event.getTo() != null ? event.getTo().getChunk() : null;

        if (toChunk == null || (fromChunk.getX() == toChunk.getX() && fromChunk.getZ() == toChunk.getZ()
                && fromChunk.getWorld().equals(toChunk.getWorld()))) {
            return; // No chunk change
        }

        Player player = event.getPlayer();
        ChunkManager.recordPlayerActivity(player, toChunk);
    }
}

