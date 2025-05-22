package me.koyere.lagxpert.listeners;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.api.events.ChunkOverloadEvent;
import me.koyere.lagxpert.system.AlertCooldownManager; // Import AlertCooldownManager
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prevents mob spawning if the chunk has exceeded the configured mob limit.
 * Also warns the nearest player in the chunk if the mob count approaches 80% of the limit,
 * subject to fine-grained alert configurations and alert cooldowns.
 * Spawn is bypassed if a player in the chunk has the bypass permission.
 */
public class EntityListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!ConfigManager.isMobsModuleEnabled()) {
            return;
        }

        Location spawnLocation = event.getLocation();
        Chunk chunk = spawnLocation.getChunk();

        List<Player> playersInChunk = new ArrayList<>();
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player) {
                Player player = (Player) entity; // Java 11 compatible cast
                playersInChunk.add(player);
            }
        }

        if (!playersInChunk.isEmpty()) {
            for (Player player : playersInChunk) {
                if (player.hasPermission("lagxpert.bypass.mobs")) {
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().info(
                                "Mob spawn at " + locationToString(spawnLocation) +
                                        " (Chunk: " + chunk.getX() + "," + chunk.getZ() + ")" +
                                        " bypassed due to player " + player.getName() + " having permission."
                        );
                    }
                    return;
                }
            }
        }

        int livingEntitiesInChunk = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity) {
                livingEntitiesInChunk++;
            }
        }

        int mobLimit = ConfigManager.getMaxMobsPerChunk();
        int nearLimitThreshold = (int) (mobLimit * 0.80);

        if (livingEntitiesInChunk >= mobLimit) {
            event.setCancelled(true);
            fireChunkOverloadEvent(chunk, "mobs_limit_reached");

            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info(
                        "Cancelled mob spawn at " + locationToString(spawnLocation) +
                                " (Chunk: " + chunk.getX() + "," + chunk.getZ() + "). " +
                                "Count: " + livingEntitiesInChunk + ", Limit: " + mobLimit
                );
            }

            if (ConfigManager.isAlertsModuleEnabled() && ConfigManager.shouldAlertOnMobsLimitReached() && !playersInChunk.isEmpty()) {
                String limitMessageKey = "limits.mobs"; // The key for the message in messages.yml
                // Generate a unique key for this specific alert condition (type and chunk)
                String alertCooldownKey = AlertCooldownManager.generateAlertKey("mobs_limit_reached", chunk);

                for (Player player : playersInChunk) {
                    // Check cooldown for this player and this specific alert
                    if (AlertCooldownManager.canSendAlert(player, alertCooldownKey)) {
                        player.sendMessage(MessageManager.getPrefixedMessage(limitMessageKey));
                    }
                }
            }
        } else if (livingEntitiesInChunk >= nearLimitThreshold && mobLimit > 0) {
            if (ConfigManager.isAlertsModuleEnabled() && ConfigManager.shouldWarnOnMobsNearLimit() && !playersInChunk.isEmpty()) {
                Player targetPlayer = findClosestPlayerToLocation(playersInChunk, spawnLocation);
                if (targetPlayer != null) {
                    // Generate a unique key for this specific alert condition (type and chunk)
                    String alertCooldownKey = AlertCooldownManager.generateAlertKey("mobs_near_limit", chunk);

                    // Check cooldown for the target player and this specific alert
                    if (AlertCooldownManager.canSendAlert(targetPlayer, alertCooldownKey)) {
                        Map<String, Object> placeholders = new HashMap<>();
                        placeholders.put("used", String.valueOf(livingEntitiesInChunk));
                        placeholders.put("max", String.valueOf(mobLimit));
                        placeholders.put("type", "mobs"); // Consistent with how messages.yml expects it

                        String nearLimitMessage = MessageManager.getPrefixedFormattedMessage("limits.near-limit", placeholders);
                        targetPlayer.sendMessage(nearLimitMessage);
                    }
                }
            }
        }
    }

    private Player findClosestPlayerToLocation(List<Player> players, Location location) {
        if (players == null || players.isEmpty()) {
            return null;
        }
        Player closestPlayer = null;
        double minDistanceSquared = Double.MAX_VALUE;

        for (Player player : players) {
            if (!player.isValid() || (location.getWorld() != null && !player.getWorld().equals(location.getWorld()))) { // Added null check for location.getWorld()
                continue;
            }
            double distanceSquared = player.getLocation().distanceSquared(location);
            if (distanceSquared < minDistanceSquared) {
                minDistanceSquared = distanceSquared;
                closestPlayer = player;
            }
        }
        return closestPlayer;
    }

    private void fireChunkOverloadEvent(Chunk chunk, String cause) {
        ChunkOverloadEvent event = new ChunkOverloadEvent(chunk, cause);
        Bukkit.getPluginManager().callEvent(event);
    }

    private String locationToString(Location loc) {
        if (loc == null) return "null_location";
        String worldName = (loc.getWorld() != null) ? loc.getWorld().getName() : "unknown_world";
        return worldName +
                String.format(", X:%.1f, Y:%.1f, Z:%.1f", loc.getX(), loc.getY(), loc.getZ());
    }
}