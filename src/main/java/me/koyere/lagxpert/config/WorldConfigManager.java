package me.koyere.lagxpert.config;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-world configuration settings for LagXpert.
 * Allows different limits and settings for different worlds (e.g., nether, end, custom worlds).
 * Provides inheritance from global configuration with world-specific overrides.
 */
public class WorldConfigManager {

    private static final String WORLDS_FOLDER = "worlds";
    private static File worldsConfigFolder;
    private static final Map<String, WorldConfig> worldConfigs = new ConcurrentHashMap<>();
    private static boolean perWorldEnabled = false;

    /**
     * Data class representing configuration settings for a specific world.
     * Contains all configurable limits and settings that can be overridden per world.
     */
    public static class WorldConfig {
        private final String worldName;
        private final FileConfiguration config;
        private final boolean isCustomConfig;

        // Cached values for performance
        private final Map<String, Object> cachedValues = new ConcurrentHashMap<>();

        public WorldConfig(String worldName, FileConfiguration config, boolean isCustomConfig) {
            this.worldName = worldName;
            this.config = config;
            this.isCustomConfig = isCustomConfig;
        }

        public String getWorldName() { return worldName; }
        public FileConfiguration getConfig() { return config; }
        public boolean isCustomConfig() { return isCustomConfig; }

        /**
         * Gets a configuration value, using cache for performance.
         * Falls back to global configuration if not set in world config.
         */
        @SuppressWarnings("unchecked")
        public <T> T getValue(String path, T globalDefault, Class<T> type) {
            String cacheKey = path + "_" + type.getSimpleName();

            if (cachedValues.containsKey(cacheKey)) {
                return (T) cachedValues.get(cacheKey);
            }

            T value;
            if (isCustomConfig && config.contains(path)) {
                // Use world-specific value
                if (type == Integer.class) {
                    value = (T) Integer.valueOf(config.getInt(path));
                } else if (type == Double.class) {
                    value = (T) Double.valueOf(config.getDouble(path));
                } else if (type == Boolean.class) {
                    value = (T) Boolean.valueOf(config.getBoolean(path));
                } else if (type == String.class) {
                    value = (T) config.getString(path);
                } else if (type == List.class) {
                    value = (T) config.getList(path);
                } else {
                    value = globalDefault;
                }
            } else {
                // Use global default
                value = globalDefault;
            }

            cachedValues.put(cacheKey, value);
            return value;
        }

        /**
         * Clears the cache to force reloading of values.
         */
        public void clearCache() {
            cachedValues.clear();
        }
    }

    /**
     * Initializes the per-world configuration system.
     * Creates the worlds folder and loads existing world configurations.
     */
    public static void initialize() {
        perWorldEnabled = ConfigManager.isPerWorldSettingsEnabled();

        if (!perWorldEnabled) {
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info("[WorldConfigManager] Per-world settings disabled, using global configuration only.");
            }
            return;
        }

        worldsConfigFolder = new File(LagXpert.getInstance().getDataFolder(), WORLDS_FOLDER);

        if (!worldsConfigFolder.exists()) {
            if (!worldsConfigFolder.mkdirs()) {
                LagXpert.getInstance().getLogger().severe("[WorldConfigManager] Failed to create worlds config folder: " + worldsConfigFolder.getPath());
                perWorldEnabled = false;
                return;
            }
        }

