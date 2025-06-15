package me.koyere.lagxpert.utils;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.config.WorldConfigManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;

/**
 * Loads and provides access to LagXpert configuration values
 * from modular YAML files and the main config.yml.
 * Initializes MessageManager with the messages configuration.
 * All configurations are loaded into static fields for easy access.
 * Enhanced with per-world configuration support through WorldConfigManager integration.
 * Extended with setter methods for GUI configuration management.
 * Fixed configuration loading and validation for entity cleanup and item cleaner systems.
 */
public class ConfigManager {

    // Configuration file names
    private static final String MOBS_YML = "mobs.yml";
    private static final String STORAGE_YML = "storage.yml";
    private static final String REDSTONE_YML = "redstone.yml";
    private static final String ALERTS_YML = "alerts.yml";
    private static final String TASK_YML = "task.yml";
    private static final String MESSAGES_YML = "messages.yml";
    private static final String ITEMCLEANER_YML = "itemcleaner.yml";
    private static final String ENTITYCLEANUP_YML = "entitycleanup.yml";
    private static final String MONITORING_YML = "monitoring.yml";
    private static final String CHUNKS_YML = "chunks.yml";
    private static final String CONFIG_YML = "config.yml"; // Main configuration

    // Store loaded configurations for setter operations
    private static final Map<String, FileConfiguration> loadedConfigs = new HashMap<>();
    private static final Map<String, File> configFiles = new HashMap<>();

    // === STORAGE LIMITS (from storage.yml) ===
    private static int maxHoppersPerChunk;
    private static int maxChestsPerChunk;
    private static int maxFurnacesPerChunk;
    private static int maxBlastFurnacesPerChunk;
    private static int maxSmokersPerChunk;
    private static int maxBarrelsPerChunk;
    private static int maxDroppersPerChunk;
    private static int maxDispensersPerChunk;
    private static int maxShulkerBoxesPerChunk;
    private static int maxTntPerChunk;
    private static int maxPistonsPerChunk;
    private static int maxObserversPerChunk;

    // === MOB LIMIT (from mobs.yml) ===
    private static int maxMobsPerChunk;

    // === REDSTONE CONTROL (settings from redstone.yml, module toggle from config.yml) ===
    private static int redstoneActiveTicks;
    private static boolean redstoneControlModuleEnabled;

    // === MODULE TOGGLES (all master toggles from config.yml) ===
    private static boolean alertsModuleEnabled; // Overall alerts system toggle
    private static boolean mobsModuleEnabled;
    private static boolean storageModuleEnabled;
    private static boolean autoChunkScanModuleEnabled;
    private static boolean itemCleanerModuleEnabled;
    private static boolean entityCleanupModuleEnabled;
    private static boolean monitoringModuleEnabled;
    private static boolean chunkManagementModuleEnabled;

    // === FINE-GRAINED ALERT TOGGLES (from alerts.yml) ===
    private static boolean alertOnMobsLimitReached;
    private static boolean alertOnHoppersLimitReached;
    private static boolean alertOnChestsLimitReached;
    private static boolean alertOnFurnacesLimitReached;
    private static boolean alertOnBlastFurnacesLimitReached;
    private static boolean alertOnSmokersLimitReached;
    private static boolean alertOnBarrelsLimitReached;
    private static boolean alertOnDroppersLimitReached;
    private static boolean alertOnDispensersLimitReached;
    private static boolean alertOnShulkerBoxesLimitReached;
    private static boolean alertOnTntLimitReached;
    private static boolean alertOnPistonsLimitReached;
    private static boolean alertOnObserversLimitReached;
    private static boolean alertOnRedstoneActivity;

    private static boolean warnOnMobsNearLimit;
    private static boolean warnOnHoppersNearLimit;
    private static boolean warnOnChestsNearLimit;
    private static boolean warnOnFurnacesNearLimit;
    private static boolean warnOnBlastFurnacesNearLimit;
    private static boolean warnOnSmokersNearLimit;
    private static boolean warnOnBarrelsNearLimit;
    private static boolean warnOnDroppersNearLimit;
    private static boolean warnOnDispensersNearLimit;
    private static boolean warnOnShulkerBoxesNearLimit;
    private static boolean warnOnTntNearLimit;
    private static boolean warnOnPistonsNearLimit;
    private static boolean warnOnObserversNearLimit;

    private static boolean autoScanSendOverloadSummary;
    private static boolean autoScanTriggerIndividualNearLimitWarnings;

    // === ALERT COOLDOWN (from alerts.yml) ===
    private static int alertCooldownDefaultSeconds;

    // === TASK CONFIG (from task.yml for AutoChunkScanTask) ===
    private static int scanIntervalTicks;

    // === ITEM CLEANER CONFIG (settings from itemcleaner.yml, module toggle from config.yml) ===
    private static int itemCleanerIntervalTicks;
    private static long itemCleanerInitialDelayTicks;
    private static boolean itemCleanerWarningEnabled;
    private static int itemCleanerWarningTimeSeconds;
    private static String itemCleanerWarningMessage;
    private static String itemCleanerCleanedMessage;
    private static List<String> itemCleanerEnabledWorlds;
    private static List<String> itemCleanerExcludedItems;

    // === ABYSS SYSTEM CONFIG (settings from itemcleaner.yml) ===
    private static boolean abyssEnabled;
    private static int abyssRetentionSeconds;
    private static int abyssMaxItemsPerPlayer;
    private static String abyssRecoverMessage;
    private static String abyssEmptyMessage;
    private static String abyssRecoverFailFullInvMessage;

    // === ENTITY CLEANUP CONFIG (settings from entitycleanup.yml, module toggle from config.yml) ===
    private static int entityCleanupIntervalTicks;
    private static long entityCleanupInitialDelayTicks;
    private static List<String> entityCleanupEnabledWorlds;
    private static boolean cleanupInvalidEntities;
    private static boolean cleanupDuplicateEntities;
    private static boolean cleanupOutOfBoundsEntities;
    private static boolean cleanupAbandonedVehicles;
    private static boolean cleanupEmptyItemFrames;
    private static boolean cleanupEmptyArmorStands;
    private static int maxEntitiesPerChunk;
    private static double duplicateDetectionRadius;
    private static int abandonedVehicleTimeoutSeconds;
    private static boolean skipNamedEntities;
    private static boolean skipTamedAnimals;
    private static boolean skipLeashedEntities;
    private static List<String> protectedEntityTypes;
    private static List<String> blacklistedWorlds;
    private static String entityCleanupCompleteMessage;
    private static boolean broadcastEntityCleanupCompletion;
    private static int entityCleanupBroadcastThreshold;
    private static int maxEntitiesPerCycle;
    private static boolean spreadAcrossTicks;
    private static int entitiesPerTick;
    private static boolean detailedLogging;
    private static boolean logStatistics;
    private static boolean includeLocations;

    // === MONITORING CONFIG (settings from monitoring.yml, module toggle from config.yml) ===
    private static boolean tpsMonitoringEnabled;
    private static int tpsUpdateIntervalTicks;
    private static int tpsShortTermWindow;
    private static int tpsMediumTermWindow;
    private static int tpsLongTermWindow;
    private static double tpsCriticalThreshold;
    private static double tpsWarningThreshold;
    private static double tpsGoodThreshold;
    private static boolean tpsHistoryEnabled;
    private static int tpsMaxRecords;
    private static int tpsSnapshotIntervalSeconds;
    private static boolean tpsAutoCleanup;
    private static boolean memoryMonitoringEnabled;
    private static double memoryCriticalThreshold;
    private static double memoryWarningThreshold;
    private static double memoryGoodThreshold;
    private static int memoryUpdateIntervalSeconds;
    private static boolean gcMonitoringEnabled;
    private static boolean chunkMonitoringEnabled;
    private static boolean trackChunkEvents;
    private static int maxLoadedChunksWarning;
    private static boolean chunkLoadingRateMonitoring;
    private static int chunkLoadingRateThreshold;
    private static boolean lagDetectionEnabled;
    private static double lagDetectionThreshold;
    private static int consecutiveLagSpikesThreshold;
    private static int maxTrackedLagSpikes;
    private static boolean autoAnalyzeLagSpikes;
    private static boolean monitoringAlertsEnabled;
    private static boolean alertsToConsole;
    private static boolean alertsToPlayers;
    private static String playerAlertPermission;
    private static int tpsAlertCooldown;
    private static int memoryAlertCooldown;
    private static int lagSpikeAlertCooldown;
    private static boolean analyticsEnabled;
    private static boolean dailyReportsEnabled;
    private static String dailyReportTime;
    private static boolean weeklySummariesEnabled;
    private static String weeklySummaryDay;
    private static boolean exportEnabled;
    private static String exportFormat;
    private static int autoExportIntervalHours;
    private static int maxExportFileSizeMB;
    private static boolean monitoringDetailedLogging;
    private static boolean logTPSCalculations;
    private static boolean logMemoryDetails;
    private static boolean includeStackTraces;
    private static boolean logMonitoringPerformance;

