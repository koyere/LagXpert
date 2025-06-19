package me.koyere.lagxpert.config;

import me.koyere.lagxpert.LagXpert;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles automatic migration of configuration files from older versions
 * to maintain compatibility while adding new features.
 * 
 * Supports migration from:
 * - v2.1.x to v2.2 (adds multi-platform and smart mob management)
 * - Future version migrations can be added here
 * 
 * Performance optimizations:
 * - Only migrates when necessary (version check)
 * - Creates backup before migration
 * - Preserves user customizations
 * - Adds only missing keys, doesn't overwrite existing ones
 */
public class ConfigMigrator {
    
    private static final String CURRENT_VERSION = "2.2";
    private static final String VERSION_KEY = "config-version";
    
    /**
     * Performs automatic migration of all configuration files if needed.
     * Called during plugin initialization.
     */
    public static void migrateConfigurations() {
        try {
            File configFile = new File(LagXpert.getInstance().getDataFolder(), "config.yml");
            
            if (!configFile.exists()) {
                // Fresh installation, no migration needed
                return;
            }
            
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String currentConfigVersion = config.getString(VERSION_KEY, "2.1.1");
            
            if (needsMigration(currentConfigVersion)) {
                LagXpert.getInstance().getLogger().info("[ConfigMigrator] Migrating configurations from v" + currentConfigVersion + " to v" + CURRENT_VERSION);
                
                // Create backup
                createConfigBackup(currentConfigVersion);
                
                // Perform migration based on current version
                if (isVersion21x(currentConfigVersion)) {
                    migrateFrom21xTo22x();
                }
                
                LagXpert.getInstance().getLogger().info("[ConfigMigrator] Configuration migration completed successfully!");
            }
            
        } catch (Exception e) {
            LagXpert.getInstance().getLogger().severe("[ConfigMigrator] Failed to migrate configurations: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Checks if migration is needed based on version comparison.
     */
    private static boolean needsMigration(String currentVersion) {
        return !CURRENT_VERSION.equals(currentVersion);
    }
    
    /**
     * Checks if current version is from 2.1.x series.
     */
    private static boolean isVersion21x(String version) {
        return version.startsWith("2.1") || version.equals("2.0") || version.startsWith("1.");
    }
    
    /**
     * Creates a backup of all configuration files before migration.
     */
    private static void createConfigBackup(String currentVersion) {
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String backupFolder = "config-backup-v" + currentVersion + "_" + timestamp;
            
            File backupDir = new File(LagXpert.getInstance().getDataFolder(), backupFolder);
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            
            // List of config files to backup
            String[] configFiles = {
                "config.yml", "mobs.yml", "storage.yml", "redstone.yml", 
                "alerts.yml", "task.yml", "messages.yml", "itemcleaner.yml",
                "entitycleanup.yml", "monitoring.yml", "chunks.yml"
            };
            
            for (String configFile : configFiles) {
                File sourceFile = new File(LagXpert.getInstance().getDataFolder(), configFile);
                if (sourceFile.exists()) {
                    File backupFile = new File(backupDir, configFile);
                    copyFile(sourceFile, backupFile);
                }
            }
            
            // Backup worlds folder if it exists
            File worldsFolder = new File(LagXpert.getInstance().getDataFolder(), "worlds");
            if (worldsFolder.exists()) {
                File worldsBackup = new File(backupDir, "worlds");
                copyDirectory(worldsFolder, worldsBackup);
            }
            
            LagXpert.getInstance().getLogger().info("[ConfigMigrator] Configuration backup created: " + backupFolder);
            
        } catch (Exception e) {
            LagXpert.getInstance().getLogger().warning("[ConfigMigrator] Failed to create backup: " + e.getMessage());
        }
    }
    
    /**
     * Migrates configurations from version 2.1.x to 2.2.
     * Adds new configuration sections while preserving existing settings.
     */
    private static void migrateFrom21xTo22x() throws IOException {
        try {
            // Migrate main config.yml
            migrateMainConfig();
            
            // Migrate mobs.yml for smart mob management
            migrateMobsConfig();
            
            // Create/update new configuration sections
            addPlatformDetectionConfig();
            addBedrockCompatibilityConfig();
            addSmartMobManagementConfig();
            
            LagXpert.getInstance().getLogger().info("[ConfigMigrator] v2.1.x → v2.2 migration completed");
            
        } catch (IOException e) {
            LagXpert.getInstance().getLogger().severe("[ConfigMigrator] Migration from v2.1.x failed: " + e.getMessage());
            // Re-throw as RuntimeException to avoid method signature changes
            throw new RuntimeException("Configuration migration failed", e);
        } catch (Exception e) {
            LagXpert.getInstance().getLogger().severe("[ConfigMigrator] Migration from v2.1.x failed: " + e.getMessage());
            throw new RuntimeException("Configuration migration failed", e);
        }
    }
    
    /**
     * Migrates the main config.yml file.
     */
    private static void migrateMainConfig() throws IOException {
        File configFile = new File(LagXpert.getInstance().getDataFolder(), "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        // Add new module toggles
        if (!config.contains("modules.auto-mob-removal")) {
            config.set("modules.auto-mob-removal", true);
        }
        
        if (!config.contains("modules.bedrock-compatibility")) {
            config.set("modules.bedrock-compatibility", true);
        }
        
        if (!config.contains("modules.platform-detection")) {
            config.set("modules.platform-detection", true);
        }
        
        // Update version
        config.set(VERSION_KEY, CURRENT_VERSION);
        
        config.save(configFile);
    }
    
    /**
     * Migrates mobs.yml to include smart mob management settings.
     */
    private static void migrateMobsConfig() throws IOException {
        File mobsFile = new File(LagXpert.getInstance().getDataFolder(), "mobs.yml");
        FileConfiguration mobsConfig = YamlConfiguration.loadConfiguration(mobsFile);
        
        // Add smart mob management section
        if (!mobsConfig.contains("smart-management")) {
            mobsConfig.set("smart-management.enabled", true);
            mobsConfig.set("smart-management.scan-interval-ticks", 200);
            mobsConfig.set("smart-management.max-mobs-per-tick-removal", 10);
            mobsConfig.set("smart-management.chunk-processing-cooldown-seconds", 30);
            
            // Protection settings
            mobsConfig.set("smart-management.protection.named-mobs", true);
            mobsConfig.set("smart-management.protection.tamed-animals", true);
            mobsConfig.set("smart-management.protection.leashed-entities", true);
            mobsConfig.set("smart-management.protection.equipped-mobs", true);
            mobsConfig.set("smart-management.protection.plugin-entities", true);
            mobsConfig.set("smart-management.protection.villagers-with-trades", true);
            
            // Protected entity types
            mobsConfig.set("smart-management.protected-types", java.util.Arrays.asList(
                "WITHER", "ENDER_DRAGON", "VILLAGER", "IRON_GOLEM"
            ));
            
            // Notification settings
            mobsConfig.set("smart-management.notifications.enabled", true);
            mobsConfig.set("smart-management.notifications.message", 
                "&e[LagXpert] &7Removed &c{removed} &7excess mobs. &8({original} → {remaining}, limit: {limit})");
            
            // World settings
            mobsConfig.set("smart-management.enabled-worlds", java.util.Arrays.asList("all"));
        }
        
        mobsConfig.save(mobsFile);
    }
    
    /**
     * Adds platform detection configuration.
     */
    private static void addPlatformDetectionConfig() throws IOException {
        File configFile = new File(LagXpert.getInstance().getDataFolder(), "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        if (!config.contains("platform-detection")) {
            config.set("platform-detection.auto-detect", true);
            config.set("platform-detection.force-bukkit-scheduler", false);
            config.set("platform-detection.debug-platform-info", false);
            
            // Folia-specific settings
            config.set("platform-detection.folia.use-region-scheduler", true);
            config.set("platform-detection.folia.use-async-scheduler", true);
            config.set("platform-detection.folia.optimize-chunk-operations", true);
        }
        
        config.save(configFile);
    }
    
    /**
     * Adds Bedrock compatibility configuration.
     */
    private static void addBedrockCompatibilityConfig() throws IOException {
        File configFile = new File(LagXpert.getInstance().getDataFolder(), "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        if (!config.contains("bedrock-compatibility")) {
            config.set("bedrock-compatibility.enabled", true);
            config.set("bedrock-compatibility.auto-detect-players", true);
            config.set("bedrock-compatibility.cache-player-platform", true);
            
            // GUI optimizations
            config.set("bedrock-compatibility.gui.optimize-for-bedrock", true);
            config.set("bedrock-compatibility.gui.max-inventory-size", 36);
            config.set("bedrock-compatibility.gui.simplify-item-data", true);
            config.set("bedrock-compatibility.gui.fallback-to-chat", true);
            config.set("bedrock-compatibility.gui.bedrock-safe-materials", true);
            
            // Geyser/Floodgate integration
            config.set("bedrock-compatibility.integrations.geyser", true);
            config.set("bedrock-compatibility.integrations.floodgate", true);
            config.set("bedrock-compatibility.integrations.auto-detect-plugins", true);
        }
        
        config.save(configFile);
    }
    
    /**
     * Adds smart mob management configuration section.
     */
    private static void addSmartMobManagementConfig() throws IOException {
        File configFile = new File(LagXpert.getInstance().getDataFolder(), "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        if (!config.contains("smart-mob-management")) {
            config.set("smart-mob-management.enabled", true);
            config.set("smart-mob-management.performance.max-chunks-per-tick", 5);
            config.set("smart-mob-management.performance.max-removals-per-chunk-per-tick", 10);
            config.set("smart-mob-management.performance.chunk-cooldown-seconds", 30);
            
            // Priority system
            config.set("smart-mob-management.priority.farm-animals", 1);
            config.set("smart-mob-management.priority.hostile-mobs", 2);
            config.set("smart-mob-management.priority.neutral-mobs", 3);
            config.set("smart-mob-management.priority.valuable-entities", 5);
            config.set("smart-mob-management.priority.boss-entities", 10);
            
            // Debug settings
            config.set("smart-mob-management.debug.log-removals", false);
            config.set("smart-mob-management.debug.log-protections", false);
            config.set("smart-mob-management.debug.log-performance", false);
        }
        
        config.save(configFile);
    }
    
    /**
     * Helper method to copy a file.
     */
    private static void copyFile(File source, File destination) throws IOException {
        if (!destination.getParentFile().exists()) {
            destination.getParentFile().mkdirs();
        }
        
        java.nio.file.Files.copy(source.toPath(), destination.toPath(), 
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    
    /**
     * Helper method to copy a directory recursively.
     */
    private static void copyDirectory(File source, File destination) throws IOException {
        if (!destination.exists()) {
            destination.mkdirs();
        }
        
        File[] files = source.listFiles();
        if (files != null) {
            for (File file : files) {
                File destFile = new File(destination, file.getName());
                if (file.isDirectory()) {
                    copyDirectory(file, destFile);
                } else {
                    copyFile(file, destFile);
                }
            }
        }
    }
    
    /**
     * Gets the current configuration version.
     */
    public static String getCurrentConfigVersion() {
        try {
            File configFile = new File(LagXpert.getInstance().getDataFolder(), "config.yml");
            if (configFile.exists()) {
                FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                return config.getString(VERSION_KEY, "2.1.1");
            }
        } catch (Exception e) {
            LagXpert.getInstance().getLogger().warning("[ConfigMigrator] Could not read config version: " + e.getMessage());
        }
        return "unknown";
    }
    
    /**
     * Checks if configurations are up to date.
     */
    public static boolean isConfigUpToDate() {
        return CURRENT_VERSION.equals(getCurrentConfigVersion());
    }
    
    /**
     * Gets migration status information.
     */
    public static Map<String, Object> getMigrationInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("current_version", CURRENT_VERSION);
        info.put("config_version", getCurrentConfigVersion());
        info.put("is_up_to_date", isConfigUpToDate());
        info.put("needs_migration", needsMigration(getCurrentConfigVersion()));
        return info;
    }
}