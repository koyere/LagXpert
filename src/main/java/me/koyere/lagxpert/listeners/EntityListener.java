package me.koyere.lagxpert.listeners;

import me.koyere.lagxpert.api.events.ChunkOverloadEvent;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.List;

/**
 * Prevents mob spawning if the chunk has exceeded the configured mob limit,
 * and warns players if the mob count reaches 80% of the limit.
 */
public class EntityListener implements Listener {

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Chunk chunk = event.getLocation().getChunk();

        // Check if a nearby player in this chunk has bypass
        boolean hasBypass = false;
        List<Player> players = chunk.getWorld().getPlayers();

        for (Player player : players) {
            if (player.getLocation().getChunk().equals(chunk)) {
                if (player.hasPermission("lagxpert.bypass.mobs")) {
                    hasBypass = true;
                    break;
                }
            }
        }

        if (hasBypass) return;

        // Count all living entities in the chunk
        int livingEntities = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity) {
                livingEntities++;
            }
        }

        int limit = ConfigManager.getMaxMobsPerChunk();

        if (livingEntities >= limit) {
            fireChunkOverloadEvent(chunk, "mobs");
            event.setCancelled(true);

            if (ConfigManager.areAlertsEnabled()) {
                for (Player player : players) {
                    if (player.getLocation().getChunk().equals(chunk)) {
                        player.sendMessage(MessageManager.getPrefix() + MessageManager.get("limits.mobs"));
                    }
                }
            }
        } else if (livingEntities >= (limit * 0.8)) {
            // Warn only the closest player who might be responsible
            Player target = findClosestPlayerInChunk(chunk, event.getLocation().getX(), event.getLocation().getZ(), players);
            if (target != null && ConfigManager.areAlertsEnabled()) {
                target.sendMessage(MessageManager.getPrefix() +
                        MessageManager.get("limits.near-limit")
                                .replace("{used}", String.valueOf(livingEntities))
                                .replace("{max}", String.valueOf(limit)));
            }
        }
    }

    /**
     * Finds the closest player in the chunk to a specific X/Z coordinate.
     */
    private Player findClosestPlayerInChunk(Chunk chunk, double x, double z, List<Player> players) {
        Player closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (Player player : players) {
            if (player.getLocation().getChunk().equals(chunk)) {
                double dist = player.getLocation().distanceSquared(chunk.getBlock(0, 64, 0).getLocation());
                if (dist < closestDistance) {
                    closest = player;
                    closestDistance = dist;
                }
            }
        }
        return closest;
    }

    private void fireChunkOverloadEvent(Chunk chunk, String cause) {
        ChunkOverloadEvent event = new ChunkOverloadEvent(chunk, cause);
        Bukkit.getPluginManager().callEvent(event);
    }
}

