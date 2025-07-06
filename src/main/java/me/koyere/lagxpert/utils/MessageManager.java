package me.koyere.lagxpert.utils;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import java.util.Map;

/**
 * Utility class for retrieving and formatting plugin messages.
 * Messages are loaded from a FileConfiguration object, typically messages.yml.
 */
public class MessageManager {

    private static FileConfiguration messagesConfig; // Stores the loaded messages configuration
    private static final String DEFAULT_PREFIX = "&7[&bLagXpert&7] ";
    private static final int ACTIONBAR_MAX_LENGTH = 100; // Maximum characters for ActionBar

    /**
     * Initializes the MessageManager with the loaded messages configuration.
     * This method should be called once, typically during plugin startup,
     * after messages.yml has been loaded into a FileConfiguration object.
     *
     * @param config The FileConfiguration object containing the messages.
     */
    public static void initialize(FileConfiguration config) {
        messagesConfig = config;
    }

    /**
     * Gets a raw message string from the loaded configuration by its path,
     * applies color codes, and provides a fallback for missing messages.
     *
     * @param path The path to the message string in messages.yml.
     * @return The color-translated message string, or a "missing message" indicator.
     */
    public static String get(String path) {
        if (messagesConfig == null) {
            return ChatColor.translateAlternateColorCodes('&', "&c[LagXpert Error] Messages not loaded! Path: " + path);
        }
        String rawMessage = messagesConfig.getString(path, "&cMissing message: " + path + "&r");
        return ChatColor.translateAlternateColorCodes('&', rawMessage);
    }