        loadAllWorldConfigs();
        createDefaultWorldConfigs();

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info("[WorldConfigManager] Per-world configuration system initialized. Loaded " + worldConfigs.size() + " world configs.");
        }
    }

    /**
     * Loads all existing world configuration files from the worlds folder.
     */
    private static void loadAllWorldConfigs() {
        File[] configFiles = worldsConfigFolder.listFiles((dir, name) -> name.endsWith(".yml"));

        if (configFiles != null) {
            for (File configFile : configFiles) {
                String worldName = configFile.getName().replace(".yml", "");
                loadWorldConfig(worldName);
            }
        }
    }

    /**
     * Creates default configuration files for standard worlds if they don't exist.
     */
    private static void createDefaultWorldConfigs() {
        // Create default configs for standard worlds
        String[] defaultWorlds = {"world", "world_nether", "world_the_end"};

        for (String worldName : defaultWorlds) {
            if (!hasWorldConfig(worldName)) {
                createDefaultWorldConfig(worldName);
            }
        }
    }

    /**
     * Creates a default configuration file for a specific world with commented examples.
     */
    private static void createDefaultWorldConfig(String worldName) {
        File configFile = new File(worldsConfigFolder, worldName + ".yml");

        try {
            YamlConfiguration config = new YamlConfiguration();

            // Add header comments
            config.options().header(generateWorldConfigHeader(worldName));

            // Add example configurations based on world type
            if (worldName.contains("nether")) {
                addNetherDefaults(config);
            } else if (worldName.contains("end")) {
                addEndDefaults(config);
            } else {
                addOverworldDefaults(config);
            }

            config.save(configFile);

            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info("[WorldConfigManager] Created default config for world: " + worldName);
            }

        } catch (IOException e) {
            LagXpert.getInstance().getLogger().warning("[WorldConfigManager] Failed to create default config for world " + worldName + ": " + e.getMessage());
        }
    }

    /**
     * Generates header comments for world configuration files.
     */
    private static String generateWorldConfigHeader(String worldName) {
        return "# LagXpert - Per-World Configuration for: " + worldName + "\n" +
                "# This file allows you to override global settings for this specific world.\n" +
                "# Only include settings you want to override - missing settings will use global defaults.\n" +
                "# You can copy any setting from the main config files (mobs.yml, storage.yml, etc.) here.\n" +
                "# \n" +
                "# Example overrides:\n" +
                "# limits:\n" +
                "#   mobs-per-chunk: 30        # Override global mob limit\n" +
                "#   hoppers-per-chunk: 5      # Override global hopper limit\n" +
                "# \n" +
                "# monitoring:\n" +
                "#   tps:\n" +
                "#     alert-thresholds:\n" +
                "#       warning: 16.0         # Different TPS warning for this world\n" +
                "# \n" +
                "# Remove or comment out sections you don't want to override.\n";
    }

    /**
     * Adds default overrides for nether worlds.
     */
    private static void addNetherDefaults(YamlConfiguration config) {
        // Lower limits for nether due to hostile environment
        config.set("limits.mobs-per-chunk", 25);
        config.set("limits.hoppers-per-chunk", 6);
        config.set("limits.tnt-per-chunk", 0); // No TNT in nether by default

        // More aggressive entity cleanup in nether
        config.set("entity-cleanup.cleanup-targets.abandoned-vehicles", true);
        config.set("entity-cleanup.advanced.max-entities-per-chunk", 150);

        // Different TPS thresholds for nether
        config.set("monitoring.tps.alert-thresholds.warning", 17.0);
        config.set("monitoring.tps.alert-thresholds.critical", 14.0);
    }

    /**
     * Adds default overrides for end worlds.
     */
    private static void addEndDefaults(YamlConfiguration config) {
        // Very restrictive limits for the end
        config.set("limits.mobs-per-chunk", 20);
        config.set("limits.hoppers-per-chunk", 4);
        config.set("limits.chests-per-chunk", 10);

        // Disable certain features in the end
        config.set("chunk-management.preload.enabled", false);
        config.set("item-cleaner.warning.enabled", false); // No warnings in end

        // Stricter performance monitoring
        config.set("monitoring.tps.alert-thresholds.warning", 18.5);
        config.set("monitoring.tps.alert-thresholds.critical", 16.0);
    }

    /**
     * Adds default overrides for overworld.
     */
    private static void addOverworldDefaults(YamlConfiguration config) {
        // Standard overworld typically uses global settings
        // Add only a few examples
        config.set("# Example overrides for overworld", null);
        config.set("# limits.mobs-per-chunk", 40);
        config.set("# limits.hoppers-per-chunk", 8);
        config.set("# Remove the # to activate these overrides", null);
    }

    /**
     * Loads a specific world configuration file.
     */
    private static void loadWorldConfig(String worldName) {
        File configFile = new File(worldsConfigFolder, worldName + ".yml");

        if (!configFile.exists()) {
            // Create a placeholder config that will inherit all global settings
            WorldConfig worldConfig = new WorldConfig(worldName, new YamlConfiguration(), false);
            worldConfigs.put(worldName.toLowerCase(), worldConfig);
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            WorldConfig worldConfig = new WorldConfig(worldName, config, true);
            worldConfigs.put(worldName.toLowerCase(), worldConfig);

            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info("[WorldConfigManager] Loaded world config: " + worldName);
            }

        } catch (Exception e) {
            LagXpert.getInstance().getLogger().warning("[WorldConfigManager] Failed to load world config " + worldName + ": " + e.getMessage());

            // Create fallback config
            WorldConfig worldConfig = new WorldConfig(worldName, new YamlConfiguration(), false);
            worldConfigs.put(worldName.toLowerCase(), worldConfig);
        }
    }

    /**
     * Gets the configuration for a specific world.
     */
    public static WorldConfig getWorldConfig(World world) {
        return getWorldConfig(world.getName());
    }

    /**
     * Gets the configuration for a specific world by name.
     */
    public static WorldConfig getWorldConfig(String worldName) {
        if (!perWorldEnabled) {
            return null; // Use global config
        }

        WorldConfig config = worldConfigs.get(worldName.toLowerCase());

        if (config == null) {
            // Create and cache a new config for this world
            config = new WorldConfig(worldName, new YamlConfiguration(), false);
            worldConfigs.put(worldName.toLowerCase(), config);
        }

        return config;
    }

    /**
     * Checks if a world has a custom configuration file.
     */
    public static boolean hasWorldConfig(String worldName) {
        File configFile = new File(worldsConfigFolder, worldName + ".yml");
        return configFile.exists();
    }

    /**
     * Gets a world-specific value with fallback to global configuration.
     */
    public static <T> T getWorldValue(World world, String configPath, T globalValue, Class<T> type) {
        if (!perWorldEnabled) {
            return globalValue;
        }

        WorldConfig worldConfig = getWorldConfig(world);
        if (worldConfig == null) {
            return globalValue;
        }

        return worldConfig.getValue(configPath, globalValue, type);
    }

    /**
     * Convenience methods for common configuration values.
     */

    // Mob limits
    public static int getMobsPerChunk(World world) {
        return getWorldValue(world, "limits.mobs-per-chunk", ConfigManager.getMaxMobsPerChunk(), Integer.class);
    }

    // Storage limits
    public static int getHoppersPerChunk(World world) {
        return getWorldValue(world, "limits.hoppers-per-chunk", ConfigManager.getMaxHoppersPerChunk(), Integer.class);
    }

    public static int getChestsPerChunk(World world) {
        return getWorldValue(world, "limits.chests-per-chunk", ConfigManager.getMaxChestsPerChunk(), Integer.class);
    }

    public static int getFurnacesPerChunk(World world) {
        return getWorldValue(world, "limits.furnaces-per-chunk", ConfigManager.getMaxFurnacesPerChunk(), Integer.class);
    }

    public static int getBlastFurnacesPerChunk(World world) {
        return getWorldValue(world, "limits.blast_furnaces-per-chunk", ConfigManager.getMaxBlastFurnacesPerChunk(), Integer.class);
    }

    public static int getSmokersPerChunk(World world) {
        return getWorldValue(world, "limits.smokers-per-chunk", ConfigManager.getMaxSmokersPerChunk(), Integer.class);
    }

    public static int getBarrelsPerChunk(World world) {
        return getWorldValue(world, "limits.barrels-per-chunk", ConfigManager.getMaxBarrelsPerChunk(), Integer.class);
    }

    public static int getDroppersPerChunk(World world) {
        return getWorldValue(world, "limits.droppers-per-chunk", ConfigManager.getMaxDroppersPerChunk(), Integer.class);
    }

    public static int getDispensersPerChunk(World world) {
        return getWorldValue(world, "limits.dispensers-per-chunk", ConfigManager.getMaxDispensersPerChunk(), Integer.class);
    }

    public static int getShulkerBoxesPerChunk(World world) {
        return getWorldValue(world, "limits.shulker_boxes-per-chunk", ConfigManager.getMaxShulkerBoxesPerChunk(), Integer.class);
    }

    public static int getTntPerChunk(World world) {
        return getWorldValue(world, "limits.tnt-per-chunk", ConfigManager.getMaxTntPerChunk(), Integer.class);
    }

    public static int getPistonsPerChunk(World world) {
        return getWorldValue(world, "limits.pistons-per-chunk", ConfigManager.getMaxPistonsPerChunk(), Integer.class);
    }

    public static int getObserversPerChunk(World world) {
        return getWorldValue(world, "limits.observers-per-chunk", ConfigManager.getMaxObserversPerChunk(), Integer.class);
    }

    // TPS monitoring thresholds
    public static double getTPSWarningThreshold(World world) {
        return getWorldValue(world, "monitoring.tps.alert-thresholds.warning", ConfigManager.getTPSWarningThreshold(), Double.class);
    }

    public static double getTPSCriticalThreshold(World world) {
        return getWorldValue(world, "monitoring.tps.alert-thresholds.critical", ConfigManager.getTPSCriticalThreshold(), Double.class);
    }

    // Chunk management settings
    public static int getChunkInactivityThreshold(World world) {
        return getWorldValue(world, "chunk-management.auto-unload.inactivity-threshold-minutes", ConfigManager.getChunkInactivityThresholdMinutes(), Integer.class);
    }

    public static boolean isChunkPreloadEnabled(World world) {
        return getWorldValue(world, "chunk-management.preload.enabled", ConfigManager.isChunkPreloadEnabled(), Boolean.class);
    }

    /**
     * Reloads all world configurations.
     */
    public static void reloadAll() {
        // Clear existing configs
        for (WorldConfig config : worldConfigs.values()) {
            config.clearCache();
        }
        worldConfigs.clear();

        // Reinitialize
        initialize();
    }

    /**
     * Gets a list of all configured worlds.
     */
    public static List<String> getConfiguredWorlds() {
        if (!perWorldEnabled) {
            return Collections.emptyList();
        }
        return new ArrayList<>(worldConfigs.keySet());
    }

    /**
     * Gets statistics about the per-world configuration system.
     */
    public static Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("per_world_enabled", perWorldEnabled);
        stats.put("total_world_configs", worldConfigs.size());
        stats.put("custom_configs", worldConfigs.values().stream().mapToInt(config -> config.isCustomConfig() ? 1 : 0).sum());
        stats.put("configured_worlds", getConfiguredWorlds());

        return stats;
    }

    /**
     * Checks if per-world configuration is enabled.
     */
    public static boolean isPerWorldEnabled() {
        return perWorldEnabled;
    }

    /**
     * Creates a new world configuration file with default settings.
     */
    public static boolean createWorldConfig(String worldName) {
        if (!perWorldEnabled) {
            return false;
        }

        File configFile = new File(worldsConfigFolder, worldName + ".yml");
        if (configFile.exists()) {
            return false; // Already exists
        }

        createDefaultWorldConfig(worldName);
        loadWorldConfig(worldName);

        return true;
    }

    /**
     * Deletes a world configuration file.
     */
    public static boolean deleteWorldConfig(String worldName) {
        if (!perWorldEnabled) {
            return false;
        }

        File configFile = new File(worldsConfigFolder, worldName + ".yml");
        if (!configFile.exists()) {
            return false; // Doesn't exist
        }

        boolean deleted = configFile.delete();
        if (deleted) {
            worldConfigs.remove(worldName.toLowerCase());
        }

        return deleted;
    }
}