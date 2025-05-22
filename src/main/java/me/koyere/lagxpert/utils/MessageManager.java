package me.koyere.lagxpert.utils;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.Map;

/**
 * Utility class for retrieving and formatting plugin messages.
 * Messages are loaded from a FileConfiguration object, typically messages.yml.
 */
public class MessageManager {

    private static FileConfiguration messagesConfig; // Stores the loaded messages configuration
    private static final String DEFAULT_PREFIX = "&7[&bLagXpert&7] ";

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
}