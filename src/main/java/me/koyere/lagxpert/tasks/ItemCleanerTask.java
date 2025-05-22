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
 */
public class ItemCleanerTask extends BukkitRunnable {

    // No instance fields for configuration; all settings are pulled from ConfigManager on demand.

    @Override
    public void run() {
        // This 'run' method is called periodically by the Bukkit scheduler.
        // It will handle the warning and then trigger the actual cleanup.

        // Use the master module toggle from config.yml
        if (!ConfigManager.isItemCleanerModuleEnabled()) { // CORRECTED: Was isItemCleanerEnabled
            return;
        }

        if (ConfigManager.isItemCleanerWarningEnabled()) {
            int warningSeconds = ConfigManager.getItemCleanerWarningTimeSeconds();
            String warningMessageTemplate = ConfigManager.getItemCleanerWarningMessage();

            String messageContent = warningMessageTemplate.replace("{seconds}", String.valueOf(warningSeconds));
            Bukkit.broadcastMessage(MessageManager.color(messageContent)); // Use MessageManager.color()

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
        List<String> enabledWorlds = ConfigManager.getItemCleanerEnabledWorlds(); // Fetches current config

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
            Bukkit.broadcastMessage(MessageManager.color(messageContent)); // Use MessageManager.color()
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
     * This method is static.
     *
     * @param world The world to clear items from.
     * @param actor The player who initiated the cleanup (for Abyss context), or null if automatic.
     * @return The number of items removed from this world.
     */
    private static int clearItemsFromSpecificWorld(World world, Player actor) {
        int itemsRemovedInWorld = 0;
        Set<String> excludedItemsUpper = ConfigManager.getItemCleanerExcludedItems().stream()
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        for (Item itemEntity : world.getEntitiesByClass(Item.class)) {
            ItemStack itemStack = itemEntity.getItemStack();

            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }

            if (excludedItemsUpper.contains(itemStack.getType().name().toUpperCase())) {
                continue;
            }

            // Check if Abyss is active before attempting to add items.
            // Abyss is active if itemCleanerModule is enabled AND abyss specific toggle in itemcleaner.yml is true
            if (ConfigManager.isAbyssEnabled()) {
                if (actor != null) {
                    AbyssManager.add(actor, itemStack);
                } else if (itemEntity.getThrower() != null) {
                    AbyssManager.add(itemEntity);
                }
            }

            itemEntity.remove();
            itemsRemovedInWorld++;
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