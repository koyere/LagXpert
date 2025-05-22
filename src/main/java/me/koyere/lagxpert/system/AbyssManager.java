package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager; // Import ConfigManager
import me.koyere.lagxpert.utils.MessageManager; // Assuming MessageManager for consistency (optional change)
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages the "Abyss" system, storing recently removed items in per-player YAML files
 * allowing players to recover them using the /abyss command.
 * Item data is persisted in plugins/LagXpert/data/abyss/.
 */
public class AbyssManager {

    private static boolean enabled;
    private static int retentionSeconds;
    private static int maxItemsPerPlayer;
    private static String recoverMessage;
    private static String emptyMessage;
    private static String recoverFailFullInvMessage; // Optional: new message

    // Folder for storing abyss data files (plugins/LagXpert/data/abyss/)
    private static File abyssFolder;

    /**
     * Loads abyss configuration settings from ConfigManager (which reads from itemcleaner.yml).
     * Initializes the abyss data storage folder.
     */
    public static void loadConfig() {
        // Retrieve settings from ConfigManager
        enabled = ConfigManager.isAbyssEnabled();
        retentionSeconds = ConfigManager.getAbyssRetentionSeconds();
        maxItemsPerPlayer = ConfigManager.getAbyssMaxItemsPerPlayer();

        // For messages, you could use MessageManager for consistency if it handles these too
        // Or continue loading them directly if they are simple and don't need complex formatting
        recoverMessage = ConfigManager.getAbyssRecoverMessage();
        emptyMessage = ConfigManager.getAbyssEmptyMessage();
        recoverFailFullInvMessage = ConfigManager.getAbyssRecoverFailFullInvMessage(); // Assumes this new key exists in config via ConfigManager

        // Initialize the abyss data folder path
        abyssFolder = new File(LagXpert.getInstance().getDataFolder(), "data" + File.separator + "abyss");

        // Ensure the abyss data folder exists
        if (!abyssFolder.exists()) {
            if (!abyssFolder.mkdirs()) {
                LagXpert.getInstance().getLogger().severe("[LagXpert] Could not create abyss data folder: " + abyssFolder.getPath());
                enabled = false; // Disable abyss if folder creation fails
                return;
            }
        }
        LagXpert.getInstance().getLogger().info("[LagXpert] Abyss system initialized. Data folder: " + abyssFolder.getPath());
    }

    /**
     * Adds an item to the player's abyss storage.
     * Typically called when items are manually cleared via commands.
     *
     * @param player The player to whom the item belongs.
     * @param item   The ItemStack to add to the abyss.
     */
    public static void add(Player player, ItemStack item) {
        if (!enabled || player == null || item == null || item.getType().isAir()) {
            return;
        }
        // Consider making saveToFile asynchronous if performance becomes an issue.
        saveToFile(player.getUniqueId(), item.clone()); // Clone to avoid issues with mutable ItemStacks
    }

    /**
     * Adds an item entity (e.g., dropped item) to the abyss storage,
     * associating it with the item's thrower, if available.
     * Typically called by automated item cleaning processes.
     *
     * @param itemEntity The Item entity to process.
     */
    public static void add(Item itemEntity) {
        if (!enabled || itemEntity == null || itemEntity.getItemStack() == null || itemEntity.getItemStack().getType().isAir()) {
            return;
        }

        UUID throwerUUID = itemEntity.getThrower();
        if (throwerUUID != null) {
            // Consider making saveToFile asynchronous.
            saveToFile(throwerUUID, itemEntity.getItemStack().clone()); // Clone item stack
        }
    }