    // === CHUNK MANAGEMENT CONFIG (settings from chunks.yml, module toggle from config.yml) ===
    private static boolean autoUnloadEnabled;
    private static int inactivityThresholdMinutes;
    private static int playerActivityRadius;
    private static int minChunksPerWorld;
    private static int maxUnloadsPerCycle;
    private static int unloadCycleIntervalTicks;
    private static boolean preloadEnabled;
    private static int preloadRadius;
    private static int maxPreloadsPerCycle;
    private static int preloadCycleIntervalTicks;
    private static boolean directionalPreloading;
    private static double minMovementSpeed;
    private static boolean activityTrackingEnabled;
    private static boolean trackPlayerVisits;
    private static boolean trackBlockChanges;
    private static boolean trackEntityChanges;
    private static int maxActivityAgeHours;
    private static int activityCleanupIntervalTicks;
    private static boolean borderChunksEnabled;
    private static boolean aggressiveBorderUnload;
    private static int borderDistanceChunks;
    private static boolean reduceBorderTicking;
    private static boolean protectImportantBlocks;
    private static boolean protectActiveRedstone;
    private static boolean protectNamedEntities;
    private static boolean protectPlayerStructures;
    private static int structureDiversityThreshold;
    private static boolean perWorldSettingsEnabled;
    private static boolean chunkStatisticsEnabled;
    private static boolean trackChunkOperations;
    private static boolean trackPerformanceImpact;
    private static boolean trackMovementPatterns;
    private static boolean trackMemoryImpact;
    private static int statisticsMaxAgeDays;
    private static int statisticsCleanupIntervalHours;
    private static boolean chunkDebugEnabled;
    private static boolean logChunkOperations;
    private static boolean logChunkActivity;
    private static boolean logChunkPerformance;
    private static boolean includeChunkCoordinates;
    private static String chunksUnloadedMessage;
    private static String chunksPreloadedMessage;
    private static boolean broadcastChunkOperations;
    private static int chunkBroadcastThreshold;

    // === GENERAL OPTIONS (from config.yml) ===
    private static boolean debugEnabled;

