package me.koyere.lagxpert.listeners;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.api.events.ChunkOverloadEvent;
import me.koyere.lagxpert.system.ActionLogger;
import me.koyere.lagxpert.system.AdaptiveThresholdEngine;
import me.koyere.lagxpert.system.AlertCooldownManager;
import me.koyere.lagxpert.system.EmergencyController;
import me.koyere.lagxpert.system.MobAIOptimizer;
import me.koyere.lagxpert.system.SmartMobManager;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.World;
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
 * Also warns the nearest player in the chunk if the mob count approaches 80% of
 * the limit,
 * subject to fine-grained alert configurations and alert cooldowns.
 * Spawn is bypassed if a player in the chunk has the bypass permission.
 */
public class EntityListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!ConfigManager.isMobsModuleEnabled()) {
            return;
        }

        // Apply AI optimization immediately
        if (event.getEntity() instanceof LivingEntity) {
            MobAIOptimizer.getInstance().optimizeEntity(event.getEntity());
        }

        Location spawnLocation = event.getLocation();
        Chunk chunk = spawnLocation.getChunk();

        // Emergency response: block environmental mob spawning outright while the
        // server is in a state that requests it. This is checked before the
        // per-chunk limit logic because it is a server-wide measure, not a
        // chunk-capacity decision.
        if (EmergencyController.getInstance().shouldBlockNaturalSpawns()
                && isEnvironmentalSpawn(event.getSpawnReason())
                && !hasBypassPlayerNearby(chunk)) {

            event.setCancelled(true);

            ActionLogger.getInstance().log(
                    ActionLogger.ActionType.SPAWN_BLOCKED,
                    chunk.getWorld().getName(),
                    chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ(),
                    "Natural spawn blocked by emergency state (" +
                            EmergencyController.getInstance().getCurrentState().name() +
                            "), reason: " + event.getSpawnReason().name(),
                    1, "emergency", true, 0);

            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info(
                        "Blocked " + event.getSpawnReason().name() + " spawn at " +
                                locationToString(spawnLocation) + " due to emergency state " +
                                EmergencyController.getInstance().getCurrentState().name() + ".");
            }
            return;
        }

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
                                        " bypassed due to player " + player.getName() + " having permission.");
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

        // Get the effective mob limit — factors in EmergencyController state multiplier
        int mobLimit = getEffectiveMobLimit(playersInChunk, chunk.getWorld());
        int nearLimitThreshold = (int) (mobLimit * 0.80);

        if (livingEntitiesInChunk >= mobLimit) {
            event.setCancelled(true);
            fireChunkOverloadEvent(chunk, "mobs_limit_reached");

            // Proactively clean the chunk so the next spawn attempt can succeed
            SmartMobManager.getInstance().processChunkImmediately(chunk);

            // Log corrective action to audit trail
            String chunkKey = chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
            ActionLogger.getInstance().log(
                    ActionLogger.ActionType.SPAWN_BLOCKED,
                    chunk.getWorld().getName(),
                    chunkKey,
                    "Count: " + livingEntitiesInChunk + ", Limit: " + mobLimit,
                    1, "auto", true, 0);

            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info(
                        "Cancelled mob spawn at " + locationToString(spawnLocation) +
                                " (Chunk: " + chunk.getX() + "," + chunk.getZ() + "). " +
                                "Count: " + livingEntitiesInChunk + ", Limit: " + mobLimit);
            }

            if (ConfigManager.isAlertsModuleEnabled() && ConfigManager.shouldAlertOnMobsLimitReached()
                    && !playersInChunk.isEmpty()) {
                String limitMessageKey = "limits.mobs"; // The key for the message in messages.yml
                // Generate a unique key for this specific alert condition (type and chunk)
                String alertCooldownKey = AlertCooldownManager.generateAlertKey("mobs_limit_reached", chunk);

                for (Player player : playersInChunk) {
                    // Only send alerts to players with permission to receive them
                    if (player.hasPermission("lagxpert.alerts.receive")
                            || player.hasPermission("lagxpert.alerts.mobs")) {
                        // Check cooldown for this player and this specific alert
                        if (AlertCooldownManager.canSendAlert(player, alertCooldownKey)) {
                            MessageManager.sendRestrictionMessage(player, limitMessageKey);
                        }
                    }
                }
            }
        } else if (livingEntitiesInChunk >= nearLimitThreshold && mobLimit > 0) {
            if (ConfigManager.isAlertsModuleEnabled() && ConfigManager.shouldWarnOnMobsNearLimit()
                    && !playersInChunk.isEmpty()) {
                Player targetPlayer = findClosestPlayerToLocation(playersInChunk, spawnLocation);
                if (targetPlayer != null && (targetPlayer.hasPermission("lagxpert.alerts.receive")
                        || targetPlayer.hasPermission("lagxpert.alerts.mobs"))) {
                    // Generate a unique key for this specific alert condition (type and chunk)
                    String alertCooldownKey = AlertCooldownManager.generateAlertKey("mobs_near_limit", chunk);

                    // Check cooldown for the target player and this specific alert
                    if (AlertCooldownManager.canSendAlert(targetPlayer, alertCooldownKey)) {
                        Map<String, Object> placeholders = new HashMap<>();
                        placeholders.put("used", String.valueOf(livingEntitiesInChunk));
                        placeholders.put("max", String.valueOf(mobLimit));
                        placeholders.put("type", "mobs"); // Consistent with how messages.yml expects it

                        MessageManager.sendFormattedRestrictionMessage(targetPlayer, "limits.near-limit", placeholders);
                    }
                }
            }
        }
    }

    /**
     * Determines whether a spawn reason represents environmental spawning
     * pressure that is safe to suppress during an emergency.
     *
     * Player- and plugin-driven spawns (spawn eggs, breeding, commands, custom
     * plugin spawns) are never suppressed: blocking those would look like the
     * server is broken rather than under load. The exact set is operator
     * configurable in emergency-controller.yml.
     *
     * Comparison is by enum name rather than by constant reference, because the
     * SpawnReason enum gained new values across the 1.16 to 1.21 range this
     * plugin supports and referencing a missing constant would throw at runtime.
     */
    private boolean isEnvironmentalSpawn(CreatureSpawnEvent.SpawnReason reason) {
        if (reason == null) {
            return false;
        }
        return EmergencyController.getInstance().isBlockedSpawnReason(reason.name());
    }

    /**
     * Returns true if any player in the chunk holds the mob bypass permission.
     *
     * Mirrors the bypass semantics of the per-chunk limit check so that a plot
     * owner with bypass keeps working spawners during an emergency.
     */
    private boolean hasBypassPlayerNearby(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player && ((Player) entity).hasPermission("lagxpert.bypass.mobs")) {
                return true;
            }
        }
        return false;
    }

    private Player findClosestPlayerToLocation(List<Player> players, Location location) {
        if (players == null || players.isEmpty()) {
            return null;
        }
        Player closestPlayer = null;
        double minDistanceSquared = Double.MAX_VALUE;

        for (Player player : players) {
            if (!player.isValid() || (location.getWorld() != null && !player.getWorld().equals(location.getWorld()))) { // Added
                                                                                                                        // null
                                                                                                                        // check
                                                                                                                        // for
                                                                                                                        // location.getWorld()
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

    /**
     * Gets the effective mob limit for a chunk, considering custom permission-based
     * limits AND the EmergencyController's dynamic state multiplier.
     * If multiple players are in the chunk, uses the highest custom limit found.
     * Priority: Highest custom permission limit > EmergencyController-adjusted config limit
     *
     * @param playersInChunk List of players in the chunk
     * @param world          The world the chunk is in
     * @return The effective mob limit for this chunk
     */
    private int getEffectiveMobLimit(List<Player> playersInChunk, World world) {
        int highestCustomLimit = 0;

        // Check each player for custom mob limits
        for (Player player : playersInChunk) {
            int playerCustomLimit = getCustomLimitFromPermissions(player, "lagxpert.limits.mobs");
            if (playerCustomLimit > highestCustomLimit) {
                highestCustomLimit = playerCustomLimit;
            }
        }

        // A permission-granted limit is an explicit operator decision for that
        // player, so it is honored verbatim and not adaptively scaled.
        if (highestCustomLimit > 0) {
            return highestCustomLimit;
        }

        // Route through the adaptive engine, which combines continuous health
        // scaling with the EmergencyController's per-state mob cap multiplier and
        // applies whichever is more restrictive.
        return AdaptiveThresholdEngine.getInstance().getEffectiveMobLimit(world);
    }

    /**
     * Extracts custom limit from player permissions.
     * Looks for permissions like "lagxpert.limits.mobs.25" and returns the highest
     * number found.
     *
     * @param player           The player to check permissions for
     * @param permissionPrefix The permission prefix (e.g., "lagxpert.limits.mobs")
     * @return The highest custom limit found, or 0 if none
     */
    private int getCustomLimitFromPermissions(Player player, String permissionPrefix) {
        int highestLimit = 0;

        // Check all permissions the player has
        for (org.bukkit.permissions.PermissionAttachmentInfo permInfo : player.getEffectivePermissions()) {
            String permission = permInfo.getPermission();

            // Check if this permission matches our pattern
            if (permission.startsWith(permissionPrefix + ".") && permInfo.getValue()) {
                // Extract the number part
                String numberPart = permission.substring((permissionPrefix + ".").length());
                try {
                    int limit = Integer.parseInt(numberPart);
                    if (limit > highestLimit) {
                        highestLimit = limit;
                    }
                } catch (NumberFormatException ignored) {
                    // Not a valid number, skip this permission
                }
            }
        }

        return highestLimit;
    }

    private void fireChunkOverloadEvent(Chunk chunk, String cause) {
        ChunkOverloadEvent event = new ChunkOverloadEvent(chunk, cause);
        Bukkit.getPluginManager().callEvent(event);
    }

    private String locationToString(Location loc) {
        if (loc == null)
            return "null_location";
        String worldName = (loc.getWorld() != null) ? loc.getWorld().getName() : "unknown_world";
        return worldName +
                String.format(", X:%.1f, Y:%.1f, Z:%.1f", loc.getX(), loc.getY(), loc.getZ());
    }
}
