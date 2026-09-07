package me.koyere.lagxpert.utils;

import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Utility class for retrieving and formatting plugin messages.
 * Messages are loaded from a FileConfiguration object, typically messages.yml.
 *
 * Supports structured message nodes where each message can have 'full' and 'short'
 * variants. When a path points to a configuration section, the 'full' sub-key is
 * resolved automatically for standard retrieval, and 'short' is used for ActionBar.
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
     * Supports structured message nodes: if the path points to a configuration
     * section (e.g., with 'full' and 'short' sub-keys), the 'full' variant
     * is returned automatically. This prevents MemorySection objects from
     * being displayed as raw text.
     *
     * @param path The path to the message string in messages.yml.
     * @return The color-translated message string, or a "missing message" indicator.
     */
    public static String get(String path) {
        if (messagesConfig == null) {
            return ChatColor.translateAlternateColorCodes('&', "&c[LagXpert Error] Messages not loaded! Path: " + path);
        }

        // If the path points to a section (structured node), resolve to '.full'
        if (messagesConfig.isConfigurationSection(path)) {
            String fullPath = path + ".full";
            String rawMessage = messagesConfig.getString(fullPath, "&cMissing message: " + path + "&r");
            return ChatColor.translateAlternateColorCodes('&', rawMessage);
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
    public static String getFormatted(String path, Map<String, Object> replacements) {
        // Substitution must happen BEFORE colour translation. Doing it the other way
        // round means any '&' code contained in a replacement value is inserted after
        // the translation pass and is rendered to the player literally, e.g. "&aNORMAL".
        String rawMessage = getRaw(path);

        if (replacements != null && !replacements.isEmpty()) {
            for (Map.Entry<String, Object> entry : replacements.entrySet()) {
                rawMessage = rawMessage.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }

        return ChatColor.translateAlternateColorCodes('&', rawMessage);
    }

    /**
     * Resolves the ActionBar (short) variant of a message and substitutes
     * placeholders before translating colour codes.
     *
     * Same ordering requirement as {@link #getFormatted}: substituting after
     * translation would render any colour code inside a replacement literally.
     */
    private static String formatShort(String path, Map<String, Object> replacements) {
        String raw = getRawShortMessage(path);
        if (replacements != null) {
            for (Map.Entry<String, Object> entry : replacements.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    /**
     * Returns the short variant of a message without colour translation,
     * falling back to the full variant and then the raw value.
     */
    private static String getRawShortMessage(String path) {
        if (messagesConfig == null) {
            return "&cMessages not loaded: " + path;
        }
        if (messagesConfig.isConfigurationSection(path)) {
            String shortValue = messagesConfig.getString(path + ".short", null);
            if (shortValue != null) {
                return shortValue;
            }
            return messagesConfig.getString(path + ".full", "&cMissing message: " + path + "&r");
        }
        String legacyShort = messagesConfig.getString(path + ".short", null);
        if (legacyShort != null) {
            return legacyShort;
        }
        return messagesConfig.getString(path, "&cMissing message: " + path + "&r");
    }

    /**
     * Returns a message if it exists in messages.yml, otherwise the supplied
     * default, with placeholders substituted and colours translated.
     *
     * Lets interface text be translatable without every key having to exist:
     * an operator who has not regenerated messages.yml still sees sensible
     * English rather than "Missing message: ...". Adding the key to messages.yml
     * overrides it, and {@code /lagxpert reload} applies the change.
     *
     * @param path         key in messages.yml
     * @param def          fallback used when the key is absent
     * @param replacements optional placeholders, may be null
     */
    public static String getOrDefault(String path, String def, Map<String, Object> replacements) {
        String raw = null;
        if (messagesConfig != null) {
            if (messagesConfig.isConfigurationSection(path)) {
                raw = messagesConfig.getString(path + ".full", null);
            } else {
                raw = messagesConfig.getString(path, null);
            }
        }
        if (raw == null) {
            raw = def;
        }
        if (raw == null) {
            return "";
        }
        if (replacements != null && !replacements.isEmpty()) {
            for (Map.Entry<String, Object> entry : replacements.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    /**
     * Returns a list of lines from messages.yml, or the supplied default list.
     *
     * Used for interface tooltips, where the number of lines is part of the
     * translation rather than fixed by code.
     */
    public static java.util.List<String> getListOrDefault(String path,
                                                          java.util.List<String> def,
                                                          Map<String, Object> replacements) {
        java.util.List<String> raw = null;
        if (messagesConfig != null && messagesConfig.isList(path)) {
            raw = messagesConfig.getStringList(path);
            if (raw.isEmpty()) {
                raw = null;
            }
        }
        if (raw == null) {
            raw = def;
        }
        if (raw == null) {
            return java.util.Collections.emptyList();
        }

        java.util.List<String> out = new java.util.ArrayList<>(raw.size());
        for (String line : raw) {
            String value = line == null ? "" : line;
            if (replacements != null && !replacements.isEmpty()) {
                for (Map.Entry<String, Object> entry : replacements.entrySet()) {
                    value = value.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
                }
            }
            out.add(ChatColor.translateAlternateColorCodes('&', value));
        }
        return out;
    }

    /**
     * Returns a message exactly as written in messages.yml, without translating
     * colour codes.
     *
     * Used by {@link #getFormatted} so that placeholder values may themselves
     * contain colour codes and still be translated.
     *
     * @param path The path to the message string in messages.yml.
     * @return The untranslated message, or a "missing message" indicator.
     */
    public static String getRaw(String path) {
        if (messagesConfig == null) {
            return "&c[LagXpert Error] Messages not loaded! Path: " + path;
        }
        if (messagesConfig.isConfigurationSection(path)) {
            return messagesConfig.getString(path + ".full", "&cMissing message: " + path + "&r");
        }
        return messagesConfig.getString(path, "&cMissing message: " + path + "&r");
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
     * Gets a translated name for a block or entity type from the translations section.
     * Falls back to the provided default if the key is not found.
     *
     * @param key          The translation key (e.g., "hoppers", "chests", "mobs").
     * @param defaultName  The fallback name if the key is missing.
     * @return The translated name.
     */
    public static String getTranslation(String key, String defaultName) {
        if (messagesConfig == null) {
            return defaultName;
        }
        return messagesConfig.getString("translations." + key, defaultName);
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
    public static String getPrefixedFormattedMessage(String path, Map<String, Object> replacements) {
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
     * Automatically selects the short message variant for ActionBar delivery.
     *
     * @param player The player to send the message to.
     * @param path The path to the message in messages.yml.
     */
    public static void sendRestrictionMessage(Player player, String path) {
        MessageType messageType = getRestrictionMessageType();

        switch (messageType) {
            case ACTIONBAR:
                sendMessage(player, getPrefix() + getShortMessage(path), MessageType.ACTIONBAR);
                break;
            case BOTH:
                sendMessage(player, getPrefixedMessage(path), MessageType.CHAT);
                sendMessage(player, getPrefix() + getShortMessage(path), MessageType.ACTIONBAR);
                break;
            default:
                sendPrefixedMessage(player, path, MessageType.CHAT);
                break;
        }
    }

    /**
     * Sends a formatted restriction message to a player using the configured restriction message type.
     * Automatically selects the short message variant for ActionBar delivery.
     *
     * @param player The player to send the message to.
     * @param path The path to the message in messages.yml.
     * @param replacements A map for placeholder replacements.
     */
    public static void sendFormattedRestrictionMessage(Player player, String path, Map<String, Object> replacements) {
        MessageType messageType = getRestrictionMessageType();

        switch (messageType) {
            case ACTIONBAR:
                sendMessage(player, getPrefix() + formatShort(path, replacements), MessageType.ACTIONBAR);
                break;
            case BOTH:
                sendPrefixedFormattedMessage(player, path, replacements, MessageType.CHAT);
                sendMessage(player, getPrefix() + formatShort(path, replacements), MessageType.ACTIONBAR);
                break;
            default:
                sendPrefixedFormattedMessage(player, path, replacements, MessageType.CHAT);
                break;
        }
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
            // Reserve space for "..."
            int truncateAt = ACTIONBAR_MAX_LENGTH - 3;
            String truncated = message;

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
     * Supports structured message nodes: looks for '.short' sub-key first,
     * then falls back to '.full', then to the raw path value.
     * Also supports legacy flat format for backward compatibility.
     *
     * @param path The base path to the message in messages.yml.
     * @return A shortened message suitable for ActionBar.
     */
    public static String getShortMessage(String path) {
        if (messagesConfig == null) {
            return get(path);
        }

        // Structured node: path is a section with 'full' and 'short' sub-keys
        if (messagesConfig.isConfigurationSection(path)) {
            String shortPath = path + ".short";
            if (messagesConfig.contains(shortPath)) {
                return ChatColor.translateAlternateColorCodes('&',
                        messagesConfig.getString(shortPath, ""));
            }
            // Fall back to '.full' within the section
            return get(path);
        }

        // Legacy flat format: check for path + ".short" as a sibling key
        String shortPath = path + ".short";
        if (messagesConfig.contains(shortPath) && messagesConfig.isString(shortPath)) {
            return ChatColor.translateAlternateColorCodes('&',
                    messagesConfig.getString(shortPath, ""));
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
