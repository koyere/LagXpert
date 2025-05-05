package me.koyere.lagxpert.utils;

import me.koyere.lagxpert.LagXpert;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * MessageManager loads and provides access to all messages from messages.yml.
 * Supports simple placeholders like {entities}, {chunk}, etc.
 */
public class MessageManager {

    private static FileConfiguration messages;
    private static final Map<String, String> cache = new HashMap<>();

    /**
     * Loads messages.yml and caches its values.
     */
    public static void loadMessages() {
        File file = new File(LagXpert.getInstance().getDataFolder(), "messages.yml");
        if (!file.exists()) {
            LagXpert.getInstance().saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);

        // Preload all messages for performance
        messages.getKeys(true).forEach(key -> {
            if (messages.isString(key)) {
                cache.put(key, ChatColor.translateAlternateColorCodes('&', messages.getString(key)));
            }
        });
    }

    /**
     * Retrieves a message by key, already colorized.
     * @param key the dot-separated message key (e.g., chunkload.info)
     * @return the formatted message, or the key itself if not found
     */
    public static String get(String key) {
        return cache.getOrDefault(key, key);
    }

    /**
     * Retrieves the prefix for all plugin messages.
     * @return the prefix from config
     */
    public static String getPrefix() {
        return get("prefix");
    }

    /**
     * Retrieves a formatted message with placeholders replaced.
     * @param key message key
     * @param placeholders placeholder map: {placeholder} -> value
     * @return formatted message
     */
    public static String getFormatted(String key, Map<String, String> placeholders) {
        String msg = get(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return msg;
    }
}