    private static FileConfiguration loadConfigurationFile(File pluginFolder, String fileName) {
        File configFile = new File(pluginFolder, fileName);

        if (!configFile.exists()) {
            LagXpert.getInstance().getLogger().warning("[ConfigManager] Configuration file not found: " + fileName);
            return new YamlConfiguration(); // Return empty config to prevent null pointer exceptions
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Store for setter operations
        loadedConfigs.put(fileName, config);
        configFiles.put(fileName, configFile);

        return config;
    }

    public static void loadAll() {
        File pluginFolder = LagXpert.getInstance().getDataFolder();

        FileConfiguration mainConfig = loadConfigurationFile(pluginFolder, CONFIG_YML);
        FileConfiguration mobsConfig = loadConfigurationFile(pluginFolder, MOBS_YML);
        FileConfiguration storageConfig = loadConfigurationFile(pluginFolder, STORAGE_YML);
        FileConfiguration redstoneConfig = loadConfigurationFile(pluginFolder, REDSTONE_YML);
        FileConfiguration alertsConfig = loadConfigurationFile(pluginFolder, ALERTS_YML);
        FileConfiguration taskConfig = loadConfigurationFile(pluginFolder, TASK_YML);
        FileConfiguration itemCleanerConfig = loadConfigurationFile(pluginFolder, ITEMCLEANER_YML);
        FileConfiguration entityCleanupConfig = loadConfigurationFile(pluginFolder, ENTITYCLEANUP_YML);
        FileConfiguration monitoringConfig = loadConfigurationFile(pluginFolder, MONITORING_YML);
        FileConfiguration chunksConfig = loadConfigurationFile(pluginFolder, CHUNKS_YML);
        FileConfiguration messagesFileConfig = loadConfigurationFile(pluginFolder, MESSAGES_YML);

        MessageManager.initialize(messagesFileConfig);

        // === MODULE TOGGLES (from config.yml) ===
        mobsModuleEnabled = mainConfig.getBoolean("modules.mobs", true);
        storageModuleEnabled = mainConfig.getBoolean("modules.storage", true);
        redstoneControlModuleEnabled = mainConfig.getBoolean("modules.redstone", true);
        alertsModuleEnabled = mainConfig.getBoolean("modules.alerts", true);
        autoChunkScanModuleEnabled = mainConfig.getBoolean("modules.auto-chunk-scan", true);
        itemCleanerModuleEnabled = mainConfig.getBoolean("modules.item-cleaner", true);
        entityCleanupModuleEnabled = mainConfig.getBoolean("modules.entity-cleanup", true);
        monitoringModuleEnabled = mainConfig.getBoolean("modules.monitoring", true);
        chunkManagementModuleEnabled = mainConfig.getBoolean("modules.chunk-management", true);

        // === FINE-GRAINED ALERT TOGGLES (from alerts.yml) ===
        alertOnMobsLimitReached = alertsConfig.getBoolean("show-limit-reached-alerts.mobs", true);
        alertOnHoppersLimitReached = alertsConfig.getBoolean("show-limit-reached-alerts.hoppers", true);
        alertOnChestsLimitReached = alertsConfig.getBoolean("show-limit-reached-alerts.chests", true);
        alertOnFurnacesLimitReached = alertsConfig.getBoolean("show-limit-reached-alerts.furnaces", true);
        alertOnBlastFurnacesLimitReached = alertsConfig.getBoolean("show-limit-reached-alerts.blast_furnaces", true);
        alertOnSmokersLimitReached = alertsConfig.getBoolean("show-limit-reached-alerts.smokers", true);
        alertOnBarrelsLimitReached = alertsConfig.getBoolean("show-limit-reached-alerts.barrels", true);
        alertOnDroppersLimitReached = alertsConfig.getBoolean("show-limit-reached-alerts.droppers", true);
        alertOnDispensersLimitReached = alertsConfig.getBoolean("show-limit-reached-alerts.dispensers", true);
        alertOnShulkerBoxesLimitReached = alertsConfig.getBoolean("show-limit-reached-alerts.shulker_boxes", true);
        alertOnTntLimitReached = alertsConfig.getBoolean("show-limit-reached-alerts.tnt", true);
        alertOnPistonsLimitReached = alertsConfig.getBoolean("show-limit-reached-alerts.pistons", true);
        alertOnObserversLimitReached = alertsConfig.getBoolean("show-limit-reached-alerts.observers", true);
        alertOnRedstoneActivity = alertsConfig.getBoolean("show-limit-reached-alerts.redstone", true);

        warnOnMobsNearLimit = alertsConfig.getBoolean("show-near-limit-warnings.mobs", true);
        warnOnHoppersNearLimit = alertsConfig.getBoolean("show-near-limit-warnings.hoppers", true);
        warnOnChestsNearLimit = alertsConfig.getBoolean("show-near-limit-warnings.chests", true);
        warnOnFurnacesNearLimit = alertsConfig.getBoolean("show-near-limit-warnings.furnaces", true);
        warnOnBlastFurnacesNearLimit = alertsConfig.getBoolean("show-near-limit-warnings.blast_furnaces", true);
        warnOnSmokersNearLimit = alertsConfig.getBoolean("show-near-limit-warnings.smokers", true);
        warnOnBarrelsNearLimit = alertsConfig.getBoolean("show-near-limit-warnings.barrels", true);
        warnOnDroppersNearLimit = alertsConfig.getBoolean("show-near-limit-warnings.droppers", true);
        warnOnDispensersNearLimit = alertsConfig.getBoolean("show-near-limit-warnings.dispensers", true);
        warnOnShulkerBoxesNearLimit = alertsConfig.getBoolean("show-near-limit-warnings.shulker_boxes", true);
        warnOnTntNearLimit = alertsConfig.getBoolean("show-near-limit-warnings.tnt", true);
        warnOnPistonsNearLimit = alertsConfig.getBoolean("show-near-limit-warnings.pistons", true);
        warnOnObserversNearLimit = alertsConfig.getBoolean("show-near-limit-warnings.observers", true);

        autoScanSendOverloadSummary = alertsConfig.getBoolean("auto-chunk-scan-alerts.send-overload-summary", true);
        autoScanTriggerIndividualNearLimitWarnings = alertsConfig.getBoolean("auto-chunk-scan-alerts.trigger-individual-near-limit-warnings", true);

        // === ALERT COOLDOWN (from alerts.yml) ===
        alertCooldownDefaultSeconds = alertsConfig.getInt("alert-cooldown.default-seconds", 15);

        // === MOB LIMIT (from mobs.yml) ===
        maxMobsPerChunk = mobsConfig.getInt("limits.mobs-per-chunk", 40);

        // === STORAGE LIMITS (from storage.yml) ===
        maxHoppersPerChunk = storageConfig.getInt("limits.hoppers-per-chunk", 8);
        maxChestsPerChunk = storageConfig.getInt("limits.chests-per-chunk", 20);
        maxFurnacesPerChunk = storageConfig.getInt("limits.furnaces-per-chunk", 10);
        maxBlastFurnacesPerChunk = storageConfig.getInt("limits.blast_furnaces-per-chunk", 6);
        maxSmokersPerChunk = storageConfig.getInt("limits.smokers-per-chunk", 6);
        maxBarrelsPerChunk = storageConfig.getInt("limits.barrels-per-chunk", 10);
        maxDroppersPerChunk = storageConfig.getInt("limits.droppers-per-chunk", 10);
        maxDispensersPerChunk = storageConfig.getInt("limits.dispensers-per-chunk", 10);
        maxShulkerBoxesPerChunk = storageConfig.getInt("limits.shulker_boxes-per-chunk", 5);
        maxTntPerChunk = storageConfig.getInt("limits.tnt-per-chunk", 6);
        maxPistonsPerChunk = storageConfig.getInt("limits.pistons-per-chunk", 12);
        maxObserversPerChunk = storageConfig.getInt("limits.observers-per-chunk", 10);

        // === REDSTONE CONTROL (settings from redstone.yml) ===
        redstoneActiveTicks = redstoneConfig.getInt("control.redstone-active-ticks", 100);

        // === TASK CONFIG (AutoChunkScanTask, settings from task.yml) ===
        scanIntervalTicks = taskConfig.getInt("task.scan-interval-ticks", 600);

        // === ITEM CLEANER CONFIG (settings from itemcleaner.yml) ===
        itemCleanerIntervalTicks = itemCleanerConfig.getInt("item-cleaner.interval-ticks", 6000);
        itemCleanerInitialDelayTicks = itemCleanerConfig.getLong("item-cleaner.initial-delay-ticks", 6000L);
        itemCleanerWarningEnabled = itemCleanerConfig.getBoolean("item-cleaner.warning.enabled", true);
        itemCleanerWarningTimeSeconds = itemCleanerConfig.getInt("item-cleaner.warning.time-seconds", 10);
        itemCleanerWarningMessage = itemCleanerConfig.getString("item-cleaner.messages.warning", "&e[LagXpert] &7Ground items will be cleared in &c{seconds}&7 seconds!");
        itemCleanerCleanedMessage = itemCleanerConfig.getString("item-cleaner.messages.cleaned", "&a[LagXpert] &fCleared &e{count}&f ground item(s).");

        // FIXED: Proper loading of enabled worlds list
        itemCleanerEnabledWorlds = itemCleanerConfig.getStringList("item-cleaner.enabled-worlds");
        if (itemCleanerEnabledWorlds == null || itemCleanerEnabledWorlds.isEmpty()) {
            itemCleanerEnabledWorlds = new ArrayList<>();
            itemCleanerEnabledWorlds.add("world");
            itemCleanerEnabledWorlds.add("world_nether");
            itemCleanerEnabledWorlds.add("world_the_end");
        }

        // FIXED: Proper loading of excluded items list
        itemCleanerExcludedItems = itemCleanerConfig.getStringList("item-cleaner.excluded-items");
        if (itemCleanerExcludedItems == null) {
            itemCleanerExcludedItems = new ArrayList<>();
        }

        // === ABYSS SYSTEM CONFIG (settings from itemcleaner.yml) ===
        abyssEnabled = itemCleanerModuleEnabled && itemCleanerConfig.getBoolean("abyss.enabled", true);
        abyssRetentionSeconds = itemCleanerConfig.getInt("abyss.retention-seconds", 120);
        abyssMaxItemsPerPlayer = itemCleanerConfig.getInt("abyss.max-items-per-player", 30);
        abyssRecoverMessage = itemCleanerConfig.getString("abyss.messages.recover", "&aYou recovered &f{count} &aitem(s) from the abyss.");
        abyssEmptyMessage = itemCleanerConfig.getString("abyss.messages.empty", "&7You have no items to recover from the abyss.");
        abyssRecoverFailFullInvMessage = itemCleanerConfig.getString("abyss.messages.recover-fail-full-inv", "&cYour inventory was full! Some recovered items may have been dropped on the ground.");

        // === ENTITY CLEANUP CONFIG (settings from entitycleanup.yml) ===
        entityCleanupIntervalTicks = entityCleanupConfig.getInt("entity-cleanup.interval-ticks", 6000);
        entityCleanupInitialDelayTicks = entityCleanupConfig.getLong("entity-cleanup.initial-delay-ticks", 6000L);

        // FIXED: Proper loading of enabled worlds list for entity cleanup
        entityCleanupEnabledWorlds = entityCleanupConfig.getStringList("entity-cleanup.enabled-worlds");
        if (entityCleanupEnabledWorlds == null || entityCleanupEnabledWorlds.isEmpty()) {
            entityCleanupEnabledWorlds = new ArrayList<>();
            entityCleanupEnabledWorlds.add("world");
            entityCleanupEnabledWorlds.add("world_nether");
            entityCleanupEnabledWorlds.add("world_the_end");
        }

        cleanupInvalidEntities = entityCleanupConfig.getBoolean("entity-cleanup.cleanup-targets.invalid-entities", true);
        cleanupDuplicateEntities = entityCleanupConfig.getBoolean("entity-cleanup.cleanup-targets.duplicate-entities", true);
        cleanupOutOfBoundsEntities = entityCleanupConfig.getBoolean("entity-cleanup.cleanup-targets.out-of-bounds-entities", true);
        cleanupAbandonedVehicles = entityCleanupConfig.getBoolean("entity-cleanup.cleanup-targets.abandoned-vehicles", true);
        cleanupEmptyItemFrames = entityCleanupConfig.getBoolean("entity-cleanup.cleanup-targets.empty-item-frames", false);
        cleanupEmptyArmorStands = entityCleanupConfig.getBoolean("entity-cleanup.cleanup-targets.empty-armor-stands", false);

        maxEntitiesPerChunk = entityCleanupConfig.getInt("entity-cleanup.advanced.max-entities-per-chunk", 200);
        duplicateDetectionRadius = entityCleanupConfig.getDouble("entity-cleanup.advanced.duplicate-detection-radius", 1.0);
        abandonedVehicleTimeoutSeconds = entityCleanupConfig.getInt("entity-cleanup.advanced.abandoned-vehicle-timeout-seconds", 300);
        skipNamedEntities = entityCleanupConfig.getBoolean("entity-cleanup.advanced.skip-named-entities", true);
        skipTamedAnimals = entityCleanupConfig.getBoolean("entity-cleanup.advanced.skip-tamed-animals", true);
        skipLeashedEntities = entityCleanupConfig.getBoolean("entity-cleanup.advanced.skip-leashed-entities", true);

        // FIXED: Proper loading of protected entity types list
        protectedEntityTypes = entityCleanupConfig.getStringList("entity-cleanup.exclusions.protected-entity-types");
        if (protectedEntityTypes == null) {
            protectedEntityTypes = new ArrayList<>();
        }
        // Add default protected entities if list is empty
        if (protectedEntityTypes.isEmpty()) {
            protectedEntityTypes.add("VILLAGER");
            protectedEntityTypes.add("IRON_GOLEM");
            protectedEntityTypes.add("ENDER_DRAGON");
            protectedEntityTypes.add("WITHER");
            protectedEntityTypes.add("PLAYER");
        }

        // FIXED: Proper loading of blacklisted worlds list
        blacklistedWorlds = entityCleanupConfig.getStringList("entity-cleanup.exclusions.blacklisted-worlds");
        if (blacklistedWorlds == null) {
            blacklistedWorlds = new ArrayList<>();
        }

        entityCleanupCompleteMessage = entityCleanupConfig.getString("entity-cleanup.messages.cleanup-complete", "&a[LagXpert] Entity cleanup completed. Removed &e{count}&a problematic entities.");
        broadcastEntityCleanupCompletion = entityCleanupConfig.getBoolean("entity-cleanup.messages.broadcast-completion", false);
        entityCleanupBroadcastThreshold = entityCleanupConfig.getInt("entity-cleanup.messages.broadcast-threshold", 10);

        maxEntitiesPerCycle = entityCleanupConfig.getInt("performance.max-entities-per-cycle", 5000);
        spreadAcrossTicks = entityCleanupConfig.getBoolean("performance.spread-across-ticks", true);
        entitiesPerTick = entityCleanupConfig.getInt("performance.entities-per-tick", 100);

        detailedLogging = entityCleanupConfig.getBoolean("debug.detailed-logging", false);
        logStatistics = entityCleanupConfig.getBoolean("debug.log-statistics", true);
        includeLocations = entityCleanupConfig.getBoolean("debug.include-locations", false);

        // === MONITORING CONFIG (settings from monitoring.yml) ===
        tpsMonitoringEnabled = monitoringConfig.getBoolean("monitoring.tps.enabled", true);
        tpsUpdateIntervalTicks = monitoringConfig.getInt("monitoring.tps.update-interval-ticks", 20);
        tpsShortTermWindow = monitoringConfig.getInt("monitoring.tps.calculation-windows.short-term", 60);
        tpsMediumTermWindow = monitoringConfig.getInt("monitoring.tps.calculation-windows.medium-term", 300);
        tpsLongTermWindow = monitoringConfig.getInt("monitoring.tps.calculation-windows.long-term", 900);
        tpsCriticalThreshold = monitoringConfig.getDouble("monitoring.tps.alert-thresholds.critical", 15.0);
        tpsWarningThreshold = monitoringConfig.getDouble("monitoring.tps.alert-thresholds.warning", 18.0);
        tpsGoodThreshold = monitoringConfig.getDouble("monitoring.tps.alert-thresholds.good", 19.5);
        tpsHistoryEnabled = monitoringConfig.getBoolean("monitoring.tps.history.enabled", true);
        tpsMaxRecords = monitoringConfig.getInt("monitoring.tps.history.max-records", 8640);
        tpsSnapshotIntervalSeconds = monitoringConfig.getInt("monitoring.tps.history.snapshot-interval-seconds", 10);
        tpsAutoCleanup = monitoringConfig.getBoolean("monitoring.tps.history.auto-cleanup", true);

        memoryMonitoringEnabled = monitoringConfig.getBoolean("monitoring.memory.enabled", true);
        memoryCriticalThreshold = monitoringConfig.getDouble("monitoring.memory.alert-thresholds.critical", 95.0);
        memoryWarningThreshold = monitoringConfig.getDouble("monitoring.memory.alert-thresholds.warning", 85.0);
        memoryGoodThreshold = monitoringConfig.getDouble("monitoring.memory.alert-thresholds.good", 70.0);
        memoryUpdateIntervalSeconds = monitoringConfig.getInt("monitoring.memory.update-interval-seconds", 30);
        gcMonitoringEnabled = monitoringConfig.getBoolean("monitoring.memory.gc-monitoring", true);

        chunkMonitoringEnabled = monitoringConfig.getBoolean("monitoring.chunks.enabled", true);
        trackChunkEvents = monitoringConfig.getBoolean("monitoring.chunks.track-events", true);
        maxLoadedChunksWarning = monitoringConfig.getInt("monitoring.chunks.max-loaded-chunks-warning", 5000);
        chunkLoadingRateMonitoring = monitoringConfig.getBoolean("monitoring.chunks.loading-rate-monitoring", true);
        chunkLoadingRateThreshold = monitoringConfig.getInt("monitoring.chunks.loading-rate-threshold", 100);

        lagDetectionEnabled = monitoringConfig.getBoolean("monitoring.lag-detection.enabled", true);
        lagDetectionThreshold = monitoringConfig.getDouble("monitoring.lag-detection.tick-threshold-ms", 100.0);
        consecutiveLagSpikesThreshold = monitoringConfig.getInt("monitoring.lag-detection.consecutive-spikes-threshold", 3);
        maxTrackedLagSpikes = monitoringConfig.getInt("monitoring.lag-detection.max-tracked-spikes", 100);
        autoAnalyzeLagSpikes = monitoringConfig.getBoolean("monitoring.lag-detection.auto-analyze", true);

        monitoringAlertsEnabled = monitoringConfig.getBoolean("alerts.enabled", true);
        alertsToConsole = monitoringConfig.getBoolean("alerts.delivery.console", true);
        alertsToPlayers = monitoringConfig.getBoolean("alerts.delivery.players", true);
        playerAlertPermission = monitoringConfig.getString("alerts.delivery.player-permission", "lagxpert.monitoring.alerts");
        tpsAlertCooldown = monitoringConfig.getInt("alerts.cooldown.tps-alerts", 60);
        memoryAlertCooldown = monitoringConfig.getInt("alerts.cooldown.memory-alerts", 120);
        lagSpikeAlertCooldown = monitoringConfig.getInt("alerts.cooldown.lag-spike-alerts", 30);

        analyticsEnabled = monitoringConfig.getBoolean("analytics.enabled", true);
        dailyReportsEnabled = monitoringConfig.getBoolean("analytics.reports.daily-reports", true);
        dailyReportTime = monitoringConfig.getString("analytics.reports.daily-report-time", "06:00");
        weeklySummariesEnabled = monitoringConfig.getBoolean("analytics.reports.weekly-summaries", true);
        weeklySummaryDay = monitoringConfig.getString("analytics.reports.weekly-summary-day", "SUNDAY");
        exportEnabled = monitoringConfig.getBoolean("analytics.export.enabled", false);
        exportFormat = monitoringConfig.getString("analytics.export.format", "JSON");
        autoExportIntervalHours = monitoringConfig.getInt("analytics.export.auto-export-interval-hours", 0);
        maxExportFileSizeMB = monitoringConfig.getInt("analytics.export.max-file-size-mb", 10);

        monitoringDetailedLogging = monitoringConfig.getBoolean("debug.detailed-logging", false);
        logTPSCalculations = monitoringConfig.getBoolean("debug.log-tps-calculations", false);
        logMemoryDetails = monitoringConfig.getBoolean("debug.log-memory-details", false);
        includeStackTraces = monitoringConfig.getBoolean("debug.include-stack-traces", false);
        logMonitoringPerformance = monitoringConfig.getBoolean("debug.log-monitoring-performance", false);

        // === CHUNK MANAGEMENT CONFIG (settings from chunks.yml) ===
        autoUnloadEnabled = chunksConfig.getBoolean("chunk-management.auto-unload.enabled", true);
        inactivityThresholdMinutes = chunksConfig.getInt("chunk-management.auto-unload.inactivity-threshold-minutes", 15);
        playerActivityRadius = chunksConfig.getInt("chunk-management.auto-unload.player-activity-radius", 8);
        minChunksPerWorld = chunksConfig.getInt("chunk-management.auto-unload.min-chunks-per-world", 50);
        maxUnloadsPerCycle = chunksConfig.getInt("chunk-management.auto-unload.max-unloads-per-cycle", 20);
        unloadCycleIntervalTicks = chunksConfig.getInt("chunk-management.auto-unload.unload-cycle-interval-ticks", 1200);

        preloadEnabled = chunksConfig.getBoolean("chunk-management.preload.enabled", true);
        preloadRadius = chunksConfig.getInt("chunk-management.preload.preload-radius", 4);
        maxPreloadsPerCycle = chunksConfig.getInt("chunk-management.preload.max-preloads-per-cycle", 10);
        preloadCycleIntervalTicks = chunksConfig.getInt("chunk-management.preload.preload-cycle-interval-ticks", 100);
        directionalPreloading = chunksConfig.getBoolean("chunk-management.preload.directional-preloading", true);
        minMovementSpeed = chunksConfig.getDouble("chunk-management.preload.min-movement-speed", 2.0);

        activityTrackingEnabled = chunksConfig.getBoolean("chunk-management.activity-tracking.enabled", true);
        trackPlayerVisits = chunksConfig.getBoolean("chunk-management.activity-tracking.track-player-visits", true);
        trackBlockChanges = chunksConfig.getBoolean("chunk-management.activity-tracking.track-block-changes", true);
        trackEntityChanges = chunksConfig.getBoolean("chunk-management.activity-tracking.track-entity-changes", true);
        maxActivityAgeHours = chunksConfig.getInt("chunk-management.activity-tracking.max-activity-age-hours", 24);
        activityCleanupIntervalTicks = chunksConfig.getInt("chunk-management.activity-tracking.cleanup-interval-ticks", 72000);

        borderChunksEnabled = chunksConfig.getBoolean("chunk-management.border-chunks.enabled", true);
        aggressiveBorderUnload = chunksConfig.getBoolean("chunk-management.border-chunks.aggressive-border-unload", true);
        borderDistanceChunks = chunksConfig.getInt("chunk-management.border-chunks.border-distance-chunks", 10);
        reduceBorderTicking = chunksConfig.getBoolean("chunk-management.border-chunks.reduce-border-ticking", true);

        protectImportantBlocks = chunksConfig.getBoolean("chunk-management.safeguards.protect-important-blocks", true);
        protectActiveRedstone = chunksConfig.getBoolean("chunk-management.safeguards.protect-active-redstone", true);
        protectNamedEntities = chunksConfig.getBoolean("chunk-management.safeguards.protect-named-entities", true);
        protectPlayerStructures = chunksConfig.getBoolean("chunk-management.safeguards.protect-player-structures", true);
        structureDiversityThreshold = chunksConfig.getInt("chunk-management.safeguards.structure-diversity-threshold", 20);

        perWorldSettingsEnabled = chunksConfig.getBoolean("chunk-management.world-settings.enabled", false);

        chunkStatisticsEnabled = chunksConfig.getBoolean("statistics.enabled", true);
        trackChunkOperations = chunksConfig.getBoolean("statistics.track.chunk-operations", true);
        trackPerformanceImpact = chunksConfig.getBoolean("statistics.track.performance-impact", true);
        trackMovementPatterns = chunksConfig.getBoolean("statistics.track.movement-patterns", true);
        trackMemoryImpact = chunksConfig.getBoolean("statistics.track.memory-impact", true);
        statisticsMaxAgeDays = chunksConfig.getInt("statistics.cleanup.max-age-days", 7);
        statisticsCleanupIntervalHours = chunksConfig.getInt("statistics.cleanup.cleanup-interval-hours", 24);

        chunkDebugEnabled = chunksConfig.getBoolean("debug.enabled", false);
        logChunkOperations = chunksConfig.getBoolean("debug.log-operations", false);
        logChunkActivity = chunksConfig.getBoolean("debug.log-activity", false);
        logChunkPerformance = chunksConfig.getBoolean("debug.log-performance", false);
        includeChunkCoordinates = chunksConfig.getBoolean("debug.include-coordinates", false);

        chunksUnloadedMessage = chunksConfig.getString("messages.chunks-unloaded", "&7[ChunkManager] Unloaded &e{count}&7 inactive chunks to improve performance.");
        chunksPreloadedMessage = chunksConfig.getString("messages.chunks-preloaded", "&7[ChunkManager] Preloaded &e{count}&7 chunks for player &f{player}&7.");
        broadcastChunkOperations = chunksConfig.getBoolean("messages.broadcast-operations", false);
        chunkBroadcastThreshold = chunksConfig.getInt("messages.broadcast-threshold", 10);

        // === GENERAL OPTIONS (from config.yml) ===
        debugEnabled = mainConfig.getBoolean("debug", false);

        // === INITIALIZE PER-WORLD CONFIGURATION SYSTEM ===
        WorldConfigManager.initialize();

        // Validate critical configurations
        validateConfigurations();
    }

    /**
     * Validates critical configuration values and logs warnings for invalid settings.
     * ADDED: Configuration validation to prevent runtime issues.
     */
    private static void validateConfigurations() {
        // Validate item cleaner settings
        if (itemCleanerWarningTimeSeconds < 0) {
            LagXpert.getInstance().getLogger().warning("[ConfigManager] Invalid item cleaner warning time (negative), using default: 10 seconds");
            itemCleanerWarningTimeSeconds = 10;
        }

        if (itemCleanerIntervalTicks < 200) {
            LagXpert.getInstance().getLogger().warning("[ConfigManager] Item cleaner interval too low (< 10 seconds), using minimum: 200 ticks");
            itemCleanerIntervalTicks = 200;
        }

        // Validate entity cleanup settings
        if (entityCleanupIntervalTicks < 600) {
            LagXpert.getInstance().getLogger().warning("[ConfigManager] Entity cleanup interval too low (< 30 seconds), using minimum: 600 ticks");
            entityCleanupIntervalTicks = 600;
        }

        if (duplicateDetectionRadius < 0.1 || duplicateDetectionRadius > 10.0) {
            LagXpert.getInstance().getLogger().warning("[ConfigManager] Invalid duplicate detection radius, using default: 1.0");
            duplicateDetectionRadius = 1.0;
        }

        // Validate abyss settings
        if (abyssRetentionSeconds < 30) {
            LagXpert.getInstance().getLogger().warning("[ConfigManager] Abyss retention time too low (< 30 seconds), using minimum: 30 seconds");
            abyssRetentionSeconds = 30;
        }

        if (abyssMaxItemsPerPlayer < 1) {
            LagXpert.getInstance().getLogger().warning("[ConfigManager] Invalid abyss max items per player, using default: 30");
            abyssMaxItemsPerPlayer = 30;
        }

        // Log loaded configurations if debug is enabled
        if (debugEnabled) {
            LagXpert.getInstance().getLogger().info("[ConfigManager] Configuration validation completed:");
            LagXpert.getInstance().getLogger().info("  - Item Cleaner Module: " + (itemCleanerModuleEnabled ? "Enabled" : "Disabled"));
            LagXpert.getInstance().getLogger().info("  - Entity Cleanup Module: " + (entityCleanupModuleEnabled ? "Enabled" : "Disabled"));
            LagXpert.getInstance().getLogger().info("  - Abyss System: " + (abyssEnabled ? "Enabled" : "Disabled"));
            LagXpert.getInstance().getLogger().info("  - Excluded Items: " + itemCleanerExcludedItems.size());
            LagXpert.getInstance().getLogger().info("  - Protected Entity Types: " + protectedEntityTypes.size());
        }
    }

    // === SETTER METHODS FOR GUI CONFIGURATION CHANGES ===

    /**
     * Applies GUI configuration changes from a map of key-value pairs.
     * This method handles all configuration changes made through the GUI system.
     *
     * @param changes Map containing configuration keys and their new values
     * @return true if all changes were applied successfully, false otherwise
     */
    public static boolean applyGUIChanges(Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) {
            return true;
        }

        boolean allSuccessful = true;

        try {
            for (Map.Entry<String, Object> entry : changes.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (!applySingleChange(key, value)) {
                    allSuccessful = false;
                    LagXpert.getInstance().getLogger().warning("Failed to apply GUI change: " + key + " = " + value);
                }
            }

            // Save all modified configuration files
            if (allSuccessful) {
                saveAllModifiedConfigs();
            }

        } catch (Exception e) {
            LagXpert.getInstance().getLogger().log(Level.SEVERE, "Error applying GUI changes", e);
            return false;
        }

        return allSuccessful;
    }

