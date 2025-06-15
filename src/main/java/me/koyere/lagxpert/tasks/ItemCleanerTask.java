package me.koyere.lagxpert.tasks;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.system.AbyssManager;
import me.koyere.lagxpert.system.AbyssTracker; // For bStats
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Periodically removes dropped items from the ground to reduce lag.
 * Supports exclusions and per-world filtering by fetching configuration from ConfigManager.
 * Integrates with Abyss recovery system and handles its own warning cycle.
 * Fixed exclusion logic to properly handle excluded items.
 */
public class ItemCleanerTask extends BukkitRunnable {

    @Override
    public void run() {
        // This 'run' method is called periodically by the Bukkit scheduler.
        // It will handle the warning and then trigger the actual cleanup.

        // Use the master module toggle from config.yml
        if (!ConfigManager.isItemCleanerModuleEnabled()) {
            return;
        }

        if (ConfigManager.isItemCleanerWarningEnabled()) {
            int warningSeconds = ConfigManager.getItemCleanerWarningTimeSeconds();
            String warningMessageTemplate = ConfigManager.getItemCleanerWarningMessage();

            String messageContent = warningMessageTemplate.replace("{seconds}", String.valueOf(warningSeconds));
            Bukkit.broadcastMessage(MessageManager.color(messageContent));

            // Schedule the actual cleanup to run after the warning period.
            new BukkitRunnable() {
                @Override
                public void run() {
                    performCleanupForAllWorlds(null); // 'null' actor signifies automatic cleanup
                }
            }.runTaskLater(LagXpert.getInstance(), warningSeconds * 20L); // Convert seconds to ticks

        } else {
            // No warning configured, perform cleanup immediately.
            performCleanupForAllWorlds(null);
        }
    }

    /**
     * Performs the item cleanup process for all configured worlds.
     * This method is static and can be called by the scheduled task or manual commands.
     *
     * @param actor The player who initiated the cleanup, or null if automatic.
     * @return The total number of items removed.
     */
    private static int performCleanupForAllWorlds(Player actor) {
        int totalItemsRemoved = 0;
        List<String> enabledWorlds = ConfigManager.getItemCleanerEnabledWorlds();

        if (ConfigManager.isDebugEnabled() && actor == null) {
            LagXpert.getInstance().getLogger().info("[LagXpert] ItemCleanerTask: Starting automatic cleanup cycle.");
        }

        for (World world : Bukkit.getWorlds()) {
            boolean isWorldEnabled = enabledWorlds.stream().anyMatch(w -> w.equalsIgnoreCase("all") || w.equalsIgnoreCase(world.getName()));
            if (isWorldEnabled) {
                totalItemsRemoved += clearItemsFromSpecificWorld(world, actor);
            }
        }

        if (totalItemsRemoved > 0 && actor == null) { // Broadcast only for automatic cleanup
            String cleanedMessageTemplate = ConfigManager.getItemCleanerCleanedMessage();
            String messageContent = cleanedMessageTemplate.replace("{count}", String.valueOf(totalItemsRemoved));
            Bukkit.broadcastMessage(MessageManager.color(messageContent));
        }

        if (totalItemsRemoved > 0) {
            AbyssTracker.itemAddedToAbyss(totalItemsRemoved);
        }

        if (ConfigManager.isDebugEnabled() && actor == null && totalItemsRemoved > 0) {
            LagXpert.getInstance().getLogger().info("[LagXpert] ItemCleanerTask: Automatic cleanup finished. Removed " + totalItemsRemoved + " items.");
        }

        return totalItemsRemoved;
    }

    /**
     * Clears items from a specific world based on current configuration.
     * Fixed to properly handle excluded items.
     *
     * @param world The world to clear items from.
     * @param actor The player who initiated the cleanup (for Abyss context), or null if automatic.
     * @return The number of items removed from this world.
     */
    private static int clearItemsFromSpecificWorld(World world, Player actor) {
        int itemsRemovedInWorld = 0;

        // Get excluded items and convert to uppercase for case-insensitive comparison
        Set<String> excludedItemsUpper = ConfigManager.getItemCleanerExcludedItems().stream()
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info("[ItemCleanerTask] Excluded items: " + excludedItemsUpper);
        }

