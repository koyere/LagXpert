package me.koyere.lagxpert.system; // Or me.koyere.lagxpert.utils;

import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages cooldowns for player alerts to prevent spam.
 * Tracks when specific alerts were last sent to players and checks
 * if a cooldown period is active before allowing a new alert of the same type.
 */
public class AlertCooldownManager {

    // Structure: Player UUID -> (Alert Key -> Timestamp of last sent alert)
    // Alert Key could be e.g., "mobs_limit_chunk_X_Z_World" or "hoppers_near_limit_chunk_X_Z_World"
    private static final Map<UUID, Map<String, Long>> playerAlertCooldowns = new ConcurrentHashMap<>();

    /**
     * Checks if an alert with the given key can be sent to the player.
     * If the alert can be sent (i.e., not on cooldown), this method will also
     * update the last sent time for this alert key for the player.
     *
     * @param player   The player to check the cooldown for.
     * @param alertKey A unique string identifying the specific alert type and context
     * (e.g., "mobs_limit_c_10_20_world", "hoppers_near_c_5_-3_world_nether").
     * @return {@code true} if the alert can be sent (not on cooldown or cooldown disabled),
     * {@code false} if the alert is currently on cooldown for this player and key.
     */
    public static boolean canSendAlert(Player player, String alertKey) {
        if (player == null || alertKey == null || alertKey.isEmpty()) {
            return false; // Invalid parameters, don't send.
        }

        int cooldownSeconds = ConfigManager.getAlertCooldownDefaultSeconds();
        if (cooldownSeconds <= 0) {
            return true; // Cooldown system is disabled, always allow sending.
        }

        UUID playerUUID = player.getUniqueId();
        long currentTimeMillis = System.currentTimeMillis();

        // Get the cooldown map for this specific player.
        // computeIfAbsent ensures the inner map is created if it's the player's first alert.
        Map<String, Long> playerSpecificCooldowns = playerAlertCooldowns.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>());

        Long lastSentTimestamp = playerSpecificCooldowns.get(alertKey);

        if (lastSentTimestamp != null) {
            long timePassedMillis = currentTimeMillis - lastSentTimestamp;
            if (timePassedMillis < (cooldownSeconds * 1000L)) {
                // Cooldown is active for this specific alert and player.
                return false;
            }
        }

        // If no previous timestamp, or if cooldown has expired:
        // Update the timestamp and allow the alert to be sent.
        playerSpecificCooldowns.put(alertKey, currentTimeMillis);
        return true;
    }

    /**
     * Generates a unique alert key based on an alert type and chunk information.
     * This key is used to track cooldowns for specific alerts in specific locations.
     *
     * @param alertType A string identifying the type of alert (e.g., "mobs_limit", "hoppers_near_limit").
     * @param chunk     The chunk related to the alert.
     * @return A unique string key for the alert cooldown system.
     */
    public static String generateAlertKey(String alertType, org.bukkit.Chunk chunk) {
        if (alertType == null || chunk == null) {
            return "invalid_alert_key";
        }
        // Example key: "mobs_limit_world_c_10_25"
        return alertType + "_" + chunk.getWorld().getName() + "_c_" + chunk.getX() + "_" + chunk.getZ();
    }

    /**
     * Generates a unique alert key based on an alert type and a more general context string.
     * Useful for alerts not tied to a specific chunk, or when chunk context is already part of alertType.
     *
     * @param player The player receiving the alert (to make it player-specific implicitly via the outer map key).
     * @param baseAlertKey A string identifying the base type of alert (e.g., "redstone_action").
     * @param contextSpecifics Additional details to make the key unique for the situation (e.g., block location string).
     * @return A unique string key for the alert cooldown system.
     */
    public static String generateAlertKey(Player player, String baseAlertKey, String contextSpecifics) {
        if (player == null || baseAlertKey == null || contextSpecifics == null) {
            return "invalid_alert_key_contextual";
        }
        // Example: "redstone_action_world_100_64_200"
        // Player UUID is handled by the outer map, so not needed in the key itself here.
        return baseAlertKey + "_" + contextSpecifics.replace(" ", "_").replace(",", "");
    }


    // Optional: Method to clear cooldowns for a player if they log out, to prevent memory buildup.
    // This would be called from a PlayerQuitEvent listener.
    /**
     * Clears all recorded alert cooldowns for a specific player.
     * Useful to call when a player logs out to free up memory.
     *
     * @param playerUUID The UUID of the player whose cooldowns should be cleared.
     */
    public static void clearCooldownsForPlayer(UUID playerUUID) {
        if (playerUUID != null) {
            playerAlertCooldowns.remove(playerUUID);
        }
    }
}