    /**
     * Updates excluded items list for item cleaner.
     * ADDED: Method to dynamically update excluded items.
     *
     * @param excludedItems List of material names to exclude from cleanup
     * @return true if the list was updated successfully
     */
    public static boolean updateExcludedItems(List<String> excludedItems) {
        try {
            FileConfiguration config = loadedConfigs.get(ITEMCLEANER_YML);
            if (config != null) {
                config.set("item-cleaner.excluded-items", excludedItems);
                itemCleanerExcludedItems = new ArrayList<>(excludedItems);

                if (debugEnabled) {
                    LagXpert.getInstance().getLogger().info("[ConfigManager] Updated excluded items list: " + excludedItems);
                }
                return true;
            }
        } catch (Exception e) {
            LagXpert.getInstance().getLogger().log(Level.WARNING, "Failed to update excluded items", e);
        }
        return false;
    }

    /**
     * Updates protected entity types list for entity cleanup.
     * ADDED: Method to dynamically update protected entities.
     *
     * @param protectedTypes List of entity type names to protect from cleanup
     * @return true if the list was updated successfully
     */
    public static boolean updateProtectedEntityTypes(List<String> protectedTypes) {
        try {
            FileConfiguration config = loadedConfigs.get(ENTITYCLEANUP_YML);
            if (config != null) {
                config.set("entity-cleanup.exclusions.protected-entity-types", protectedTypes);
                protectedEntityTypes = new ArrayList<>(protectedTypes);

                if (debugEnabled) {
                    LagXpert.getInstance().getLogger().info("[ConfigManager] Updated protected entity types: " + protectedTypes);
                }
                return true;
            }
        } catch (Exception e) {
            LagXpert.getInstance().getLogger().log(Level.WARNING, "Failed to update protected entity types", e);
        }
        return false;
    }

