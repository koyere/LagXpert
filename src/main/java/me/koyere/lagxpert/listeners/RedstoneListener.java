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
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.List;

/**
 * Controls redstone activity to prevent lag caused by persistent signals.
 * Exempt if player with bypass is nearby.
 */
public class RedstoneListener implements Listener {

    @EventHandler
    public void onRedstoneChange(BlockRedstoneEvent event) {
        if (!ConfigManager.isRedstoneControlEnabled()) return;

        Block block = event.getBlock();
        Chunk chunk = block.getChunk();

        // Bypass if player nearby has bypass permission
        List<Player> nearby = block.getWorld().getPlayers();
        for (Player player : nearby) {
            if (player.getLocation().distance(block.getLocation()) <= 16 &&
                    player.hasPermission("lagxpert.bypass.redstone")) {
                return;
            }
        }

        // Only act if signal stays active too long (simulated control)
        if (event.getNewCurrent() > 0 && block.getType() == Material.REDSTONE_WIRE) {
            // Fire an event just in case other plugins want to act
            fireChunkOverloadEvent(chunk, "redstone");
            block.setType(Material.AIR); // Cut the redstone
            block.getWorld().playSound(block.getLocation(), "block.note_block.bass", 0.5f, 0.5f);

            if (ConfigManager.areAlertsEnabled()) {
                chunk.getWorld().getPlayers().forEach(player -> {
                    if (player.getLocation().getChunk().equals(chunk)) {
                        player.sendMessage(MessageManager.getPrefix() + MessageManager.get("limits.redstone"));
                    }
                });
            }
        }
    }

    private void fireChunkOverloadEvent(Chunk chunk, String cause) {
        ChunkOverloadEvent event = new ChunkOverloadEvent(chunk, cause);
        Bukkit.getPluginManager().callEvent(event);
    }
}
