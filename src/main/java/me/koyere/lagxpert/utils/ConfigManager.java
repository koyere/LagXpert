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

    private static int maxMobsPerChunk;
    private static int maxHoppersPerChunk;
    private static int maxChestsPerChunk;
    private static int maxFurnacesPerChunk;
    private static int redstoneActiveTicks;
    private static int scanIntervalTicks;

    private static boolean alertsEnabled;
    private static boolean redstoneControlEnabled;

    private static boolean mobsModuleEnabled;
    private static boolean storageModuleEnabled;
    private static boolean taskModuleEnabled;

    /**
     * Loads all modular config files and general control values.
     */
    public static void loadAll() {
        File pluginFolder = LagXpert.getInstance().getDataFolder();

        // Load modular files
        FileConfiguration mobs = YamlConfiguration.loadConfiguration(new File(pluginFolder, "mobs.yml"));
        FileConfiguration storage = YamlConfiguration.loadConfiguration(new File(pluginFolder, "storage.yml"));
        FileConfiguration redstone = YamlConfiguration.loadConfiguration(new File(pluginFolder, "redstone.yml"));
        FileConfiguration alerts = YamlConfiguration.loadConfiguration(new File(pluginFolder, "alerts.yml"));
        FileConfiguration task = YamlConfiguration.loadConfiguration(new File(pluginFolder, "task.yml"));
        FileConfiguration main = YamlConfiguration.loadConfiguration(new File(pluginFolder, "config.yml"));

        // Load modular values
        maxMobsPerChunk = mobs.getInt("limits.mobs-per-chunk", 40);
        maxHoppersPerChunk = storage.getInt("limits.hoppers-per-chunk", 8);
        maxChestsPerChunk = storage.getInt("limits.chests-per-chunk", 20);
        maxFurnacesPerChunk = storage.getInt("limits.furnaces-per-chunk", 10);

        redstoneActiveTicks = redstone.getInt("control.redstone-active-ticks", 100);
        redstoneControlEnabled = redstone.getBoolean("control.enabled", true);

        alertsEnabled = alerts.getBoolean("alerts.enabled", true);
        scanIntervalTicks = task.getInt("task.scan-interval-ticks", 600);

        // Load module activation states from config.yml
        mobsModuleEnabled = main.getBoolean("modules.mobs", true);
        storageModuleEnabled = main.getBoolean("modules.storage", true);
        taskModuleEnabled = main.getBoolean("modules.task", true);
        // redstoneControlEnabled is already loaded from redstone.yml for better decoupling
    }

    // === LIMITS ===

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

    public static int getRedstoneActiveTicks() {
        return redstoneActiveTicks;
    }

    public static int getScanIntervalTicks() {
        return scanIntervalTicks;
    }

    // === TOGGLES ===

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