    /**
     * Applies a single configuration change.
     *
     * @param key The configuration key
     * @param value The new value
     * @return true if the change was applied successfully
     */
    private static boolean applySingleChange(String key, Object value) {
        try {
            switch (key) {
                // === MOB LIMITS ===
                case "mobs-per-chunk":
                    return setMobLimit((Integer) value);
                case "mobs-module-enabled":
                    return setMobsModuleEnabled((Boolean) value);

                // === STORAGE LIMITS ===
                case "hoppers-per-chunk":
                    return setHoppersLimit((Integer) value);
                case "chests-per-chunk":
                    return setChestsLimit((Integer) value);
                case "furnaces-per-chunk":
                    return setFurnacesLimit((Integer) value);
                case "blast-furnaces-per-chunk":
                    return setBlastFurnacesLimit((Integer) value);
                case "smokers-per-chunk":
                    return setSmokersLimit((Integer) value);
                case "barrels-per-chunk":
                    return setBarrelsLimit((Integer) value);
                case "droppers-per-chunk":
                    return setDroppersLimit((Integer) value);
                case "dispensers-per-chunk":
                    return setDispensersLimit((Integer) value);
                case "shulker-boxes-per-chunk":
                    return setShulkerBoxesLimit((Integer) value);
                case "tnt-per-chunk":
                    return setTntLimit((Integer) value);
                case "pistons-per-chunk":
                    return setPistonsLimit((Integer) value);
                case "observers-per-chunk":
                    return setObserversLimit((Integer) value);
                case "storage-module-enabled":
                    return setStorageModuleEnabled((Boolean) value);

                // === REDSTONE SETTINGS ===
                case "redstone-active-ticks":
                    return setRedstoneActiveTicks((Integer) value);
                case "redstone-module-enabled":
                    return setRedstoneModuleEnabled((Boolean) value);

                // === CLEANUP SETTINGS ===
                case "item-cleaner-module-enabled":
                    return setItemCleanerModuleEnabled((Boolean) value);
                case "entity-cleanup-module-enabled":
                    return setEntityCleanupModuleEnabled((Boolean) value);

                // === ALERT SETTINGS ===
                case "alerts-module-enabled":
                    return setAlertsModuleEnabled((Boolean) value);
                case "alert-cooldown-seconds":
                    return setAlertCooldownSeconds((Integer) value);

                // === MONITORING SETTINGS ===
                case "monitoring-module-enabled":
                    return setMonitoringModuleEnabled((Boolean) value);
                case "tps-monitoring-enabled":
                    return setTPSMonitoringEnabled((Boolean) value);
                case "memory-monitoring-enabled":
                    return setMemoryMonitoringEnabled((Boolean) value);

                // === ADVANCED SETTINGS ===
                case "debug-enabled":
                    return setDebugEnabled((Boolean) value);
                case "chunk-management-enabled":
                    return setChunkManagementModuleEnabled((Boolean) value);

                default:
                    LagXpert.getInstance().getLogger().warning("Unknown GUI configuration key: " + key);
                    return false;
            }
        } catch (ClassCastException e) {
            LagXpert.getInstance().getLogger().warning("Invalid value type for key " + key + ": " + value);
            return false;
        } catch (Exception e) {
            LagXpert.getInstance().getLogger().log(Level.WARNING, "Error applying change for key " + key, e);
            return false;
        }
    }