    /**
     * Gets a message string by its path, replaces placeholders using the provided map,
     * and applies color codes. Accepts Object values in the replacements map.
     *
     * @param path          The path to the message string in messages.yml.
     * @param replacements  A map where keys are placeholder names (without braces)
     * and values are their replacements (can be any Object, will be converted to String).
     * @return The formatted and color-translated message string.
     */
    public static String getFormatted(String path, Map<String, Object> replacements) { // MODIFIED: Map<String, Object>
        String baseMessage = get(path); // get() already handles color translation and missing paths
        if (replacements == null || replacements.isEmpty()) {
            return baseMessage;
        }
        for (Map.Entry<String, Object> entry : replacements.entrySet()) { // MODIFIED: Map.Entry<String, Object>
            // Using String.valueOf() to safely convert any object to its string representation.
            baseMessage = baseMessage.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return baseMessage;
    }

    /**
     * Retrieves the configured plugin prefix from messages.yml (path: "prefix").
     * Returns a default prefix if not configured or if messages are not loaded.
     * The returned prefix is color-translated.
     *
     * @return The color-translated plugin prefix.
     */
    public static String getPrefix() {
        if (messagesConfig == null) {
            return ChatColor.translateAlternateColorCodes('&', DEFAULT_PREFIX);
        }
        String prefix = messagesConfig.getString("prefix", DEFAULT_PREFIX);
        return ChatColor.translateAlternateColorCodes('&', prefix);
    }

    /**
     * Utility method to apply Bukkit color codes (&) to a string.
     *
     * @param input The string to colorize.
     * @return The colorized string, or an empty string if input is null.
     */
    public static String color(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    /**
     * Convenience method to get a prefixed message directly.
     *
     * @param path The path to the message string in messages.yml.
     * @return The prefixed and color-translated message string.
     */
    public static String getPrefixedMessage(String path) {
        return getPrefix() + get(path);
    }

    /**
     * Convenience method to get a prefixed and formatted message directly.
     * Accepts Object values in the replacements map.
     *
     * @param path          The path to the message string in messages.yml.
     * @param replacements  A map for placeholder replacements (values can be any Object).
     * @return The prefixed, formatted, and color-translated message string.
     */
    public static String getPrefixedFormattedMessage(String path, Map<String, Object> replacements) { // MODIFIED: Map<String, Object>
        return getPrefix() + getFormatted(path, replacements);
    }

    /**
     * Gets the configured message delivery method for restriction messages.
     * Defaults to CHAT if not configured.
     *
     * @return The MessageType for restriction messages.
     */
    public static MessageType getRestrictionMessageType() {
        if (messagesConfig == null) {
            return MessageType.CHAT;
        }
        String method = messagesConfig.getString("delivery.restrictions.method", "chat").toLowerCase();
        switch (method) {
            case "actionbar":
                return MessageType.ACTIONBAR;
            case "both":
                return MessageType.BOTH;
            default:
                return MessageType.CHAT;
        }
    }

    /**
     * Gets the configured default message delivery method.
     * Defaults to CHAT if not configured.
     *
     * @return The default MessageType.
     */
    public static MessageType getDefaultMessageType() {
        if (messagesConfig == null) {
            return MessageType.CHAT;
        }
        String method = messagesConfig.getString("delivery.default-method", "chat").toLowerCase();
        switch (method) {
            case "actionbar":
                return MessageType.ACTIONBAR;
            case "both":
                return MessageType.BOTH;
            default:
                return MessageType.CHAT;
        }
    }

    /**
     * Sends a message to a player using the specified delivery method.
     *
     * @param player The player to send the message to.
     * @param message The message to send (already formatted and colorized).
     * @param messageType The delivery method to use.
     */
    public static void sendMessage(Player player, String message, MessageType messageType) {
        if (player == null || message == null || messageType == null) {
            return;
        }

        switch (messageType) {
            case CHAT:
                player.sendMessage(message);
                break;
            case ACTIONBAR:
                sendActionBarMessage(player, message);
                break;
            case BOTH:
                player.sendMessage(message);
                sendActionBarMessage(player, message);
                break;
        }
    }

    /**
     * Sends a prefixed message to a player using the specified delivery method.
     *
     * @param player The player to send the message to.
     * @param path The path to the message in messages.yml.
     * @param messageType The delivery method to use.
     */
    public static void sendPrefixedMessage(Player player, String path, MessageType messageType) {
        String message = getPrefixedMessage(path);
        sendMessage(player, message, messageType);
    }

    /**
     * Sends a formatted prefixed message to a player using the specified delivery method.
     *
     * @param player The player to send the message to.
     * @param path The path to the message in messages.yml.
     * @param replacements A map for placeholder replacements.
     * @param messageType The delivery method to use.
     */
    public static void sendPrefixedFormattedMessage(Player player, String path, Map<String, Object> replacements, MessageType messageType) {
        String message = getPrefixedFormattedMessage(path, replacements);
        sendMessage(player, message, messageType);
    }

    /**
     * Sends a restriction message to a player using the configured restriction message type.
     *
     * @param player The player to send the message to.
     * @param path The path to the message in messages.yml.
     */
    public static void sendRestrictionMessage(Player player, String path) {
        MessageType messageType = getRestrictionMessageType();
        sendPrefixedMessage(player, path, messageType);
    }

    /**
     * Sends a formatted restriction message to a player using the configured restriction message type.
     *
     * @param player The player to send the message to.
     * @param path The path to the message in messages.yml.
     * @param replacements A map for placeholder replacements.
     */
    public static void sendFormattedRestrictionMessage(Player player, String path, Map<String, Object> replacements) {
        MessageType messageType = getRestrictionMessageType();
        sendPrefixedFormattedMessage(player, path, replacements, messageType);
    }

    /**
     * Sends an action bar message to a player.
     * If the message is too long, it will be truncated with "...".
     *
     * @param player The player to send the action bar message to.
     * @param message The message to send.
     */
    private static void sendActionBarMessage(Player player, String message) {
        if (player == null || message == null) {
            return;
        }

        // Strip color codes for length calculation
        String strippedMessage = ChatColor.stripColor(message);
        
        // Truncate message if too long for ActionBar
        if (strippedMessage.length() > ACTIONBAR_MAX_LENGTH) {
            // Find a good truncation point (preserve color codes)
            int truncateAt = ACTIONBAR_MAX_LENGTH - 3; // Reserve space for "..."
            String truncated = message;
            
            // Simple truncation - could be improved to preserve word boundaries
            if (message.length() > truncateAt) {
                truncated = message.substring(0, truncateAt) + "...";
            }
            message = truncated;
        }

        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        } catch (Exception e) {
            // Fallback to chat if ActionBar fails
            player.sendMessage(message);
        }
    }

    /**
     * Gets a shortened version of a message suitable for ActionBar display.
     * Attempts to get a message from path + ".short" first, then falls back to the full message.
     *
     * @param path The base path to the message in messages.yml.
     * @return A shortened message suitable for ActionBar.
     */
    public static String getShortMessage(String path) {
        String shortPath = path + ".short";
        if (messagesConfig != null && messagesConfig.contains(shortPath)) {
            return get(shortPath);
        }
        return get(path);
    }

    /**
     * Gets a prefixed shortened message suitable for ActionBar display.
     *
     * @param path The base path to the message in messages.yml.
     * @return A prefixed shortened message suitable for ActionBar.
     */
    public static String getPrefixedShortMessage(String path) {
        return getPrefix() + getShortMessage(path);
    }
}