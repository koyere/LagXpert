package me.koyere.lagxpert.listeners;

import me.koyere.lagxpert.api.events.ChunkOverloadEvent;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Listens for block placements and restricts storage blocks
 * like hoppers, chests, and furnaces if limits are exceeded per chunk.
 * Also warns players when limits are nearly reached (80% threshold).
 */
public class StorageListener implements Listener {

    @EventHandler
    public void onStoragePlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Chunk chunk = block.getChunk();
        Player player = event.getPlayer();
        Material type = block.getType();

        // Hopper check
        if (type == Material.HOPPER && !player.hasPermission("lagxpert.bypass.hoppers")) {
            int count = countBlocksInChunk(chunk, Material.HOPPER);
            int limit = ConfigManager.getMaxHoppersPerChunk();
            if (count >= limit) {
                fireChunkOverloadEvent(chunk, "hoppers");
                event.setCancelled(true);
                player.sendMessage(MessageManager.getPrefix() + MessageManager.get("limits.hopper"));
                return;
            } else if (count >= (limit * 0.8)) {
                player.sendMessage(MessageManager.getPrefix() + MessageManager.get("limits.near-limit")
                        .replace("{used}", String.valueOf(count))
                        .replace("{max}", String.valueOf(limit)));
            }
        }

        // Chest check
        if (type == Material.CHEST && !player.hasPermission("lagxpert.bypass.chests")) {
            int count = countBlocksInChunk(chunk, Material.CHEST);
            int limit = ConfigManager.getMaxChestsPerChunk();
            if (count >= limit) {
                fireChunkOverloadEvent(chunk, "chests");
                event.setCancelled(true);
                player.sendMessage(MessageManager.getPrefix() + MessageManager.get("limits.chest"));
                return;
            } else if (count >= (limit * 0.8)) {
                player.sendMessage(MessageManager.getPrefix() + MessageManager.get("limits.near-limit")
                        .replace("{used}", String.valueOf(count))
                        .replace("{max}", String.valueOf(limit)));
            }
        }

        // Furnace check
        if (type == Material.FURNACE && !player.hasPermission("lagxpert.bypass.furnaces")) {
            int count = countBlocksInChunk(chunk, Material.FURNACE);
            int limit = ConfigManager.getMaxFurnacesPerChunk();
            if (count >= limit) {
                fireChunkOverloadEvent(chunk, "furnaces");
                event.setCancelled(true);
                player.sendMessage(MessageManager.getPrefix() + MessageManager.get("limits.furnace"));
            } else if (count >= (limit * 0.8)) {
                player.sendMessage(MessageManager.getPrefix() + MessageManager.get("limits.near-limit")
                        .replace("{used}", String.valueOf(count))
                        .replace("{max}", String.valueOf(limit)));
            }
        }
    }

    private int countBlocksInChunk(Chunk chunk, Material type) {
        int count = 0;
        for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (chunk.getBlock(x, y, z).getType() == type) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private void fireChunkOverloadEvent(Chunk chunk, String cause) {
        ChunkOverloadEvent event = new ChunkOverloadEvent(chunk, cause);
        Bukkit.getPluginManager().callEvent(event);
    }
}
