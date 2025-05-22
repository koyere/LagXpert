package me.koyere.lagxpert;

import me.koyere.lagxpert.commands.*;
import me.koyere.lagxpert.listeners.*;
import me.koyere.lagxpert.metrics.MetricsHandler;
import me.koyere.lagxpert.system.AbyssManager;
import me.koyere.lagxpert.tasks.AutoChunkScanTask;
import me.koyere.lagxpert.tasks.ItemCleanerTask;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main class for the LagXpert plugin.
 * Handles the initialization and deinitialization of plugin components,
 * including configuration, commands, listeners, tasks, and metrics.
 */
public class LagXpert extends JavaPlugin {

    private static LagXpert instance;
    private static final int BSTATS_PLUGIN_ID = 25746; // bStats Plugin ID for metrics.
    private Metrics bStatsInstance; // Store the bStats Metrics instance

    /**
     * Returns the static instance of this plugin.
     * Provides a global access point to the plugin instance.
     *
     * @return The singleton instance of LagXpert.
     */
    public static LagXpert getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfigurations();
        loadPluginLogic();
        registerCommandsAndListeners();
        schedulePluginTasks();
        initializeMetrics(); // Initialize bStats and custom charts

        getLogger().info("LagXpert Free enabled successfully.");
    }

    @Override
    public void onDisable() {
        // Cancel all tasks registered by this plugin to prevent potential errors
        // or continued execution after the plugin is disabled.
        Bukkit.getScheduler().cancelTasks(this);

        getLogger().info("LagXpert Free disabled.");
    }

    /**
     * Saves all default configuration files from the JAR to the plugin's data folder
     * if they do not already exist. This ensures users have the default configs on first run.
     */
    private void saveDefaultConfigurations() {
        saveDefaultConfig(); // Saves config.yml
        saveResource("mobs.yml", false);
        saveResource("storage.yml", false);
        saveResource("redstone.yml", false);
        saveResource("alerts.yml", false);
        saveResource("task.yml", false);
        saveResource("messages.yml", false);
        saveResource("itemcleaner.yml", false);
    }

    /**
     * Loads essential plugin data and configurations into memory.
     * ConfigManager.loadAll() is responsible for loading all YAML files
     * and initializing other managers like MessageManager with their respective configs.
     */
    private void loadPluginLogic() {
        ConfigManager.loadAll();
        AbyssManager.loadConfig(); // AbyssManager fetches its config from ConfigManager values
    }

    /**
     * Registers all plugin commands and their executors/tab completers.
     * Also registers event listeners, conditionally based on module activation
     * status retrieved from ConfigManager.
     */
    private void registerCommandsAndListeners() {
        getCommand("lagxpert").setExecutor(new LagXpertCommand());
        getCommand("lagxpert").setTabCompleter(new LagXpertCommand());
        getCommand("chunkstatus").setExecutor(new ChunkStatusCommand());
        getCommand("abyss").setExecutor(new AbyssCommand());
        getCommand("clearitems").setExecutor(new ClearItemsCommand());
        getCommand("clearitems").setTabCompleter(new ClearItemsCommand()); // Assuming ClearItemsCommand also implements TabCompleter

        if (ConfigManager.isRedstoneControlModuleEnabled()) {
            getServer().getPluginManager().registerEvents(new RedstoneListener(), this);
        }
        if (ConfigManager.isStorageModuleEnabled()) {
            getServer().getPluginManager().registerEvents(new StorageListener(), this);
        }
        if (ConfigManager.isMobsModuleEnabled()) {
            getServer().getPluginManager().registerEvents(new EntityListener(), this);
        }
    }

    /**
     * Schedules all recurring tasks for the plugin.
     * Tasks like chunk scanning and item cleaning are started if enabled in the configuration.
     */
    private void schedulePluginTasks() {
        // Schedule automatic chunk scanning task
        if (ConfigManager.isAutoChunkScanModuleEnabled()) { // CORRECTED: Was isAutoChunkScanTaskModuleEnabled
            long scanInterval = ConfigManager.getScanIntervalTicks();
            new AutoChunkScanTask().runTaskTimer(this, 100L, scanInterval);
        }

        // Schedule automatic item cleaner task
        if (ConfigManager.isItemCleanerModuleEnabled()) { // CORRECTED: Was isItemCleanerEnabled
            int cleanerInterval = ConfigManager.getItemCleanerIntervalTicks();
            long initialDelay = ConfigManager.getItemCleanerInitialDelayTicks();
            new ItemCleanerTask().runTaskTimer(this, initialDelay, cleanerInterval);
        }
    }

    /**
     * Initializes bStats metrics collection for the plugin.
     * Creates the Metrics instance once and passes it to MetricsHandler
     * for the registration of custom charts.
     */
    private void initializeMetrics() {
        this.bStatsInstance = new Metrics(this, BSTATS_PLUGIN_ID);
        MetricsHandler.init(this.bStatsInstance);
    }
}