        for (Item itemEntity : world.getEntitiesByClass(Item.class)) {
            try {
                ItemStack itemStack = itemEntity.getItemStack();

                // Skip invalid items
                if (itemStack == null || itemStack.getType().isAir()) {
                    continue;
                }

                // Check if item is excluded - FIXED: Compare material name properly
                String materialName = itemStack.getType().name().toUpperCase();
                if (excludedItemsUpper.contains(materialName)) {
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().info("[ItemCleanerTask] Skipping excluded item: " + materialName);
                    }
                    continue;
                }

                // Skip items with custom names (often important)
                if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().info("[ItemCleanerTask] Skipping named item: " + itemStack.getItemMeta().getDisplayName());
                    }
                    continue;
                }

                // Skip items with custom lore (often important)
                if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasLore()) {
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().info("[ItemCleanerTask] Skipping item with lore: " + materialName);
                    }
                    continue;
                }

                // Skip enchanted items (often valuable)
                if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasEnchants()) {
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().info("[ItemCleanerTask] Skipping enchanted item: " + materialName);
                    }
                    continue;
                }

                // Check if Abyss is active before attempting to add items.
                if (ConfigManager.isAbyssEnabled()) {
                    if (actor != null) {
                        AbyssManager.add(actor, itemStack);
                    } else {
                        // For automatic cleanup, try to find the item thrower
                        try {
                            if (itemEntity.getThrower() != null) {
                                Player thrower = Bukkit.getPlayer(itemEntity.getThrower());
                                if (thrower != null) {
                                    AbyssManager.add(thrower, itemStack);
                                } else {
                                    // If thrower is offline, add to abyss with offline player info
                                    AbyssManager.add(itemEntity);
                                }
                            } else {
                                // No thrower info available, add to general abyss
                                AbyssManager.add(itemEntity);
                            }
                        } catch (Exception e) {
                            // If there's any issue with abyss, just continue with removal
                            if (ConfigManager.isDebugEnabled()) {
                                LagXpert.getInstance().getLogger().warning("[ItemCleanerTask] Failed to add item to abyss: " + e.getMessage());
                            }
                        }
                    }
                }

                // Remove the item
                itemEntity.remove();
                itemsRemovedInWorld++;

                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().info("[ItemCleanerTask] Removed item: " + materialName + " (x" + itemStack.getAmount() + ")");
                }

            } catch (Exception e) {
                // Log the error but continue with other items
                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().warning("[ItemCleanerTask] Error processing item entity: " + e.getMessage());
                }
            }
        }

        return itemsRemovedInWorld;
    }

    // --- Static utility methods for manual cleanup commands ---

    /**
     * Manually triggers item cleanup for all configured worlds.
     * Intended for use by commands (e.g., /clearitems all).
     *
     * @param actor The player executing the command, can be null if run from console.
     * @return The total number of items removed.
     */
    public static int runManualCleanupAllWorlds(Player actor) {
        String actorName = (actor == null) ? "CONSOLE" : actor.getName();
        LagXpert.getInstance().getLogger().info("[LagXpert] Manual cleanup of all worlds initiated by " + actorName);
        return performCleanupForAllWorlds(actor);
    }

    /**
     * Manually triggers item cleanup for a specific world.
     * Intended for use by commands (e.g., /clearitems <world_name>).
     *
     * @param actor The player executing the command, can be null if run from console.
     * @param world The specific world to clean items from.
     * @return The number of items removed from the specified world.
     */
    public static int runManualCleanupForWorld(Player actor, World world) {
        if (world == null) {
            return 0;
        }
        String actorName = (actor == null) ? "CONSOLE" : actor.getName();
        LagXpert.getInstance().getLogger().info("[LagXpert] Manual cleanup of world '" + world.getName() + "' initiated by " + actorName);

        int itemsRemoved = clearItemsFromSpecificWorld(world, actor);
        if (itemsRemoved > 0) {
            AbyssTracker.itemAddedToAbyss(itemsRemoved);
        }
        return itemsRemoved;
    }
}