    /**
     * Saves an ItemStack to the specified player's abyss YAML file.
     * This method performs synchronous file I/O.
     *
     * @param uuid  The UUID of the player.
     * @param stack The ItemStack to save.
     */
    private static void saveToFile(UUID uuid, ItemStack stack) {
        File playerFile = new File(abyssFolder, uuid.toString() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

        List<Map<String, Object>> itemsList = (List<Map<String, Object>>) config.getList("items");
        if (itemsList == null) {
            itemsList = new ArrayList<>();
        }

        // Remove oldest items if the list exceeds the maximum allowed size
        while (itemsList.size() >= maxItemsPerPlayer && !itemsList.isEmpty()) {
            itemsList.remove(0); // FIFO: Remove the oldest item
        }

        Map<String, Object> itemData = new HashMap<>();
        itemData.put("timestamp", System.currentTimeMillis());
        itemData.put("item", stack.serialize()); // ItemStack serialization

        itemsList.add(itemData);
        config.set("items", itemsList);

        try {
            config.save(playerFile);
        } catch (IOException e) {
            LagXpert.getInstance().getLogger().warning("[LagXpert] Failed to save abyss data for UUID " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * Allows a player to attempt recovery of their items from the abyss.
     * Items are checked for expiry, and deserialized. Recovered items are given
     * to the player. The abyss file is updated accordingly.
     * This method performs synchronous file I/O for reading and writing.
     * Player interactions (giving items, messages) are on the main thread.
     *
     * @param player The player attempting to recover items.
     */
    public static void tryRecover(Player player) {
        if (!enabled) {
            // This case should ideally be handled by the command itself,
            // but double-check here.
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[LagXpert] The Abyss system is currently disabled."));
            return;
        }

        File playerFile = new File(abyssFolder, player.getUniqueId().toString() + ".yml");
        if (!playerFile.exists()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', emptyMessage));
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
        List<?> rawListUntyped = config.getList("items");

        if (rawListUntyped == null || rawListUntyped.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', emptyMessage));
            return;
        }

        List<Map<?, ?>> rawItemList = new ArrayList<>();
        for (Object obj : rawListUntyped) {
            if (obj instanceof Map) {
                rawItemList.add((Map<?, ?>) obj);
            }
        }

        if (rawItemList.isEmpty()) { // If all entries were malformed and not maps
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', emptyMessage));
            return;
        }

        long currentTimeMillis = System.currentTimeMillis();
        int itemsSuccessfullyGiven = 0;
        boolean partialRecoveryDueToFullInv = false;

        List<ItemStack> itemsToGivePlayer = new ArrayList<>();
        List<Map<?, ?>> itemsToKeepInAbyssFile = new ArrayList<>(); // For unexpired items that couldn't be recovered or weren't processed

        for (Map<?, ?> itemEntryMap : rawItemList) {
            Object timestampObj = itemEntryMap.get("timestamp");
            Object itemDataObj = itemEntryMap.get("item");

            // Basic validation of the entry structure
            if (!(timestampObj instanceof Number) || !(itemDataObj instanceof Map)) {
                LagXpert.getInstance().getLogger().warning("[LagXpert] Abyss item for " + player.getName() +
                        " has a malformed entry. Timestamp: " + timestampObj + ", ItemData: " + itemDataObj);
                // Decide if malformed entries should be kept or discarded.
                // For now, let's discard them to prevent repeated errors.
                // If you want to keep them, add to itemsToKeepInAbyssFile if not expired (though timestamp might be bad).
                continue;
            }

            long itemTimestamp = ((Number) timestampObj).longValue();

            // Check for item expiry
            if ((currentTimeMillis - itemTimestamp) > (retentionSeconds * 1000L)) {
                // Item is expired, do not keep it in the file and do not attempt recovery.
                continue;
            }

            // Item is not expired, attempt to deserialize it.
            try {
                ItemStack deserializedStack = ItemStack.deserialize((Map<String, Object>) itemDataObj);
                if (deserializedStack != null && !deserializedStack.getType().isAir()) {
                    itemsToGivePlayer.add(deserializedStack);
                } else {
                    // Deserialized to air or null, effectively an invalid item.
                    LagXpert.getInstance().getLogger().warning("[LagXpert] Abyss item for " + player.getName() +
                            " deserialized to null or AIR. Discarding.");
                }
            } catch (Exception e) {
                LagXpert.getInstance().getLogger().warning("[LagXpert] Failed to deserialize an abyss item for " +
                        player.getName() + ". Error: " + e.getMessage() + ". This item will be kept if not expired.");
                // Deserialization failed, but the item is not expired. Keep it in the abyss file.
                itemsToKeepInAbyssFile.add(itemEntryMap);
            }
        }

        // Attempt to give the deserialized, non-expired items to the player.
        if (!itemsToGivePlayer.isEmpty()) {
            for (ItemStack stackToGive : itemsToGivePlayer) {
                HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(stackToGive);
                if (leftovers.isEmpty()) {
                    itemsSuccessfullyGiven++;
                } else {
                    // Inventory was full for this item.
                    partialRecoveryDueToFullInv = true;
                    // The `leftovers` are items that couldn't be added. Bukkit drops them by default.
                    // We are considering the attempt to give as "recovered" from abyss for this slot.
                    // For a more complex system, one might re-add leftovers to itemsToKeepInAbyssFile.
                    // For simplicity here, we assume player needs to manage their inventory.
                    // We count it as "given" if any part of the stack was taken, or if an attempt was made.
                    if (stackToGive.getAmount() > leftovers.values().stream().mapToInt(ItemStack::getAmount).sum()){
                        itemsSuccessfullyGiven++; // At least some part of the stack was given
                    }
                }
            }
        }

        // Send appropriate messages to the player.
        if (itemsSuccessfullyGiven > 0) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    recoverMessage.replace("{count}", String.valueOf(itemsSuccessfullyGiven))));
            if (partialRecoveryDueToFullInv) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', recoverFailFullInvMessage));
            }
        } else {
            // This message now also appropriately covers cases where items existed but were all expired,
            // or couldn't be deserialized and kept, or no valid items were found.
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', emptyMessage));
        }

        // Update the abyss file: save only items that were kept.
        if (itemsToKeepInAbyssFile.isEmpty() && rawItemList.stream().allMatch(map -> itemsToGivePlayer.stream().anyMatch(item -> map.get("item").equals(item.serialize())) || (currentTimeMillis - ((Number)map.get("timestamp")).longValue()) > (retentionSeconds * 1000L) )) {
            // If all original items were either successfully processed for recovery or expired,
            // and the "keep" list is empty, the file can be deleted.
            if (!rawItemList.isEmpty() && itemsToKeepInAbyssFile.isEmpty()) { // Check if original list was not empty
                if (playerFile.delete()) {
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().info("[LagXpert] Abyss file for " + player.getName() + " was cleared and deleted.");
                    }
                } else {
                    LagXpert.getInstance().getLogger().warning("[LagXpert] Could not delete empty abyss file for " + player.getName());
                    config.set("items", null); // Fallback to clearing the list if delete fails
                    trySaveConfig(config, playerFile, player.getName());
                }
            } else { // Original list was empty, or itemsToKeepInAbyssFile is not empty (e.g. file was empty to begin with)
                config.set("items", itemsToKeepInAbyssFile); // Ensure list is explicitly set (even if empty)
                trySaveConfig(config, playerFile, player.getName());
            }
        } else {
            config.set("items", itemsToKeepInAbyssFile);
            trySaveConfig(config, playerFile, player.getName());
        }
    }

    /**
     * Helper method to save YamlConfiguration with error handling.
     */
    private static void trySaveConfig(YamlConfiguration config, File file, String playerName) {
        try {
            config.save(file);
        } catch (IOException e) {
            LagXpert.getInstance().getLogger().warning("[LagXpert] Failed to update abyss file for " + playerName + ": " + e.getMessage());
        }
    }
}