    // === INDIVIDUAL SETTER METHODS ===

    // Mob Limits
    public static boolean setMobLimit(int limit) {
        FileConfiguration config = loadedConfigs.get(MOBS_YML);
        if (config != null) {
            config.set("limits.mobs-per-chunk", limit);
            maxMobsPerChunk = limit;
            return true;
        }
        return false;
    }

    public static boolean setMobsModuleEnabled(boolean enabled) {
        FileConfiguration config = loadedConfigs.get(CONFIG_YML);
        if (config != null) {
            config.set("modules.mobs", enabled);
            mobsModuleEnabled = enabled;
            return true;
        }
        return false;
    }

    // Storage Limits
    public static boolean setHoppersLimit(int limit) {
        FileConfiguration config = loadedConfigs.get(STORAGE_YML);
        if (config != null) {
            config.set("limits.hoppers-per-chunk", limit);
            maxHoppersPerChunk = limit;
            return true;
        }
        return false;
    }

    public static boolean setChestsLimit(int limit) {
        FileConfiguration config = loadedConfigs.get(STORAGE_YML);
        if (config != null) {
            config.set("limits.chests-per-chunk", limit);
            maxChestsPerChunk = limit;
            return true;
        }
        return false;
    }

    public static boolean setFurnacesLimit(int limit) {
        FileConfiguration config = loadedConfigs.get(STORAGE_YML);
        if (config != null) {
            config.set("limits.furnaces-per-chunk", limit);
            maxFurnacesPerChunk = limit;
            return true;
        }
        return false;
    }

    public static boolean setBlastFurnacesLimit(int limit) {
        FileConfiguration config = loadedConfigs.get(STORAGE_YML);
        if (config != null) {
            config.set("limits.blast_furnaces-per-chunk", limit);
            maxBlastFurnacesPerChunk = limit;
            return true;
        }
        return false;
    }

    public static boolean setSmokersLimit(int limit) {
        FileConfiguration config = loadedConfigs.get(STORAGE_YML);
        if (config != null) {
            config.set("limits.smokers-per-chunk", limit);
            maxSmokersPerChunk = limit;
            return true;
        }
        return false;
    }

    public static boolean setBarrelsLimit(int limit) {
        FileConfiguration config = loadedConfigs.get(STORAGE_YML);
        if (config != null) {
            config.set("limits.barrels-per-chunk", limit);
            maxBarrelsPerChunk = limit;
            return true;
        }
        return false;
    }

    public static boolean setDroppersLimit(int limit) {
        FileConfiguration config = loadedConfigs.get(STORAGE_YML);
        if (config != null) {
            config.set("limits.droppers-per-chunk", limit);
            maxDroppersPerChunk = limit;
            return true;
        }
        return false;
    }

    public static boolean setDispensersLimit(int limit) {
        FileConfiguration config = loadedConfigs.get(STORAGE_YML);
        if (config != null) {
            config.set("limits.dispensers-per-chunk", limit);
            maxDispensersPerChunk = limit;
            return true;
        }
        return false;
    }

    public static boolean setShulkerBoxesLimit(int limit) {
        FileConfiguration config = loadedConfigs.get(STORAGE_YML);
        if (config != null) {
            config.set("limits.shulker_boxes-per-chunk", limit);
            maxShulkerBoxesPerChunk = limit;
            return true;
        }
        return false;
    }

    public static boolean setTntLimit(int limit) {
        FileConfiguration config = loadedConfigs.get(STORAGE_YML);
        if (config != null) {
            config.set("limits.tnt-per-chunk", limit);
            maxTntPerChunk = limit;
            return true;
        }
        return false;
    }

    public static boolean setPistonsLimit(int limit) {
        FileConfiguration config = loadedConfigs.get(STORAGE_YML);
        if (config != null) {
            config.set("limits.pistons-per-chunk", limit);
            maxPistonsPerChunk = limit;
            return true;
        }
        return false;
    }

    public static boolean setObserversLimit(int limit) {
        FileConfiguration config = loadedConfigs.get(STORAGE_YML);
        if (config != null) {
            config.set("limits.observers-per-chunk", limit);
            maxObserversPerChunk = limit;
            return true;
        }
        return false;
    }

    public static boolean setStorageModuleEnabled(boolean enabled) {
        FileConfiguration config = loadedConfigs.get(CONFIG_YML);
        if (config != null) {
            config.set("modules.storage", enabled);
            storageModuleEnabled = enabled;
            return true;
        }
        return false;
    }

    // Redstone Settings
    public static boolean setRedstoneActiveTicks(int ticks) {
        FileConfiguration config = loadedConfigs.get(REDSTONE_YML);
        if (config != null) {
            config.set("control.redstone-active-ticks", ticks);
            redstoneActiveTicks = ticks;
            return true;
        }
        return false;
    }

    public static boolean setRedstoneModuleEnabled(boolean enabled) {
        FileConfiguration config = loadedConfigs.get(CONFIG_YML);
        if (config != null) {
            config.set("modules.redstone", enabled);
            redstoneControlModuleEnabled = enabled;
            return true;
        }
        return false;
    }

    // Cleanup Settings
    public static boolean setItemCleanerModuleEnabled(boolean enabled) {
        FileConfiguration config = loadedConfigs.get(CONFIG_YML);
        if (config != null) {
            config.set("modules.item-cleaner", enabled);
            itemCleanerModuleEnabled = enabled;
            return true;
        }
        return false;
    }

    public static boolean setEntityCleanupModuleEnabled(boolean enabled) {
        FileConfiguration config = loadedConfigs.get(CONFIG_YML);
        if (config != null) {
            config.set("modules.entity-cleanup", enabled);
            entityCleanupModuleEnabled = enabled;
            return true;
        }
        return false;
    }

    // Alert Settings
    public static boolean setAlertsModuleEnabled(boolean enabled) {
        FileConfiguration config = loadedConfigs.get(CONFIG_YML);
        if (config != null) {
            config.set("modules.alerts", enabled);
            alertsModuleEnabled = enabled;
            return true;
        }
        return false;
    }

    public static boolean setAlertCooldownSeconds(int seconds) {
        FileConfiguration config = loadedConfigs.get(ALERTS_YML);
        if (config != null) {
            config.set("alert-cooldown.default-seconds", seconds);
            alertCooldownDefaultSeconds = seconds;
            return true;
        }
        return false;
    }

    // Monitoring Settings
    public static boolean setMonitoringModuleEnabled(boolean enabled) {
        FileConfiguration config = loadedConfigs.get(CONFIG_YML);
        if (config != null) {
            config.set("modules.monitoring", enabled);
            monitoringModuleEnabled = enabled;
            return true;
        }
        return false;
    }

    public static boolean setTPSMonitoringEnabled(boolean enabled) {
        FileConfiguration config = loadedConfigs.get(MONITORING_YML);
        if (config != null) {
            config.set("monitoring.tps.enabled", enabled);
            tpsMonitoringEnabled = enabled;
            return true;
        }
        return false;
    }

    public static boolean setMemoryMonitoringEnabled(boolean enabled) {
        FileConfiguration config = loadedConfigs.get(MONITORING_YML);
        if (config != null) {
            config.set("monitoring.memory.enabled", enabled);
            memoryMonitoringEnabled = enabled;
            return true;
        }
        return false;
    }

    // Advanced Settings
    public static boolean setDebugEnabled(boolean enabled) {
        FileConfiguration config = loadedConfigs.get(CONFIG_YML);
        if (config != null) {
            config.set("debug", enabled);
            debugEnabled = enabled;
            return true;
        }
        return false;
    }

    public static boolean setChunkManagementModuleEnabled(boolean enabled) {
        FileConfiguration config = loadedConfigs.get(CONFIG_YML);
        if (config != null) {
            config.set("modules.chunk-management", enabled);
            chunkManagementModuleEnabled = enabled;
            return true;
        }
        return false;
    }

    /**
     * Saves all modified configuration files to disk.
     *
     * @return true if all files were saved successfully
     */
    private static boolean saveAllModifiedConfigs() {
        boolean allSuccessful = true;

        for (Map.Entry<String, FileConfiguration> entry : loadedConfigs.entrySet()) {
            String fileName = entry.getKey();
            FileConfiguration config = entry.getValue();
            File configFile = configFiles.get(fileName);

            if (configFile != null) {
                try {
                    config.save(configFile);

                    if (debugEnabled) {
                        LagXpert.getInstance().getLogger().info("Saved configuration file: " + fileName);
                    }
                } catch (IOException e) {
                    allSuccessful = false;
                    LagXpert.getInstance().getLogger().log(Level.SEVERE,
                            "Failed to save configuration file: " + fileName, e);
                }
            }
        }

        return allSuccessful;
    }

