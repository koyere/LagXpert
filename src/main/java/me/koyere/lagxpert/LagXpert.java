package me.koyere.lagxpert;

import me.koyere.lagxpert.commands.*;
import me.koyere.lagxpert.listeners.*;
import me.koyere.lagxpert.metrics.MetricsHandler;
import me.koyere.lagxpert.monitoring.PerformanceTracker;
import me.koyere.lagxpert.monitoring.TPSMonitor;
import me.koyere.lagxpert.system.AbyssManager;
import me.koyere.lagxpert.system.RedstoneCircuitTracker;
import me.koyere.lagxpert.tasks.AsyncChunkAnalyzer;
import me.koyere.lagxpert.tasks.AutoChunkScanTask;
import me.koyere.lagxpert.tasks.EntityCleanupTask;
import me.koyere.lagxpert.tasks.ItemCleanerTask;
import me.koyere.lagxpert.utils.ChunkUtils;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main class for the LagXpert plugin.
 * Handles the initialization and deinitialization of plugin components,
 * including configuration, commands, listeners, tasks, metrics, and Phase 1 & 2 features.
 * Enhanced with performance cache system, async processing, advanced redstone control,
 * entity cleanup functionality, and comprehensive TPS monitoring system.
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
        initializeAdvancedSystems(); // Phase 1 systems
        initializeMonitoringSystems(); // Phase 2 systems
        initializeMetrics(); // Initialize bStats and custom charts

        getLogger().info("LagXpert Free v" + getDescription().getVersion() + " enabled successfully with Phase 1 & 2 optimizations.");
    }

    @Override
    public void onDisable() {
        // Shutdown Phase 2 systems gracefully
        shutdownMonitoringSystems();

        // Shutdown Phase 1 systems gracefully
        shutdownAdvancedSystems();

        // Cancel all tasks registered by this plugin to prevent potential errors
        // or continued execution after the plugin is disabled.
        Bukkit.getScheduler().cancelTasks(this);

        getLogger().info("LagXpert Free disabled.");
    }

    /**
     * Saves all default configuration files from the JAR to the plugin's data folder
     * if they do not already exist. This ensures users have the default configs on first run.
     * Enhanced to include Phase 1 & 2 configuration files.
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
        saveResource("entitycleanup.yml", false); // Phase 1 entity cleanup config
        saveResource("monitoring.yml", false); // Phase 2 monitoring config
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

        // Phase 2: Register TPS command
        if (ConfigManager.isMonitoringModuleEnabled()) {
            getCommand("tps").setExecutor(new TPSCommand());
            getCommand("tps").setTabCompleter(new TPSCommand());
        }

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
     * Tasks like chunk scanning, item cleaning, entity cleanup, and monitoring are started if enabled in the configuration.
     * Enhanced with Phase 1 & 2 tasks.
     */
    private void schedulePluginTasks() {
        // Schedule automatic chunk scanning task
        if (ConfigManager.isAutoChunkScanModuleEnabled()) {
            long scanInterval = ConfigManager.getScanIntervalTicks();
            new AutoChunkScanTask().runTaskTimer(this, 100L, scanInterval);

            if (ConfigManager.isDebugEnabled()) {
                getLogger().info("[LagXpert] AutoChunkScanTask scheduled with interval: " + scanInterval + " ticks");
            }
        }

        // Schedule automatic item cleaner task
        if (ConfigManager.isItemCleanerModuleEnabled()) {
            int cleanerInterval = ConfigManager.getItemCleanerIntervalTicks();
            long initialDelay = ConfigManager.getItemCleanerInitialDelayTicks();
            new ItemCleanerTask().runTaskTimer(this, initialDelay, cleanerInterval);

            if (ConfigManager.isDebugEnabled()) {
                getLogger().info("[LagXpert] ItemCleanerTask scheduled with interval: " + cleanerInterval + " ticks");
            }
        }

        // Schedule automatic entity cleanup task (Phase 1)
        if (ConfigManager.isEntityCleanupModuleEnabled()) {
            int entityCleanupInterval = ConfigManager.getEntityCleanupIntervalTicks();
            long entityCleanupInitialDelay = ConfigManager.getEntityCleanupInitialDelayTicks();
            new EntityCleanupTask().runTaskTimer(this, entityCleanupInitialDelay, entityCleanupInterval);

            if (ConfigManager.isDebugEnabled()) {
                getLogger().info("[LagXpert] EntityCleanupTask scheduled with interval: " + entityCleanupInterval + " ticks");
            }
        }
    }

    /**
     * Initializes advanced Phase 1 systems for performance optimization.
     * Includes cache system, async processing, and redstone circuit tracking.
     */
    private void initializeAdvancedSystems() {
        try {
            // Initialize redstone circuit tracking system
            if (ConfigManager.isRedstoneControlModuleEnabled()) {
                RedstoneCircuitTracker.startCleanupTask();
                getLogger().info("[LagXpert] Advanced redstone circuit tracking system initialized.");
            }

            // Initialize chunk data cache system (always initialize for performance)
            // Cache system is passive and doesn't need explicit initialization
            getLogger().info("[LagXpert] Performance cache system initialized.");

            // Log async chunk analyzer statistics
            if (ConfigManager.isDebugEnabled()) {
                getLogger().info("[LagXpert] Async chunk analyzer initialized with " +
                        Runtime.getRuntime().availableProcessors() / 2 + " threads.");
            }

        } catch (Exception e) {
            getLogger().severe("[LagXpert] Failed to initialize advanced systems: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Initializes Phase 2 monitoring systems for TPS, memory, and performance tracking.
     * NEW: Comprehensive monitoring with real-time alerts and analytics.
     */
    private void initializeMonitoringSystems() {
        if (!ConfigManager.isMonitoringModuleEnabled()) {
            if (ConfigManager.isDebugEnabled()) {
                getLogger().info("[LagXpert] Monitoring module is disabled, skipping monitoring system initialization.");
            }
            return;
        }

        try {
            // Initialize TPS monitoring system
            if (ConfigManager.isTPSMonitoringEnabled()) {
                TPSMonitor.startMonitoring();
                getLogger().info("[LagXpert] TPS monitoring system initialized.");
            }

            // Initialize performance tracking system
            PerformanceTracker.startTracking();
            getLogger().info("[LagXpert] Performance tracking system initialized.");

            if (ConfigManager.isDebugEnabled()) {
                getLogger().info("[LagXpert] Monitoring systems initialized successfully:");
                getLogger().info("  - TPS Monitoring: " + (ConfigManager.isTPSMonitoringEnabled() ? "Enabled" : "Disabled"));
                getLogger().info("  - Memory Monitoring: " + (ConfigManager.isMemoryMonitoringEnabled() ? "Enabled" : "Disabled"));
                getLogger().info("  - Chunk Monitoring: " + (ConfigManager.isChunkMonitoringEnabled() ? "Enabled" : "Disabled"));
                getLogger().info("  - Lag Detection: " + (ConfigManager.isLagDetectionEnabled() ? "Enabled" : "Disabled"));
            }

        } catch (Exception e) {
            getLogger().severe("[LagXpert] Failed to initialize monitoring systems: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gracefully shuts down Phase 2 monitoring systems.
     * NEW: Ensures proper cleanup of monitoring threads and data.
     */
    private void shutdownMonitoringSystems() {
        try {
            // Stop TPS monitoring
            TPSMonitor.stopMonitoring();
            getLogger().info("[LagXpert] TPS monitoring system shutdown completed.");

            // Stop performance tracking
            PerformanceTracker.stopTracking();
            getLogger().info("[LagXpert] Performance tracking system shutdown completed.");

        } catch (Exception e) {
            getLogger().warning("[LagXpert] Error during monitoring systems shutdown: " + e.getMessage());
        }
    }

    /**
     * Gracefully shuts down Phase 1 advanced systems.
     * Ensures proper cleanup of cache, async tasks, and circuit tracking.
     */
    private void shutdownAdvancedSystems() {
        try {
            // Shutdown async chunk analyzer
            AsyncChunkAnalyzer.shutdown();
            getLogger().info("[LagXpert] Async chunk analyzer shutdown completed.");

            // Clear cache systems
            ChunkUtils.clearAllCache();
            getLogger().info("[LagXpert] Performance cache cleared.");

            // Clear redstone circuit tracking data
            RedstoneCircuitTracker.clearAll();
            getLogger().info("[LagXpert] Redstone circuit tracking data cleared.");

        } catch (Exception e) {
            getLogger().warning("[LagXpert] Error during advanced systems shutdown: " + e.getMessage());
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

        if (ConfigManager.isDebugEnabled()) {
            getLogger().info("[LagXpert] bStats metrics initialized with plugin ID: " + BSTATS_PLUGIN_ID);
        }
    }

    /**
     * Gets performance statistics from all Phase 1 & 2 systems.
     * Enhanced to include monitoring system statistics.
     *
     * @return Map containing performance statistics from cache, async, circuit, and monitoring systems
     */
    public static java.util.Map<String, Object> getPerformanceStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();

        try {
            // Phase 1 statistics
            stats.put("cache", ChunkUtils.getCacheStatistics());
            stats.put("async_analyzer", AsyncChunkAnalyzer.getStatistics());

            if (ConfigManager.isRedstoneControlModuleEnabled()) {
                stats.put("redstone_circuits", RedstoneCircuitTracker.getStatistics());
            }

            if (ConfigManager.isEntityCleanupModuleEnabled()) {
                stats.put("entity_cleanup", EntityCleanupTask.getStatistics());
            }

            // Phase 2 statistics
            if (ConfigManager.isMonitoringModuleEnabled()) {
                stats.put("performance_tracking", PerformanceTracker.getPerformanceStatistics());

                // TPS statistics
                java.util.Map<String, Object> tpsStats = new java.util.HashMap<>();
                tpsStats.put("current_tps", TPSMonitor.getCurrentTPS());
                tpsStats.put("short_term_tps", TPSMonitor.getShortTermTPS());
                tpsStats.put("medium_term_tps", TPSMonitor.getMediumTermTPS());
                tpsStats.put("long_term_tps", TPSMonitor.getLongTermTPS());
                tpsStats.put("average_tick_time", TPSMonitor.getAverageTickTime());
                tpsStats.put("max_tick_time", TPSMonitor.getMaxTickTime());
                tpsStats.put("total_ticks", TPSMonitor.getTotalTicks());
                tpsStats.put("recent_lag_spikes", TPSMonitor.getRecentLagSpikes().size());
                stats.put("tps_monitoring", tpsStats);
            }

        } catch (Exception e) {
            if (getInstance() != null) {
                getInstance().getLogger().warning("[LagXpert] Error collecting performance statistics: " + e.getMessage());
            }
        }

        return stats;
    }

    /**
     * Resets all performance statistics counters.
     * Enhanced to include monitoring system statistics.
     */
    public static void resetPerformanceStatistics() {
        try {
            // Reset Phase 1 statistics
            AsyncChunkAnalyzer.resetStatistics();
            EntityCleanupTask.resetStatistics();

            // Reset Phase 2 statistics
            if (ConfigManager.isMonitoringModuleEnabled()) {
                PerformanceTracker.resetStatistics();
                TPSMonitor.resetStatistics();
            }

            if (getInstance() != null && ConfigManager.isDebugEnabled()) {
                getInstance().getLogger().info("[LagXpert] Performance statistics reset.");
            }
        } catch (Exception e) {
            if (getInstance() != null) {
                getInstance().getLogger().warning("[LagXpert] Error resetting performance statistics: " + e.getMessage());
            }
        }
    }

    /**
     * Forces cleanup of all cache systems.
     * Useful for troubleshooting and memory management.
     */
    public static void forceCacheCleanup() {
        try {
            ChunkUtils.clearAllCache();

            if (getInstance() != null && ConfigManager.isDebugEnabled()) {
                getInstance().getLogger().info("[LagXpert] Cache force cleanup completed.");
            }
        } catch (Exception e) {
            if (getInstance() != null) {
                getInstance().getLogger().warning("[LagXpert] Error during cache force cleanup: " + e.getMessage());
            }
        }
    }

    /**
     * Gets a comprehensive server performance report.
     * NEW: Provides formatted performance summary for commands and APIs.
     *
     * @return Formatted string with current server performance data
     */
    public static String getPerformanceReport() {
        if (!ConfigManager.isMonitoringModuleEnabled()) {
            return "Monitoring module is disabled";
        }

        try {
            StringBuilder report = new StringBuilder();
            report.append("=== LagXpert Performance Report ===\n");
            report.append("TPS: ").append(String.format("%.2f", TPSMonitor.getCurrentTPS())).append("/20.00\n");
            report.append("Memory: ").append(String.format("%.1f%%", PerformanceTracker.getCurrentMemoryUsage())).append("\n");
            report.append("Chunks: ").append(PerformanceTracker.getTotalChunksLoaded()).append(" loaded\n");
            report.append("Avg Tick: ").append(String.format("%.2f", TPSMonitor.getAverageTickTime())).append("ms\n");
            report.append("Lag Spikes: ").append(TPSMonitor.getRecentLagSpikes().size()).append(" recent\n");
            return report.toString();
        } catch (Exception e) {
            return "Error generating performance report: " + e.getMessage();
        }
    }
}