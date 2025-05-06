package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Stores recently removed items in per-player YAML files
 * so players can recover them using /abyss.
 */
public class AbyssManager {

    private static boolean enabled;
    private static int retentionSeconds;
    private static int maxItemsPerPlayer;
    private static String recoverMessage;
    private static String emptyMessage;

    // ✅ Define the abyss folder under data/abyss for persistence
    private static final File abyssFolder = new File(LagXpert.getInstance().getDataFolder(), "data/abyss");

    /**
     * Loads abyss config from itemcleaner.yml
     */
    public static void loadConfig() {
        FileConfiguration config = LagXpert.loadYaml(new File(LagXpert.getInstance().getDataFolder(), "itemcleaner.yml"));

        enabled = config.getBoolean("abyss.enabled", true);
        retentionSeconds = config.getInt("abyss.retention-seconds", 120);
        maxItemsPerPlayer = config.getInt("abyss.max-items-per-player", 30);
        recoverMessage = config.getString("abyss.recover-message", "&aYou recovered &f{count} &aitem(s) from the abyss.");
        emptyMessage = config.getString("abyss.empty-message", "&7You have no items to recover.");

        // ✅ Ensure the abyss folder exists (plugins/LagXpert/data/abyss)
        if (!abyssFolder.exists()) {
            abyssFolder.mkdirs();
        }
    }

    /**
     * Adds a removed item to the abyss storage for a player (manual /clearitems).
     */
    public static void add(Player player, ItemStack item) {
        if (!enabled || player == null || item == null) return;
        saveToFile(player.getUniqueId(), item);
    }

    /**
     * Adds a removed item using the thrower's UUID (automatic).
     */
    public static void add(Item item) {
        if (!enabled || item.getItemStack() == null) return;

        UUID uuid = item.getThrower();
        if (uuid != null) {
            saveToFile(uuid, item.getItemStack());
        }
    }

    /**
     * Saves an item to the player-specific abyss YAML file.
     */
    private static void saveToFile(UUID uuid, ItemStack stack) {
        File file = new File(abyssFolder, uuid.toString() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        List<Map<String, Object>> items = (List<Map<String, Object>>) config.getList("items");
        if (items == null) items = new ArrayList<>();

        if (items.size() >= maxItemsPerPlayer) {
            items.remove(0);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", System.currentTimeMillis());
        data.put("item", stack.serialize());

        items.add(data);
        config.set("items", items);

        try {
            config.save(file);
        } catch (IOException e) {
            Bukkit.getLogger().warning("[LagXpert] Failed to save abyss data for " + uuid);
        }
    }

    /**
     * Attempts to recover items from the abyss file for a player.
     */
    public static void tryRecover(Player player) {
        if (!enabled) return;

        File file = new File(abyssFolder, player.getUniqueId().toString() + ".yml");
        if (!file.exists()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', emptyMessage));
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<?> rawList = config.getList("items");
        if (rawList == null || rawList.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', emptyMessage));
            return;
        }

        long now = System.currentTimeMillis();
        int recovered = 0;
        List<Map<String, Object>> kept = new ArrayList<>();

        for (Object obj : rawList) {
            if (!(obj instanceof Map<?, ?> map)) continue;

            Object timeObj = map.get("timestamp");
            if (!(timeObj instanceof Number)) continue;

            long timestamp = ((Number) timeObj).longValue();
            if ((now - timestamp) > (retentionSeconds * 1000L)) {
                continue; // Skip expired
            }

            Object itemData = map.get("item");
            if (itemData instanceof Map<?, ?> dataMap) {
                try {
                    ItemStack stack = ItemStack.deserialize((Map<String, Object>) dataMap);
                    player.getInventory().addItem(stack);
                    recovered++;
                } catch (Exception ignored) {}
            } else {
                kept.add((Map<String, Object>) map); // keep if valid structure but couldn't deserialize
            }
        }

        if (recovered > 0) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    recoverMessage.replace("{count}", String.valueOf(recovered))));
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', emptyMessage));
        }

        // Save only non-expired or invalid items
        config.set("items", kept);
        try {
            config.save(file);
        } catch (IOException e) {
            Bukkit.getLogger().warning("[LagXpert] Failed to update abyss file for " + player.getName());
        }
    }
}
