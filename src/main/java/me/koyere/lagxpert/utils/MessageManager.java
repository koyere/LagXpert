package me.koyere.lagxpert.utils;

import me.koyere.lagxpert.LagXpert;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Handles all plugin messages loaded from messages.yml.
 * Provides support for prefixing and custom formatting.
 */
public class MessageManager {

    private static FileConfiguration messages;
    private static String prefix = "&7[&bLagXpert&7] ";

    /**
     * Loads messages from messages.yml and stores the prefix.
     */
    public static void loadMessages() {
        File file = new File(LagXpert.getInstance().getDataFolder(), "messages.yml");
        messages = YamlConfiguration.loadConfiguration(file);

        prefix = messages.getString("prefix", "&7[&bLagXpert&7] ");
    }

    /**
     * Returns the translated prefix.
     *
     * @return colored prefix string
     */
    public static String getPrefix() {
        return ChatColor.translateAlternateColorCodes('&', prefix);
    }

    /**
     * Gets a message by key and applies color formatting.
     *
     * @param path the YAML path in messages.yml
     * @return colored string
     */
    public static String get(String path) {
        String raw = messages.getString(path, "&c[Missing message: " + path + "]");
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    /**
     * Applies color codes (&) to a string.
     *
     * @param input the raw message
     * @return colored message
     */
    public static String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
