package me.koyere.lagxpert.utils;

import me.koyere.lagxpert.LagXpert;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * Loads and provides access to LagXpert configuration values
 * from modular YAML files and the main config.yml.
 * Initializes MessageManager with the messages configuration.
 * All configurations are loaded into static fields for easy access.
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
    private static final String CONFIG_YML = "config.yml"; // Main configuration

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
    private static boolean monitoringModuleEnabled; // NEW

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

    // === MONITORING CONFIG (settings from monitoring.yml, module toggle from config.yml) === NEW
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

    // === GENERAL OPTIONS (from config.yml) ===
    private static boolean debugEnabled;

    private static FileConfiguration loadConfigurationFile(File pluginFolder, String fileName) {
        File configFile = new File(pluginFolder, fileName);
        return YamlConfiguration.loadConfiguration(configFile);
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
        monitoringModuleEnabled = mainConfig.getBoolean("modules.monitoring", true); // NEW

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
        itemCleanerWarningTimeSeconds = itemCleanerConfig.getInt("item-cleaner.warning.time-seconds", 60);
        itemCleanerWarningMessage = itemCleanerConfig.getString("item-cleaner.messages.warning", "&e[LagXpert] Ground items will be cleared in &c{seconds}&7s.");
        itemCleanerCleanedMessage = itemCleanerConfig.getString("item-cleaner.messages.cleaned", "&a[LagXpert] Cleared &e{count}&f ground item(s).");
        itemCleanerEnabledWorlds = itemCleanerConfig.getStringList("item-cleaner.enabled-worlds");
        if (itemCleanerEnabledWorlds.isEmpty()) {
            itemCleanerEnabledWorlds = Collections.singletonList("all");
        }
        itemCleanerExcludedItems = itemCleanerConfig.getStringList("item-cleaner.excluded-items");
        if (itemCleanerExcludedItems == null) {
            itemCleanerExcludedItems = new ArrayList<>();
        }

        // === ABYSS SYSTEM CONFIG (settings from itemcleaner.yml) ===
        abyssEnabled = itemCleanerModuleEnabled && itemCleanerConfig.getBoolean("abyss.enabled", true);
        abyssRetentionSeconds = itemCleanerConfig.getInt("abyss.retention-seconds", 120);
        abyssMaxItemsPerPlayer = itemCleanerConfig.getInt("abyss.max-items-per-player", 30);
        abyssRecoverMessage = itemCleanerConfig.getString("abyss.messages.recover", "&aYou recovered &f{count} &aitem(s) from the abyss.");
        abyssEmptyMessage = itemCleanerConfig.getString("abyss.messages.empty", "&7You have no items to recover.");
        abyssRecoverFailFullInvMessage = itemCleanerConfig.getString("abyss.messages.recover-fail-full-inv", "&cYour inventory was full, some items may have been dropped!");

        // === ENTITY CLEANUP CONFIG (settings from entitycleanup.yml) ===
        entityCleanupIntervalTicks = entityCleanupConfig.getInt("entity-cleanup.interval-ticks", 6000);
        entityCleanupInitialDelayTicks = entityCleanupConfig.getLong("entity-cleanup.initial-delay-ticks", 6000L);
        entityCleanupEnabledWorlds = entityCleanupConfig.getStringList("entity-cleanup.enabled-worlds");
        if (entityCleanupEnabledWorlds.isEmpty()) {
            entityCleanupEnabledWorlds = Collections.singletonList("all");
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

        protectedEntityTypes = entityCleanupConfig.getStringList("entity-cleanup.exclusions.protected-entity-types");
        if (protectedEntityTypes == null) {
            protectedEntityTypes = new ArrayList<>();
        }
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

        // === MONITORING CONFIG (settings from monitoring.yml) === NEW
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

        // === GENERAL OPTIONS (from config.yml) ===
        debugEnabled = mainConfig.getBoolean("debug", false);
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

    // --- Getters for Monitoring Configuration --- NEW
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
    public static boolean isChunkLoadingRateMonitoring() { return chunkLoadingRateMonitoring; }
    public static int getChunkLoadingRateThreshold() { return chunkLoadingRateThreshold; }
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
    /**
     * Gets the default cooldown period in seconds for alerts.
     * If an alert for a specific condition is sent to a player,
     * the same alert for the same condition will not be re-sent until this period has passed.
     * A value of 0 typically disables the cooldown.
     *
     * @return The default alert cooldown in seconds.
     */
    public static int getAlertCooldownDefaultSeconds() { return alertCooldownDefaultSeconds; }

    // --- Getter for Debug Mode ---
    public static boolean isDebugEnabled() { return debugEnabled; }
}