    // --- Getters for Mob Limits ---
    public static int getMaxMobsPerChunk() { return maxMobsPerChunk; }

    // --- Getters for Storage Limits ---
    public static int getMaxHoppersPerChunk() { return maxHoppersPerChunk; }
    public static int getMaxChestsPerChunk() { return maxChestsPerChunk; }
    public static int getMaxFurnacesPerChunk() { return maxFurnacesPerChunk; }
    public static int getMaxBlastFurnacesPerChunk() { return maxBlastFurnacesPerChunk; }
    public static int getMaxSmokersPerChunk() { return maxSmokersPerChunk; }
    public static int getMaxBarrelsPerChunk() { return maxBarrelsPerChunk; }
    public static int getMaxDroppersPerChunk() { return maxDroppersPerChunk; }
    public static int getMaxDispensersPerChunk() { return maxDispensersPerChunk; }
    public static int getMaxShulkerBoxesPerChunk() { return maxShulkerBoxesPerChunk; }
    public static int getMaxTntPerChunk() { return maxTntPerChunk; }
    public static int getMaxPistonsPerChunk() { return maxPistonsPerChunk; }
    public static int getMaxObserversPerChunk() { return maxObserversPerChunk; }

    // --- Getters for Redstone Control ---
    public static int getRedstoneActiveTicks() { return redstoneActiveTicks; }

    // --- Getters for Task Configuration (AutoChunkScanTask) ---
    public static int getScanIntervalTicks() { return scanIntervalTicks; }

    // --- Getters for Item Cleaner Configuration ---
    public static int getItemCleanerIntervalTicks() { return itemCleanerIntervalTicks; }
    public static long getItemCleanerInitialDelayTicks() { return itemCleanerInitialDelayTicks; }
    public static boolean isItemCleanerWarningEnabled() { return itemCleanerWarningEnabled; }
    public static int getItemCleanerWarningTimeSeconds() { return itemCleanerWarningTimeSeconds; }
    public static String getItemCleanerWarningMessage() { return itemCleanerWarningMessage; }
    public static String getItemCleanerCleanedMessage() { return itemCleanerCleanedMessage; }
    public static List<String> getItemCleanerEnabledWorlds() { return Collections.unmodifiableList(itemCleanerEnabledWorlds); }
    public static List<String> getItemCleanerExcludedItems() { return Collections.unmodifiableList(itemCleanerExcludedItems); }

    // --- Getters for Abyss System Configuration ---
    public static boolean isAbyssEnabled() { return abyssEnabled; }
    public static int getAbyssRetentionSeconds() { return abyssRetentionSeconds; }
    public static int getAbyssMaxItemsPerPlayer() { return abyssMaxItemsPerPlayer; }
    public static String getAbyssRecoverMessage() { return abyssRecoverMessage; }
    public static String getAbyssEmptyMessage() { return abyssEmptyMessage; }
    public static String getAbyssRecoverFailFullInvMessage() { return abyssRecoverFailFullInvMessage; }

    // --- Getters for Entity Cleanup Configuration ---
    public static boolean isEntityCleanupEnabled() { return entityCleanupModuleEnabled; }
    public static int getEntityCleanupIntervalTicks() { return entityCleanupIntervalTicks; }
    public static long getEntityCleanupInitialDelayTicks() { return entityCleanupInitialDelayTicks; }
    public static List<String> getEntityCleanupEnabledWorlds() { return Collections.unmodifiableList(entityCleanupEnabledWorlds); }
    public static boolean shouldCleanupInvalidEntities() { return cleanupInvalidEntities; }
    public static boolean shouldCleanupDuplicateEntities() { return cleanupDuplicateEntities; }
    public static boolean shouldCleanupOutOfBoundsEntities() { return cleanupOutOfBoundsEntities; }
    public static boolean shouldCleanupAbandonedVehicles() { return cleanupAbandonedVehicles; }
    public static boolean shouldCleanupEmptyItemFrames() { return cleanupEmptyItemFrames; }
    public static boolean shouldCleanupEmptyArmorStands() { return cleanupEmptyArmorStands; }
    public static int getMaxEntitiesPerChunk() { return maxEntitiesPerChunk; }
    public static double getDuplicateDetectionRadius() { return duplicateDetectionRadius; }
    public static int getAbandonedVehicleTimeoutSeconds() { return abandonedVehicleTimeoutSeconds; }
    public static boolean shouldSkipNamedEntities() { return skipNamedEntities; }
    public static boolean shouldSkipTamedAnimals() { return skipTamedAnimals; }
    public static boolean shouldSkipLeashedEntities() { return skipLeashedEntities; }
    public static List<String> getProtectedEntityTypes() { return Collections.unmodifiableList(protectedEntityTypes); }
    public static List<String> getBlacklistedWorlds() { return Collections.unmodifiableList(blacklistedWorlds); }
    public static String getEntityCleanupCompleteMessage() { return entityCleanupCompleteMessage; }
    public static boolean shouldBroadcastEntityCleanupCompletion() { return broadcastEntityCleanupCompletion; }
    public static int getEntityCleanupBroadcastThreshold() { return entityCleanupBroadcastThreshold; }
    public static int getMaxEntitiesPerCycle() { return maxEntitiesPerCycle; }
    public static boolean shouldSpreadAcrossTicks() { return spreadAcrossTicks; }
    public static int getEntitiesPerTick() { return entitiesPerTick; }
    public static boolean isDetailedLogging() { return detailedLogging; }
    public static boolean shouldLogStatistics() { return logStatistics; }
    public static boolean shouldIncludeLocations() { return includeLocations; }

    // --- Getters for Monitoring Configuration ---
    public static boolean isMonitoringModuleEnabled() { return monitoringModuleEnabled; }
    public static boolean isTPSMonitoringEnabled() { return tpsMonitoringEnabled; }
    public static int getTPSUpdateIntervalTicks() { return tpsUpdateIntervalTicks; }
    public static int getTPSShortTermWindow() { return tpsShortTermWindow; }
    public static int getTPSMediumTermWindow() { return tpsMediumTermWindow; }
    public static int getTPSLongTermWindow() { return tpsLongTermWindow; }
    public static double getTPSCriticalThreshold() { return tpsCriticalThreshold; }
    public static double getTPSWarningThreshold() { return tpsWarningThreshold; }
    public static double getTPSGoodThreshold() { return tpsGoodThreshold; }
    public static boolean isTPSHistoryEnabled() { return tpsHistoryEnabled; }
    public static int getTPSMaxRecords() { return tpsMaxRecords; }
    public static int getTPSSnapshotIntervalSeconds() { return tpsSnapshotIntervalSeconds; }
    public static boolean isTPSAutoCleanup() { return tpsAutoCleanup; }
    public static boolean isMemoryMonitoringEnabled() { return memoryMonitoringEnabled; }
    public static double getMemoryCriticalThreshold() { return memoryCriticalThreshold; }
    public static double getMemoryWarningThreshold() { return memoryWarningThreshold; }
    public static double getMemoryGoodThreshold() { return memoryGoodThreshold; }
    public static int getMemoryUpdateIntervalSeconds() { return memoryUpdateIntervalSeconds; }
    public static boolean isGCMonitoringEnabled() { return gcMonitoringEnabled; }
    public static boolean isChunkMonitoringEnabled() { return chunkMonitoringEnabled; }
    public static boolean shouldTrackChunkEvents() { return trackChunkEvents; }
    public static int getMaxLoadedChunksWarning() { return maxLoadedChunksWarning; }
    public static int getChunkLoadingRateThreshold() { return chunkLoadingRateThreshold; }
    public static boolean isChunkLoadingRateMonitoring() { return chunkLoadingRateMonitoring; }
    public static boolean isLagDetectionEnabled() { return lagDetectionEnabled; }
    public static double getLagDetectionThreshold() { return lagDetectionThreshold; }
    public static int getConsecutiveLagSpikesThreshold() { return consecutiveLagSpikesThreshold; }
    public static int getMaxTrackedLagSpikes() { return maxTrackedLagSpikes; }
    public static boolean shouldAutoAnalyzeLagSpikes() { return autoAnalyzeLagSpikes; }
    public static boolean isMonitoringAlertsEnabled() { return monitoringAlertsEnabled; }
    public static boolean shouldSendAlertsToConsole() { return alertsToConsole; }
    public static boolean shouldSendAlertsToPlayers() { return alertsToPlayers; }
    public static String getPlayerAlertPermission() { return playerAlertPermission; }
    public static int getTPSAlertCooldown() { return tpsAlertCooldown; }
    public static int getMemoryAlertCooldown() { return memoryAlertCooldown; }
    public static int getLagSpikeAlertCooldown() { return lagSpikeAlertCooldown; }
    public static boolean isAnalyticsEnabled() { return analyticsEnabled; }
    public static boolean isDailyReportsEnabled() { return dailyReportsEnabled; }
    public static String getDailyReportTime() { return dailyReportTime; }
    public static boolean isWeeklySummariesEnabled() { return weeklySummariesEnabled; }
    public static String getWeeklySummaryDay() { return weeklySummaryDay; }
    public static boolean isExportEnabled() { return exportEnabled; }
    public static String getExportFormat() { return exportFormat; }
    public static int getAutoExportIntervalHours() { return autoExportIntervalHours; }
    public static int getMaxExportFileSizeMB() { return maxExportFileSizeMB; }
    public static boolean isMonitoringDetailedLogging() { return monitoringDetailedLogging; }
    public static boolean shouldLogTPSCalculations() { return logTPSCalculations; }
    public static boolean shouldLogMemoryDetails() { return logMemoryDetails; }
    public static boolean shouldIncludeStackTraces() { return includeStackTraces; }
    public static boolean shouldLogMonitoringPerformance() { return logMonitoringPerformance; }

