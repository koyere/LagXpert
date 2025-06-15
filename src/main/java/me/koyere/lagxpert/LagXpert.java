package me.koyere.lagxpert;

import me.koyere.lagxpert.commands.*;
import me.koyere.lagxpert.config.WorldConfigManager;
import me.koyere.lagxpert.gui.GUIManager;
import me.koyere.lagxpert.listeners.*;
import me.koyere.lagxpert.metrics.MetricsHandler;
import me.koyere.lagxpert.monitoring.PerformanceTracker;
import me.koyere.lagxpert.monitoring.TPSMonitor;
import me.koyere.lagxpert.system.AbyssManager;
import me.koyere.lagxpert.system.ChunkManager;
import me.koyere.lagxpert.system.RedstoneCircuitTracker;
import me.koyere.lagxpert.tasks.AsyncChunkAnalyzer;
import me.koyere.lagxpert.tasks.AutoChunkScanTask;
import me.koyere.lagxpert.tasks.ChunkPreloader;
import me.koyere.lagxpert.tasks.EntityCleanupTask;
import me.koyere.lagxpert.tasks.InactiveChunkUnloader;
import me.koyere.lagxpert.tasks.ItemCleanerTask;
import me.koyere.lagxpert.utils.ChunkUtils;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Main class for the LagXpert plugin.
 * Handles the initialization and deinitialization of plugin components,
 * including configuration, commands, listeners, tasks, metrics, and Phase 1 & 2 features.
 * Enhanced with performance cache system, async processing, advanced redstone control,
 * entity cleanup functionality, comprehensive TPS monitoring system, smart chunk management,
 * per-world configuration system, and GUI configuration system.
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
        createPerWorldConfigurationStructure(); // NEW: Create per-world config structure
        loadPluginLogic();
        registerCommandsAndListeners();
        schedulePluginTasks();
        initializeAdvancedSystems(); // Phase 1 systems
        initializeMonitoringSystems(); // Phase 2 systems
        initializeChunkManagementSystems(); // Phase 2 chunk management
        initializeGUISystem(); // Phase 2 GUI system
        initializeMetrics(); // Initialize bStats and custom charts

        getLogger().info("LagXpert Free v" + getDescription().getVersion() + " enabled successfully with Phase 1 & 2 optimizations, per-world configuration support, and GUI configuration system.");
    }

    @Override
    public void onDisable() {
        // Shutdown GUI system gracefully
        shutdownGUISystem();

        // Shutdown Phase 2 systems gracefully
        shutdownChunkManagementSystems();
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
     * Enhanced to include Phase 1 & 2 configuration files and prevent unnecessary warnings.
     */
    private void saveDefaultConfigurations() {
        // Save config.yml (always handled by saveDefaultConfig())
        saveDefaultConfig();

        // List of configuration files to check and save
        String[] configFiles = {
                "mobs.yml",
                "storage.yml",
                "redstone.yml",
                "alerts.yml",
                "task.yml",
                "messages.yml",
                "itemcleaner.yml",
                "entitycleanup.yml", // Phase 1 entity cleanup config
                "monitoring.yml",    // Phase 2 monitoring config
                "chunks.yml"         // Phase 2 chunk management config
        };

        // Only save files that don't exist to prevent warnings
        for (String fileName : configFiles) {
            File configFile = new File(getDataFolder(), fileName);
            if (!configFile.exists()) {
                saveResource(fileName, false);

                if (ConfigManager.isDebugEnabled()) {
                    getLogger().info("[LagXpert] Created default configuration file: " + fileName);
                }
            }
        }
    }

    /**
     * Creates the per-world configuration structure and example world configuration files.
     * This method creates the worlds/ folder and example configuration files for standard worlds.
     * NEW: Per-world configuration system setup.
     */
    private void createPerWorldConfigurationStructure() {
        try {
            // Create the worlds configuration folder
            File worldsFolder = new File(getDataFolder(), "config" + File.separator + "worlds");
            if (!worldsFolder.exists()) {
                if (worldsFolder.mkdirs()) {
                    getLogger().info("[LagXpert] Created per-world configuration folder: " + worldsFolder.getPath());
                } else {
                    getLogger().warning("[LagXpert] Failed to create per-world configuration folder: " + worldsFolder.getPath());
                    return;
                }
            }

            // Create example world configuration files if they don't exist
            createExampleWorldConfig(worldsFolder, "world.yml");
            createExampleWorldConfig(worldsFolder, "world_nether.yml");
            createExampleWorldConfig(worldsFolder, "world_the_end.yml");

            if (ConfigManager.isDebugEnabled()) {
                getLogger().info("[LagXpert] Per-world configuration structure initialized successfully.");
            }

        } catch (Exception e) {
            getLogger().severe("[LagXpert] Failed to create per-world configuration structure: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates an example world configuration file if it doesn't exist.
     * Uses the resource files from the JAR as templates.
     *
     * @param worldsFolder The worlds configuration folder
     * @param fileName The configuration file name (e.g., "world.yml")
     */
    private void createExampleWorldConfig(File worldsFolder, String fileName) {
        File worldConfigFile = new File(worldsFolder, fileName);

        if (!worldConfigFile.exists()) {
            try {
                // Try to save the resource file from JAR to the worlds folder
                String resourcePath = "config" + File.separator + "worlds" + File.separator + fileName;

                // Check if the resource exists in the JAR
                if (getResource(resourcePath) != null) {
                    saveResource(resourcePath, false);

                    // Move the file from plugin folder root to worlds subfolder
                    File sourceFile = new File(getDataFolder(), resourcePath);
                    if (sourceFile.exists()) {
                        if (sourceFile.renameTo(worldConfigFile)) {
                            getLogger().info("[LagXpert] Created example world configuration: " + fileName);
                        } else {
                            getLogger().warning("[LagXpert] Failed to move world configuration file: " + fileName);
                        }

                        // Clean up any created intermediate directories
                        File configDir = new File(getDataFolder(), "config");
                        if (configDir.exists() && configDir.isDirectory()) {
                            File[] contents = configDir.listFiles();
                            if (contents != null && contents.length == 0) {
                                configDir.delete();
                            }
                        }
                    }
                } else {
                    // Resource doesn't exist in JAR, create a basic template
                    createBasicWorldConfigTemplate(worldConfigFile, fileName);
                }

            } catch (Exception e) {
                getLogger().warning("[LagXpert] Failed to create world configuration " + fileName + ": " + e.getMessage());
                // Fallback: create a basic template
                createBasicWorldConfigTemplate(worldConfigFile, fileName);
            }
        }
    }

    /**
     * Creates a basic world configuration template file.
     * Used as fallback when resource files are not available.
     *
     * @param configFile The configuration file to create
     * @param fileName The name of the configuration file
     */
    private void createBasicWorldConfigTemplate(File configFile, String fileName) {
        try {
            String worldName = fileName.replace(".yml", "");
            StringBuilder content = new StringBuilder();

            content.append("# LagXpert - Per-World Configuration for: ").append(worldName).append("\n");
            content.append("# This file allows you to override global settings for this specific world.\n");
            content.append("# Only include settings you want to override - missing settings will use global defaults.\n");
            content.append("# You can copy any setting from the main config files (mobs.yml, storage.yml, etc.) here.\n");
            content.append("#\n");
            content.append("# Example overrides:\n");
            content.append("# limits:\n");
            content.append("#   mobs-per-chunk: 30        # Override global mob limit\n");
            content.append("#   hoppers-per-chunk: 5      # Override global hopper limit\n");
            content.append("#\n");
            content.append("# monitoring:\n");
            content.append("#   tps:\n");
            content.append("#     alert-thresholds:\n");
            content.append("#       warning: 16.0         # Different TPS warning for this world\n");
            content.append("#\n");
            content.append("# Remove or comment out sections you don't want to override.\n");
            content.append("\n");

            // Add specific examples based on world type
            if (worldName.contains("nether")) {
                content.append("# Nether-specific overrides - lower limits due to hostile environment\n");
                content.append("limits:\n");
                content.append("  mobs-per-chunk: 25\n");
                content.append("  hoppers-per-chunk: 6\n");
                content.append("  tnt-per-chunk: 0 # No TNT in nether by default\n");
                content.append("\n");
                content.append("# More aggressive entity cleanup in nether\n");
                content.append("entity-cleanup:\n");
                content.append("  cleanup-targets:\n");
                content.append("    abandoned-vehicles: true\n");
                content.append("  advanced:\n");
                content.append("    max-entities-per-chunk: 150\n");
                content.append("\n");
                content.append("# Different TPS thresholds for nether\n");
                content.append("monitoring:\n");
                content.append("  tps:\n");
                content.append("    alert-thresholds:\n");
                content.append("      warning: 17.0\n");
                content.append("      critical: 14.0\n");
            } else if (worldName.contains("end")) {
                content.append("# End-specific overrides - very restrictive limits for the end\n");
                content.append("limits:\n");
                content.append("  mobs-per-chunk: 20\n");
                content.append("  hoppers-per-chunk: 4\n");
                content.append("  chests-per-chunk: 10\n");
                content.append("\n");
                content.append("# Disable certain features in the end\n");
                content.append("chunk-management:\n");
                content.append("  preload:\n");
                content.append("    enabled: false\n");
                content.append("\n");
                content.append("item-cleaner:\n");
                content.append("  warning:\n");
                content.append("    enabled: false # No warnings in end\n");
                content.append("\n");
                content.append("# Stricter performance monitoring\n");
                content.append("monitoring:\n");
                content.append("  tps:\n");
                content.append("    alert-thresholds:\n");
                content.append("      warning: 18.5\n");
                content.append("      critical: 16.0\n");
            } else {
                content.append("# Example overrides for overworld\n");
                content.append("# limits:\n");
                content.append("#   mobs-per-chunk: 40\n");
                content.append("#   hoppers-per-chunk: 8\n");
                content.append("# Remove the # to activate these overrides\n");
            }

            // Write content to file
            java.nio.file.Files.write(configFile.toPath(), content.toString().getBytes());

            if (ConfigManager.isDebugEnabled()) {
                getLogger().info("[LagXpert] Created basic world configuration template: " + fileName);
            }

        } catch (Exception e) {
            getLogger().warning("[LagXpert] Failed to create basic world configuration template " + fileName + ": " + e.getMessage());
        }
    }

    /**
     * Loads essential plugin data and configurations into memory.
     * ConfigManager.loadAll() is responsible for loading all YAML files
     * and initializing other managers like MessageManager with their respective configs.
     * Now includes WorldConfigManager initialization.
     */
    private void loadPluginLogic() {
        ConfigManager.loadAll(); // This now also initializes WorldConfigManager
        AbyssManager.loadConfig(); // AbyssManager fetches its config from ConfigManager values
    }

    /**
     * Registers all plugin commands and their executors/tab completers.
     * Also registers event listeners, conditionally based on module activation
     * status retrieved from ConfigManager.
     * Enhanced with GUI command registration.
     */
    private void registerCommandsAndListeners() {
        getCommand("lagxpert").setExecutor(new LagXpertCommand());
        getCommand("lagxpert").setTabCompleter(new LagXpertCommand());
        getCommand("chunkstatus").setExecutor(new ChunkStatusCommand());
        getCommand("abyss").setExecutor(new AbyssCommand());
        getCommand("clearitems").setExecutor(new ClearItemsCommand());
        getCommand("clearitems").setTabCompleter(new ClearItemsCommand()); // Assuming ClearItemsCommand also implements TabCompleter

        // Register GUI command
        getCommand("lagxpertgui").setExecutor(new LagXpertGUICommand());
        getCommand("lagxpertgui").setTabCompleter(new LagXpertGUICommand());

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
     * Initializes Phase 2 smart chunk management systems.
     * NEW: Intelligent chunk loading/unloading with activity tracking and preloading.
     */
    private void initializeChunkManagementSystems() {
        if (!ConfigManager.isChunkManagementModuleEnabled()) {
            if (ConfigManager.isDebugEnabled()) {
                getLogger().info("[LagXpert] Chunk management module is disabled, skipping chunk management system initialization.");
            }
            return;
        }

        try {
            // Start inactive chunk unloader
            if (ConfigManager.isAutoUnloadEnabled()) {
                InactiveChunkUnloader.start();
                getLogger().info("[LagXpert] Inactive chunk unloader initialized.");
            }

            // Start chunk preloader
            if (ConfigManager.isChunkPreloadEnabled()) {
                ChunkPreloader.start();
                getLogger().info("[LagXpert] Chunk preloader initialized.");
            }

            // Initialize chunk activity tracking (always initialize if module is enabled)
            if (ConfigManager.isChunkActivityTrackingEnabled()) {
                // ChunkManager is passive and doesn't need explicit initialization
                getLogger().info("[LagXpert] Chunk activity tracking initialized.");
            }

            if (ConfigManager.isDebugEnabled()) {
                getLogger().info("[LagXpert] Chunk management systems initialized successfully:");
                getLogger().info("  - Auto Unloading: " + (ConfigManager.isAutoUnloadEnabled() ? "Enabled" : "Disabled"));
                getLogger().info("  - Chunk Preloading: " + (ConfigManager.isChunkPreloadEnabled() ? "Enabled" : "Disabled"));
                getLogger().info("  - Activity Tracking: " + (ConfigManager.isChunkActivityTrackingEnabled() ? "Enabled" : "Disabled"));
                getLogger().info("  - Directional Preloading: " + (ConfigManager.isDirectionalPreloadingEnabled() ? "Enabled" : "Disabled"));
                getLogger().info("  - Per-World Settings: " + (ConfigManager.isPerWorldSettingsEnabled() ? "Enabled" : "Disabled"));
            }

        } catch (Exception e) {
            getLogger().severe("[LagXpert] Failed to initialize chunk management systems: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Initializes Phase 2 GUI configuration system.
     * NEW: Interactive GUI-based configuration management.
     */
    private void initializeGUISystem() {
        try {
            if (GUIManager.initialize(this)) {
                getLogger().info("[LagXpert] GUI configuration system initialized successfully.");

                if (ConfigManager.isDebugEnabled()) {
                    getLogger().info("[LagXpert] GUI system features:");
                    getLogger().info("  - Interactive configuration editing");
                    getLogger().info("  - Real-time setting preview");
                    getLogger().info("  - Session management and timeout handling");
                    getLogger().info("  - Permission-based access control");
                }
            } else {
                getLogger().warning("[LagXpert] Failed to initialize GUI configuration system.");
            }

        } catch (Exception e) {
            getLogger().severe("[LagXpert] Failed to initialize GUI system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gracefully shuts down Phase 2 GUI system.
     * NEW: Ensures proper cleanup of GUI sessions and resources.
     */
    private void shutdownGUISystem() {
        try {
            GUIManager.shutdown();
            getLogger().info("[LagXpert] GUI configuration system shutdown completed.");

        } catch (Exception e) {
            getLogger().warning("[LagXpert] Error during GUI system shutdown: " + e.getMessage());
        }
    }

    /**
     * Gracefully shuts down Phase 2 chunk management systems.
     * NEW: Ensures proper cleanup of chunk management tasks and data.
     */
    private void shutdownChunkManagementSystems() {
        try {
            // Stop chunk preloader
            ChunkPreloader.stop();
            getLogger().info("[LagXpert] Chunk preloader shutdown completed.");

            // Stop inactive chunk unloader
            InactiveChunkUnloader.stop();
            getLogger().info("[LagXpert] Inactive chunk unloader shutdown completed.");

            // Reset chunk management statistics
            ChunkManager.resetStatistics();
            getLogger().info("[LagXpert] Chunk management statistics reset.");

        } catch (Exception e) {
            getLogger().warning("[LagXpert] Error during chunk management systems shutdown: " + e.getMessage());
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
     * Enhanced to include monitoring system, chunk management, GUI system, and per-world configuration statistics.
     *
     * @return Map containing performance statistics from cache, async, circuit, monitoring, chunk management, GUI, and per-world systems
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

            // Phase 2 chunk management statistics
            if (ConfigManager.isChunkManagementModuleEnabled()) {
                stats.put("chunk_management", ChunkManager.getStatistics());
                stats.put("chunk_unloader", InactiveChunkUnloader.getStatistics());
                stats.put("chunk_preloader", ChunkPreloader.getStatistics());
            }

            // Phase 2 GUI system statistics
            if (GUIManager.isInitialized()) {
                stats.put("gui_system", GUIManager.getInstance().getSessionStatistics());
            }

            // Per-world configuration statistics
            stats.put("per_world_config", WorldConfigManager.getStatistics());

        } catch (Exception e) {
            if (getInstance() != null) {
                getInstance().getLogger().warning("[LagXpert] Error collecting performance statistics: " + e.getMessage());
            }
        }

        return stats;
    }

    /**
     * Resets all performance statistics counters.
     * Enhanced to include monitoring system, chunk management, GUI system, and per-world configuration statistics.
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

            // Reset chunk management statistics
            if (ConfigManager.isChunkManagementModuleEnabled()) {
                ChunkManager.resetStatistics();
                InactiveChunkUnloader.resetStatistics();
                ChunkPreloader.resetStatistics();
            }

            // Note: GUI statistics are reset automatically through session management

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
     * Enhanced with chunk management, GUI system, and per-world configuration information.
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

            // Add chunk management information if enabled
            if (ConfigManager.isChunkManagementModuleEnabled()) {
                java.util.Map<String, Object> chunkStats = ChunkManager.getStatistics();
                report.append("Chunk Management: ");
                report.append("Unloaded: ").append(chunkStats.getOrDefault("total_chunks_unloaded", 0));
                report.append(", Preloaded: ").append(chunkStats.getOrDefault("total_chunks_preloaded", 0)).append("\n");
            }

            // Add GUI system information if enabled
            if (GUIManager.isInitialized()) {
                java.util.Map<String, Object> guiStats = GUIManager.getInstance().getSessionStatistics();
                report.append("GUI Sessions: ").append(guiStats.getOrDefault("active_sessions", 0));
                report.append("/").append(guiStats.getOrDefault("max_concurrent_sessions", 0)).append("\n");
            }

            // Add per-world configuration information
            if (ConfigManager.isPerWorldSettingsEnabled()) {
                java.util.Map<String, Object> worldStats = WorldConfigManager.getStatistics();
                report.append("Per-World Configs: ").append(worldStats.getOrDefault("total_world_configs", 0));
                report.append(" (").append(worldStats.getOrDefault("custom_configs", 0)).append(" custom)\n");
            }

            return report.toString();
        } catch (Exception e) {
            return "Error generating performance report: " + e.getMessage();
        }
    }

    /**
     * Triggers manual chunk management operations (for admin commands).
     * NEW: Allows manual triggering of chunk unloading and preloading for testing.
     */
    public static void triggerManualChunkManagement() {
        if (!ConfigManager.isChunkManagementModuleEnabled()) {
            if (getInstance() != null) {
                getInstance().getLogger().warning("[LagXpert] Chunk management module is disabled.");
            }
            return;
        }

        try {
            if (ConfigManager.isAutoUnloadEnabled()) {
                InactiveChunkUnloader.triggerManualUnload();
            }

            if (ConfigManager.isChunkPreloadEnabled()) {
                ChunkPreloader.triggerManualPreload();
            }

            if (getInstance() != null && ConfigManager.isDebugEnabled()) {
                getInstance().getLogger().info("[LagXpert] Manual chunk management triggered.");
            }
        } catch (Exception e) {
            if (getInstance() != null) {
                getInstance().getLogger().warning("[LagXpert] Error triggering manual chunk management: " + e.getMessage());
            }
        }
    }

    /**
     * Opens the GUI configuration interface for a player.
     * NEW: Provides programmatic access to GUI system for other plugins or commands.
     *
     * @param player The player to open the GUI for
     * @return true if the GUI was opened successfully
     */
    public static boolean openConfigurationGUI(Player player) {
        if (!GUIManager.isInitialized()) {
            if (getInstance() != null) {
                getInstance().getLogger().warning("[LagXpert] GUI system is not initialized.");
            }
            return false;
        }

        try {
            return GUIManager.getInstance().openConfigGUI(player);
        } catch (Exception e) {
            if (getInstance() != null) {
                getInstance().getLogger().warning("[LagXpert] Error opening configuration GUI for " + player.getName() + ": " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Closes any open GUI for a player.
     * NEW: Provides programmatic access to close GUIs for other plugins or commands.
     *
     * @param player The player whose GUI should be closed
     */
    public static void closeConfigurationGUI(Player player) {
        if (!GUIManager.isInitialized()) {
            return;
        }

        try {
            GUIManager.getInstance().closeGUI(player);
        } catch (Exception e) {
            if (getInstance() != null) {
                getInstance().getLogger().warning("[LagXpert] Error closing configuration GUI for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Gets GUI system statistics.
     * NEW: Provides information about active GUI sessions and usage.
     *
     * @return Map containing GUI system statistics, or empty map if system is not initialized
     */
    public static java.util.Map<String, Object> getGUISystemStatistics() {
        if (!GUIManager.isInitialized()) {
            return new java.util.HashMap<>();
        }

        try {
            return GUIManager.getInstance().getSessionStatistics();
        } catch (Exception e) {
            if (getInstance() != null) {
                getInstance().getLogger().warning("[LagXpert] Error collecting GUI system statistics: " + e.getMessage());
            }
            return new java.util.HashMap<>();
        }
    }

    /**
     * Reloads the GUI system configuration.
     * NEW: Allows runtime reloading of GUI system settings.
     */
    public static void reloadGUISystem() {
        if (!GUIManager.isInitialized()) {
            if (getInstance() != null) {
                getInstance().getLogger().warning("[LagXpert] GUI system is not initialized.");
            }
            return;
        }

        try {
            GUIManager.getInstance().reload();

            if (getInstance() != null && ConfigManager.isDebugEnabled()) {
                getInstance().getLogger().info("[LagXpert] GUI system reloaded successfully.");
            }
        } catch (Exception e) {
            if (getInstance() != null) {
                getInstance().getLogger().warning("[LagXpert] Error reloading GUI system: " + e.getMessage());
            }
        }
    }

    /**
     * Reloads per-world configurations.
     * NEW: Allows runtime reloading of per-world configuration files.
     */
    public static void reloadPerWorldConfigurations() {
        try {
            WorldConfigManager.reloadAll();

            if (getInstance() != null && ConfigManager.isDebugEnabled()) {
                getInstance().getLogger().info("[LagXpert] Per-world configurations reloaded successfully.");
            }
        } catch (Exception e) {
            if (getInstance() != null) {
                getInstance().getLogger().warning("[LagXpert] Error reloading per-world configurations: " + e.getMessage());
            }
        }
    }

    /**
     * Gets per-world configuration statistics.
     * NEW: Provides information about the per-world configuration system.
     *
     * @return Map containing per-world configuration statistics
     */
    public static java.util.Map<String, Object> getPerWorldConfigurationStatistics() {
        return WorldConfigManager.getStatistics();
    }

    /**
     * Creates a new world configuration file.
     * NEW: Allows dynamic creation of world configuration files.
     *
     * @param worldName The name of the world to create configuration for
     * @return true if the configuration was created successfully, false otherwise
     */
    public static boolean createWorldConfiguration(String worldName) {
        try {
            boolean created = WorldConfigManager.createWorldConfig(worldName);

            if (created && getInstance() != null && ConfigManager.isDebugEnabled()) {
                getInstance().getLogger().info("[LagXpert] Created world configuration for: " + worldName);
            }

            return created;
        } catch (Exception e) {
            if (getInstance() != null) {
                getInstance().getLogger().warning("[LagXpert] Error creating world configuration for " + worldName + ": " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Deletes a world configuration file.
     * NEW: Allows dynamic deletion of world configuration files.
     *
     * @param worldName The name of the world to delete configuration for
     * @return true if the configuration was deleted successfully, false otherwise
     */
    public static boolean deleteWorldConfiguration(String worldName) {
        try {
            boolean deleted = WorldConfigManager.deleteWorldConfig(worldName);

            if (deleted && getInstance() != null && ConfigManager.isDebugEnabled()) {
                getInstance().getLogger().info("[LagXpert] Deleted world configuration for: " + worldName);
            }

            return deleted;
        } catch (Exception e) {
            if (getInstance() != null) {
                getInstance().getLogger().warning("[LagXpert] Error deleting world configuration for " + worldName + ": " + e.getMessage());
            }
            return false;
        }
    }
}