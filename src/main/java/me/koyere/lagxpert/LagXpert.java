package me.koyere.lagxpert;

import me.koyere.lagxpert.commands.*;
import me.koyere.lagxpert.listeners.*;
import me.koyere.lagxpert.tasks.AutoChunkScanTask;
import me.koyere.lagxpert.tasks.ItemCleanerTask;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Main class for the LagXpert plugin.
 * Handles registration of commands, listeners, configuration, metrics, and scheduled tasks.
 */
public class LagXpert extends JavaPlugin {

    private static LagXpert instance;

    /**
     * Returns the static instance of this plugin.
     *
     * @return plugin instance
     */
    public static LagXpert getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Save default config and modular resources if missing
        saveDefaultConfig();
        saveResource("mobs.yml", false);
        saveResource("storage.yml", false);
        saveResource("redstone.yml", false);
        saveResource("alerts.yml", false);
        saveResource("task.yml", false);
        saveResource("messages.yml", false);
        saveResource("itemcleaner.yml", false);

        // Load all configuration and language files
        ConfigManager.loadAll();
        MessageManager.loadMessages();

        // Register base commands
        getCommand("lagxpert").setExecutor(new LagXpertCommand());
        getCommand("lagxpert").setTabCompleter(new LagXpertCommand());
        getCommand("chunkstatus").setExecutor(new ChunkStatusCommand());
        getCommand("abyss").setExecutor(new AbyssCommand());

        // Register module-specific listeners if enabled in config
        if (ConfigManager.isRedstoneControlEnabled()) {
            getServer().getPluginManager().registerEvents(new RedstoneListener(), this);
        }
        if (ConfigManager.isStorageModuleEnabled()) {
            getServer().getPluginManager().registerEvents(new StorageListener(), this);
        }
        if (ConfigManager.isMobsModuleEnabled()) {
            getServer().getPluginManager().registerEvents(new EntityListener(), this);
        }

        // Start automatic chunk scanning task if enabled
        if (ConfigManager.isTaskModuleEnabled()) {
            long interval = ConfigManager.getScanIntervalTicks();
            new AutoChunkScanTask().runTaskTimer(this, 100L, interval); // ✅ Usar BukkitRunnable correctamente
        }

        // Start item cleaner task if enabled
        File itemCleanerFile = new File(getDataFolder(), "itemcleaner.yml");
        FileConfiguration itemCleanerConfig = loadYaml(itemCleanerFile);

        if (itemCleanerConfig.getBoolean("enabled", true)) {
            int ticks = itemCleanerConfig.getInt("interval-ticks", 6000);
            if (itemCleanerConfig.getBoolean("warning.enabled", true)) {
                ItemCleanerTask.scheduleWarning();
            }
            new ItemCleanerTask().runTaskTimer(this, ticks, ticks);
        }

        // Initialize bStats metrics
        int pluginId = 25746;
        new Metrics(this, pluginId);

        getLogger().info("LagXpert Free enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("LagXpert Free disabled.");
    }

    /**
     * Utility method to load a YAML file from disk.
     *
     * @param file YAML file to load
     * @return parsed FileConfiguration
     */
    public static FileConfiguration loadYaml(File file) {
        return YamlConfiguration.loadConfiguration(file);
    }
}
