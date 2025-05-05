package me.koyere.lagxpert.tasks;

import me.koyere.lagxpert.LagXpert;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Periodically removes dropped items from the ground to reduce lag.
 * Excludes specific item types and supports per-world filtering.
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

    /**
     * Executes the cleanup task, removing dropped items not excluded by config.
     */
    @Override
    public void run() {
        int totalRemoved = 0;

        for (World world : Bukkit.getWorlds()) {
            if (!enabledWorlds.contains(world.getName())) continue;

            for (Item item : world.getEntitiesByClass(Item.class)) {
                ItemStack stack = item.getItemStack();
                if (stack == null || excludedItems.contains(stack.getType().name())) continue;

                item.remove();
                totalRemoved++;
            }
        }

        if (totalRemoved > 0) {
            Bukkit.broadcastMessage("§a[LagXpert] §fCleared §e" + totalRemoved + " §fground item(s).");
        }
    }

    /**
     * Schedules a warning broadcast message before the next cleanup.
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

        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.broadcastMessage(msg);
            }
        }.runTaskLater(LagXpert.getInstance(), delay);
    }
}