    // --- Getters for Chunk Management Configuration ---
    public static boolean isChunkManagementModuleEnabled() { return chunkManagementModuleEnabled; }
    public static boolean isAutoUnloadEnabled() { return autoUnloadEnabled; }
    public static int getChunkInactivityThresholdMinutes() { return inactivityThresholdMinutes; }
    public static int getPlayerActivityRadius() { return playerActivityRadius; }
    public static int getMinChunksPerWorld() { return minChunksPerWorld; }
    public static int getMaxUnloadsPerCycle() { return maxUnloadsPerCycle; }
    public static int getUnloadCycleIntervalTicks() { return unloadCycleIntervalTicks; }
    public static boolean isChunkPreloadEnabled() { return preloadEnabled; }
    public static int getPreloadRadius() { return preloadRadius; }
    public static int getMaxPreloadsPerCycle() { return maxPreloadsPerCycle; }
    public static int getPreloadCycleIntervalTicks() { return preloadCycleIntervalTicks; }
    public static boolean isDirectionalPreloadingEnabled() { return directionalPreloading; }
    public static double getMinMovementSpeed() { return minMovementSpeed; }
    public static boolean isChunkActivityTrackingEnabled() { return activityTrackingEnabled; }
    public static boolean shouldTrackPlayerVisits() { return trackPlayerVisits; }
    public static boolean shouldTrackBlockChanges() { return trackBlockChanges; }
    public static boolean shouldTrackEntityChanges() { return trackEntityChanges; }
    public static int getMaxActivityAgeHours() { return maxActivityAgeHours; }
    public static int getActivityCleanupIntervalTicks() { return activityCleanupIntervalTicks; }
    public static boolean isBorderChunksEnabled() { return borderChunksEnabled; }
    public static boolean shouldAggressiveBorderUnload() { return aggressiveBorderUnload; }
    public static int getBorderDistanceChunks() { return borderDistanceChunks; }
    public static boolean shouldReduceBorderTicking() { return reduceBorderTicking; }
    public static boolean shouldProtectImportantBlocks() { return protectImportantBlocks; }
    public static boolean shouldProtectActiveRedstone() { return protectActiveRedstone; }
    public static boolean shouldProtectNamedEntities() { return protectNamedEntities; }
    public static boolean shouldProtectPlayerStructures() { return protectPlayerStructures; }
    public static int getStructureDiversityThreshold() { return structureDiversityThreshold; }
    public static boolean isPerWorldSettingsEnabled() { return perWorldSettingsEnabled; }
    public static boolean isChunkStatisticsEnabled() { return chunkStatisticsEnabled; }
    public static boolean shouldTrackChunkOperations() { return trackChunkOperations; }
    public static boolean shouldTrackPerformanceImpact() { return trackPerformanceImpact; }
    public static boolean shouldTrackMovementPatterns() { return trackMovementPatterns; }
    public static boolean shouldTrackMemoryImpact() { return trackMemoryImpact; }
    public static int getStatisticsMaxAgeDays() { return statisticsMaxAgeDays; }
    public static int getStatisticsCleanupIntervalHours() { return statisticsCleanupIntervalHours; }
    public static boolean isChunkDebugEnabled() { return chunkDebugEnabled; }
    public static boolean shouldLogChunkOperations() { return logChunkOperations; }
    public static boolean shouldLogChunkActivity() { return logChunkActivity; }
    public static boolean shouldLogChunkPerformance() { return logChunkPerformance; }
    public static boolean shouldIncludeChunkCoordinates() { return includeChunkCoordinates; }
    public static String getChunksUnloadedMessage() { return chunksUnloadedMessage; }
    public static String getChunksPreloadedMessage() { return chunksPreloadedMessage; }
    public static boolean shouldBroadcastChunkOperations() { return broadcastChunkOperations; }
    public static int getChunkBroadcastThreshold() { return chunkBroadcastThreshold; }

    // --- Getters for Module Toggles (Master switches from config.yml) ---
    public static boolean isAlertsModuleEnabled() { return alertsModuleEnabled; }
    public static boolean isMobsModuleEnabled() { return mobsModuleEnabled; }
    public static boolean isStorageModuleEnabled() { return storageModuleEnabled; }
    public static boolean isRedstoneControlModuleEnabled() { return redstoneControlModuleEnabled; }
    public static boolean isAutoChunkScanModuleEnabled() { return autoChunkScanModuleEnabled; }
    public static boolean isItemCleanerModuleEnabled() { return itemCleanerModuleEnabled; }
    public static boolean isEntityCleanupModuleEnabled() { return entityCleanupModuleEnabled; }

    // --- Getters for Fine-Grained Alert Toggles (from alerts.yml) ---
    public static boolean shouldAlertOnMobsLimitReached() { return alertOnMobsLimitReached; }
    public static boolean shouldAlertOnHoppersLimitReached() { return alertOnHoppersLimitReached; }
    public static boolean shouldAlertOnChestsLimitReached() { return alertOnChestsLimitReached; }
    public static boolean shouldAlertOnFurnacesLimitReached() { return alertOnFurnacesLimitReached; }
    public static boolean shouldAlertOnBlastFurnacesLimitReached() { return alertOnBlastFurnacesLimitReached; }
    public static boolean shouldAlertOnSmokersLimitReached() { return alertOnSmokersLimitReached; }
    public static boolean shouldAlertOnBarrelsLimitReached() { return alertOnBarrelsLimitReached; }
    public static boolean shouldAlertOnDroppersLimitReached() { return alertOnDroppersLimitReached; }
    public static boolean shouldAlertOnDispensersLimitReached() { return alertOnDispensersLimitReached; }
    public static boolean shouldAlertOnShulkerBoxesLimitReached() { return alertOnShulkerBoxesLimitReached; }
    public static boolean shouldAlertOnTntLimitReached() { return alertOnTntLimitReached; }
    public static boolean shouldAlertOnPistonsLimitReached() { return alertOnPistonsLimitReached; }
    public static boolean shouldAlertOnObserversLimitReached() { return alertOnObserversLimitReached; }
    public static boolean shouldAlertOnRedstoneActivity() { return alertOnRedstoneActivity; }

    public static boolean shouldWarnOnMobsNearLimit() { return warnOnMobsNearLimit; }
    public static boolean shouldWarnOnHoppersNearLimit() { return warnOnHoppersNearLimit; }
    public static boolean shouldWarnOnChestsNearLimit() { return warnOnChestsNearLimit; }
    public static boolean shouldWarnOnFurnacesNearLimit() { return warnOnFurnacesNearLimit; }
    public static boolean shouldWarnOnBlastFurnacesNearLimit() { return warnOnBlastFurnacesNearLimit; }
    public static boolean shouldWarnOnSmokersNearLimit() { return warnOnSmokersNearLimit; }
    public static boolean shouldWarnOnBarrelsNearLimit() { return warnOnBarrelsNearLimit; }
    public static boolean shouldWarnOnDroppersNearLimit() { return warnOnDroppersNearLimit; }
    public static boolean shouldWarnOnDispensersNearLimit() { return warnOnDispensersNearLimit; }
    public static boolean shouldWarnOnShulkerBoxesNearLimit() { return warnOnShulkerBoxesNearLimit; }
    public static boolean shouldWarnOnTntNearLimit() { return warnOnTntNearLimit; }
    public static boolean shouldWarnOnPistonsNearLimit() { return warnOnPistonsNearLimit; }
    public static boolean shouldWarnOnObserversNearLimit() { return warnOnObserversNearLimit; }

    public static boolean shouldAutoScanSendOverloadSummary() { return autoScanSendOverloadSummary; }
    public static boolean shouldAutoScanTriggerIndividualNearLimitWarnings() { return autoScanTriggerIndividualNearLimitWarnings; }

    // --- Getter for Alert Cooldown (from alerts.yml) ---
    public static int getAlertCooldownDefaultSeconds() { return alertCooldownDefaultSeconds; }

    // --- Getter for Debug Mode ---
    public static boolean isDebugEnabled() { return debugEnabled; }

    /**
     * Reloads all configurations including per-world settings.
     * This method should be called when configurations need to be refreshed.
     */
    public static void reloadAll() {
        loadAll(); // This will also reinitialize WorldConfigManager
    }
}