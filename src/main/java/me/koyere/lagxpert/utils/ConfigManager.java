package me.koyere.lagxpert.utils;

import me.koyere.lagxpert.LagXpert;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Loads and provides access to LagXpert configuration values
 * from modular YAML files (mobs, storage, redstone, alerts, task),
 * and global control via config.yml.
 */
public class ConfigManager {

    // === STORAGE LIMITS ===
    private static int maxHoppersPerChunk;
    private static int maxChestsPerChunk;
    private static int maxFurnacesPerChunk;
    private static int maxBlastFurnacesPerChunk;
    private static int maxShulkerBoxesPerChunk;
    private static int maxDroppersPerChunk;
    private static int maxDispensersPerChunk;
    private static int maxObserversPerChunk;
    private static int maxHopperMinecartsPerChunk;
    private static int maxPistonsPerChunk;
    private static int maxTntPerChunk;
    private static int maxBarrelsPerChunk;

    // === MOB LIMIT ===
    private static int maxMobsPerChunk;

    // === REDSTONE CONTROL ===
    private static int redstoneActiveTicks;
    private static boolean redstoneControlEnabled;

    // === GLOBAL MODULE TOGGLES ===
    private static boolean alertsEnabled;
    private static boolean mobsModuleEnabled;
    private static boolean storageModuleEnabled;
    private static boolean taskModuleEnabled;

    // === TASK CONFIG ===
    private static int scanIntervalTicks;

    /**
     * Loads all modular config files and general control values.
     */
    public static void loadAll() {
        File pluginFolder = LagXpert.getInstance().getDataFolder();

        FileConfiguration mobs = YamlConfiguration.loadConfiguration(new File(pluginFolder, "mobs.yml"));
        FileConfiguration storage = YamlConfiguration.loadConfiguration(new File(pluginFolder, "storage.yml"));
        FileConfiguration redstone = YamlConfiguration.loadConfiguration(new File(pluginFolder, "redstone.yml"));
        FileConfiguration alerts = YamlConfiguration.loadConfiguration(new File(pluginFolder, "alerts.yml"));
        FileConfiguration task = YamlConfiguration.loadConfiguration(new File(pluginFolder, "task.yml"));
        FileConfiguration main = YamlConfiguration.loadConfiguration(new File(pluginFolder, "config.yml"));

        // === MOB LIMIT ===
        maxMobsPerChunk = mobs.getInt("limits.mobs-per-chunk", 40);

        // === STORAGE LIMITS ===
        maxHoppersPerChunk         = storage.getInt("limits.hoppers-per-chunk", 8);
        maxChestsPerChunk          = storage.getInt("limits.chests-per-chunk", 20);
        maxFurnacesPerChunk        = storage.getInt("limits.furnaces-per-chunk", 10);
        maxBlastFurnacesPerChunk   = storage.getInt("limits.blast_furnaces-per-chunk", 6);
        maxShulkerBoxesPerChunk    = storage.getInt("limits.shulker_boxes-per-chunk", 10);
        maxDroppersPerChunk        = storage.getInt("limits.droppers-per-chunk", 10);
        maxDispensersPerChunk      = storage.getInt("limits.dispensers-per-chunk", 10);
        maxObserversPerChunk       = storage.getInt("limits.observers-per-chunk", 10);
        maxHopperMinecartsPerChunk = storage.getInt("limits.hopper_minecarts-per-chunk", 4);
        maxPistonsPerChunk         = storage.getInt("limits.pistons-per-chunk", 12);
        maxTntPerChunk             = storage.getInt("limits.tnt-per-chunk", 6);
        maxBarrelsPerChunk         = storage.getInt("limits.barrels-per-chunk", 10);

        // === REDSTONE CONTROL ===
        redstoneActiveTicks = redstone.getInt("control.redstone-active-ticks", 100);
        redstoneControlEnabled = redstone.getBoolean("control.enabled", true);

        // === TASK CONFIG ===
        scanIntervalTicks = task.getInt("task.scan-interval-ticks", 600);
        alertsEnabled = alerts.getBoolean("alerts.enabled", true);

        // === MODULE TOGGLES ===
        mobsModuleEnabled = main.getBoolean("modules.mobs", true);
        storageModuleEnabled = main.getBoolean("modules.storage", true);
        taskModuleEnabled = main.getBoolean("modules.task", true);
    }

    // === LIMIT GETTERS ===
    public static int getMaxMobsPerChunk() {
        return maxMobsPerChunk;
    }

    public static int getMaxHoppersPerChunk() {
        return maxHoppersPerChunk;
    }

    public static int getMaxChestsPerChunk() {
        return maxChestsPerChunk;
    }

    public static int getMaxFurnacesPerChunk() {
        return maxFurnacesPerChunk;
    }

    public static int getMaxBlastFurnacesPerChunk() {
        return maxBlastFurnacesPerChunk;
    }

    public static int getMaxShulkerBoxesPerChunk() {
        return maxShulkerBoxesPerChunk;
    }

    public static int getMaxDroppersPerChunk() {
        return maxDroppersPerChunk;
    }

    public static int getMaxDispensersPerChunk() {
        return maxDispensersPerChunk;
    }

    public static int getMaxObserversPerChunk() {
        return maxObserversPerChunk;
    }

    public static int getMaxHopperMinecartsPerChunk() {
        return maxHopperMinecartsPerChunk;
    }

    public static int getMaxPistonsPerChunk() {
        return maxPistonsPerChunk;
    }

    public static int getMaxTntPerChunk() {
        return maxTntPerChunk;
    }

    public static int getMaxBarrelsPerChunk() {
        return maxBarrelsPerChunk;
    }

    // === REDSTONE ===
    public static int getRedstoneActiveTicks() {
        return redstoneActiveTicks;
    }

    // === TASK ===
    public static int getScanIntervalTicks() {
        return scanIntervalTicks;
    }

    // === MODULE TOGGLES ===
    public static boolean areAlertsEnabled() {
        return alertsEnabled;
    }

    public static boolean isRedstoneControlEnabled() {
        return redstoneControlEnabled;
    }

    public static boolean isMobsModuleEnabled() {
        return mobsModuleEnabled;
    }

    public static boolean isStorageModuleEnabled() {
        return storageModuleEnabled;
    }

    public static boolean isTaskModuleEnabled() {
        return taskModuleEnabled;
    }
}

