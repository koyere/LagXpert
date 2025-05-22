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

    // === ALERT COOLDOWN (from alerts.yml) === NEW SECTION
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
        FileConfiguration messagesFileConfig = loadConfigurationFile(pluginFolder, MESSAGES_YML);

        MessageManager.initialize(messagesFileConfig);

        // === MODULE TOGGLES (from config.yml) ===
        mobsModuleEnabled = mainConfig.getBoolean("modules.mobs", true);
        storageModuleEnabled = mainConfig.getBoolean("modules.storage", true);
        redstoneControlModuleEnabled = mainConfig.getBoolean("modules.redstone", true);
        alertsModuleEnabled = mainConfig.getBoolean("modules.alerts", true);
        autoChunkScanModuleEnabled = mainConfig.getBoolean("modules.auto-chunk-scan", true);
        itemCleanerModuleEnabled = mainConfig.getBoolean("modules.item-cleaner", true);

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

        // === ALERT COOLDOWN (from alerts.yml) === NEW
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

    // --- Getters for Module Toggles (Master switches from config.yml) ---
    public static boolean isAlertsModuleEnabled() { return alertsModuleEnabled; }
    public static boolean isMobsModuleEnabled() { return mobsModuleEnabled; }
    public static boolean isStorageModuleEnabled() { return storageModuleEnabled; }
    public static boolean isRedstoneControlModuleEnabled() { return redstoneControlModuleEnabled; }
    public static boolean isAutoChunkScanModuleEnabled() { return autoChunkScanModuleEnabled; }
    public static boolean isItemCleanerModuleEnabled() { return itemCleanerModuleEnabled; }

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

    // --- Getter for Alert Cooldown (from alerts.yml) --- NEW
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