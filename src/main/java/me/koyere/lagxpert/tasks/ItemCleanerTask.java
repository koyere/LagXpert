package me.koyere.lagxpert.tasks;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.system.AbyssManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Periodically removes dropped items from the ground to reduce lag.
 * Supports exclusions and per-world filtering.
 * Integrates with Abyss recovery system.
 */
public class ItemCleanerTask extends BukkitRunnable {

    private final Set<String> enabledWorlds = new HashSet<>();
    private final Set<String> excludedItems = new HashSet<>();
    private final boolean broadcastWarning;
    private final int warningSeconds;
    private final String warningMessage;

    /**
     * Loads configuration from itemcleaner.yml
     */
    public ItemCleanerTask() {
        File file = new File(LagXpert.getInstance().getDataFolder(), "itemcleaner.yml");
        FileConfiguration config = LagXpert.loadYaml(file);

        enabledWorlds.addAll(config.getStringList("enabled-worlds"));
        excludedItems.addAll(config.getStringList("excluded-items"));

        broadcastWarning = config.getBoolean("warning.enabled", true);
        warningSeconds = config.getInt("warning.seconds-before", 10);
        warningMessage = config.getString("warning.message", "&e[LagXpert] &7Items will be cleared in &c{seconds}&7s.");
    }

    @Override
    public void run() {
        int totalRemoved = runCleanupInternal(null);
        if (totalRemoved > 0) {
            String msg = ChatColor.translateAlternateColorCodes('&',
                    "&a[LagXpert] &fCleared &e" + totalRemoved + " &fground item(s).");
            Bukkit.broadcastMessage(msg);
        }
    }

    private int runCleanupInternal(Player actor) {
        int totalRemoved = 0;

        for (World world : Bukkit.getWorlds()) {
            if (!enabledWorlds.contains(world.getName())) continue;
            totalRemoved += clearItemsFromWorld(world, actor);
        }

        return totalRemoved;
    }

    private int clearItemsFromWorld(World world, Player actor) {
        int removed = 0;

        for (Item item : world.getEntitiesByClass(Item.class)) {
            ItemStack stack = item.getItemStack();
            if (stack == null || excludedItems.contains(stack.getType().name())) continue;

            if (actor != null) {
                AbyssManager.add(actor, stack);
            } else if (item.getThrower() != null) {
                AbyssManager.add(item);
            }

            item.remove();
            removed++;
        }

        return removed;
    }

    /**
     * Triggers cleanup manually with player context (for /clearitems).
     */
    public static int runManualCleanup(Player actor) {
        ItemCleanerTask task = new ItemCleanerTask();
        return task.runCleanupInternal(actor);
    }

    /**
     * 🔹 Public method to trigger cleanup of a specific world.
     */
    public static int runWorldCleanup(World world) {
        if (world == null) return 0;
        ItemCleanerTask task = new ItemCleanerTask();
        return task.clearItemsFromWorld(world, null);
    }

    /**
     * Schedules a broadcast warning before cleanup.
     */
    public static void scheduleWarning() {
        File file = new File(LagXpert.getInstance().getDataFolder(), "itemcleaner.yml");
        FileConfiguration config = LagXpert.loadYaml(file);

        if (!config.getBoolean("enabled", true) || !config.getBoolean("warning.enabled", true)) return;

        int intervalTicks = config.getInt("interval-ticks", 6000);
        int secondsBefore = config.getInt("warning.seconds-before", 10);
        long delay = intervalTicks - (secondsBefore * 20L);

        String msg = config.getString("warning.message", "&e[LagXpert] &7Items will be cleared in &c{seconds}&7s.")
                .replace("{seconds}", String.valueOf(secondsBefore));
        String coloredMsg = ChatColor.translateAlternateColorCodes('&', msg);

        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.broadcastMessage(coloredMsg);
            }
        }.runTaskLater(LagXpert.getInstance(), delay);
    }
}
