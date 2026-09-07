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
    
    /**
     * Configuration schema version this build understands.
     *
     * Must be kept in step with the {@code config-version} shipped in
     * config.yml. Leaving it stale is what caused this class to report
     * "migrating from v2.7 to v2.2" and to create a fresh backup folder on
     * every single server start.
     */
    private static final String CURRENT_VERSION = "2.7";

    private static final String VERSION_KEY = "config-version";

    /** Prefix used for the backup folders this class creates. */
    static final String BACKUP_PREFIX = "config-backup-v";
    
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

            int comparison = compareVersions(currentConfigVersion, CURRENT_VERSION);

            if (comparison > 0) {
                // The config was written by a NEWER build than this one. Migrating
                // would be a downgrade, so refuse and leave everything untouched.
                LagXpert.getInstance().getLogger().warning(
                        "[ConfigMigrator] Configuration is version v" + currentConfigVersion +
                                " but this build expects v" + CURRENT_VERSION +
                                ". Refusing to downgrade; no changes made. " +
                                "If you rolled the plugin back, restore your configuration backup.");
                return;
            }

            if (comparison == 0) {
                // Already current. Nothing to do, and crucially no backup created.
                return;
            }

            // Only older configurations reach this point.
            if (!hasMigrationPathFrom(currentConfigVersion)) {
                // Nothing this class knows how to transform. Stamp the version so the
                // check does not repeat forever, but do not touch anything else and do
                // not create a backup for a no-op.
                LagXpert.getInstance().getLogger().info(
                        "[ConfigMigrator] Configuration v" + currentConfigVersion +
                                " needs no structural changes for v" + CURRENT_VERSION +
                                "; new keys will use their defaults.");
                stampVersion();
                return;
            }

            LagXpert.getInstance().getLogger().info("[ConfigMigrator] Migrating configurations from v" + currentConfigVersion + " to v" + CURRENT_VERSION);

            // Create backup only when a real migration is about to run.
            createConfigBackup(currentConfigVersion);

            if (isVersion21x(currentConfigVersion)) {
                migrateFrom21xTo22x();
            }

            // Always finish on the current schema version, whatever path ran.
            stampVersion();

            LagXpert.getInstance().getLogger().info("[ConfigMigrator] Configuration migration completed successfully!");

        } catch (Exception e) {
            LagXpert.getInstance().getLogger().severe("[ConfigMigrator] Failed to migrate configurations: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Compares two dotted version strings numerically.
     *
     * Replaces the previous string-inequality check, which treated any version
     * that was not literally equal to the target as needing migration. That made
     * a newer configuration look like an older one and triggered a bogus
     * "migration" plus a backup folder on every startup.
     *
     * Missing components are treated as zero, so {@code 2.7} and {@code 2.7.0}
     * compare equal. Non-numeric components are treated as zero rather than
     * throwing, because a hand-edited version string must not break startup.
     *
     * @return negative if {@code a} is older, zero if equal, positive if newer
     */
    static int compareVersions(String a, String b) {
        if (a == null) a = "0";
        if (b == null) b = "0";

        String[] left = a.trim().split("\\.");
        String[] right = b.trim().split("\\.");
        int length = Math.max(left.length, right.length);

        for (int i = 0; i < length; i++) {
            int l = parseComponent(i < left.length ? left[i] : "0");
            int r = parseComponent(i < right.length ? right[i] : "0");
            if (l != r) {
                return l < r ? -1 : 1;
            }
        }
        return 0;
    }

    private static int parseComponent(String raw) {
        try {
            // Tolerate suffixes such as "2.7-SNAPSHOT" by keeping leading digits only.
            StringBuilder digits = new StringBuilder();
            for (char c : raw.trim().toCharArray()) {
                if (Character.isDigit(c)) {
                    digits.append(c);
                } else {
                    break;
                }
            }
            return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Returns true when this class has an actual transformation to apply for the
     * given older configuration version.
     *
     * Versions newer than the last structural change need no transformation:
     * Bukkit supplies defaults for keys that are simply absent, so the only work
     * required is stamping the version forward.
     */
    private static boolean hasMigrationPathFrom(String version) {
        return isVersion21x(version);
    }

    /**
     * Checks if current version is from the 2.1.x series or older.
     *
     * Null-tolerant: an operator who empties the {@code config-version:} line by
     * hand must not cause a startup failure.
     */
    private static boolean isVersion21x(String version) {
        if (version == null) {
            return false;
        }
        String trimmed = version.trim();
        return trimmed.startsWith("2.1") || trimmed.equals("2.0") || trimmed.startsWith("1.");
    }

    /**
     * Writes the current schema version into config.yml without touching
     * anything else, so the migration check does not repeat on every startup.
     */
    private static void stampVersion() {
        try {
            File configFile = new File(LagXpert.getInstance().getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                return;
            }
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            config.set(VERSION_KEY, CURRENT_VERSION);
            config.save(configFile);
        } catch (IOException e) {
            LagXpert.getInstance().getLogger().warning(
                    "[ConfigMigrator] Could not update " + VERSION_KEY + " in config.yml: " + e.getMessage());
        }
    }

    /**
     * Counts the backup folders this class has previously created.
     *
     * Used to warn operators who accumulated backups while the stale version
     * constant was creating one on every restart. Nothing is deleted
     * automatically; removing a backup is the operator's decision.
     */
    private static int countExistingBackups() {
        try {
            File dataFolder = LagXpert.getInstance().getDataFolder();
            File[] entries = dataFolder.listFiles(
                    (dir, name) -> name.startsWith(BACKUP_PREFIX) && new File(dir, name).isDirectory());
            return entries == null ? 0 : entries.length;
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Creates a backup of all configuration files before migration.
     */
    private static void createConfigBackup(String currentVersion) {
        try {
            // Warn if backups have piled up. Earlier builds created one on every
            // startup because of the stale version constant.
            int existing = countExistingBackups();
            if (existing >= 5) {
                LagXpert.getInstance().getLogger().warning(
                        "[ConfigMigrator] " + existing + " configuration backup folder(s) already exist in " +
                                "the LagXpert data folder. They are safe to delete once you no longer need them.");
            }

            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String backupFolder = BACKUP_PREFIX + currentVersion + "_" + timestamp;
            
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
     * Checks if configurations are at or beyond the expected schema version.
     */
    public static boolean isConfigUpToDate() {
        return compareVersions(getCurrentConfigVersion(), CURRENT_VERSION) >= 0;
    }

    /**
     * Gets migration status information.
     */
    public static Map<String, Object> getMigrationInfo() {
        String configVersion = getCurrentConfigVersion();
        int comparison = compareVersions(configVersion, CURRENT_VERSION);

        Map<String, Object> info = new HashMap<>();
        info.put("current_version", CURRENT_VERSION);
        info.put("config_version", configVersion);
        info.put("is_up_to_date", comparison >= 0);
        info.put("needs_migration", comparison < 0);
        info.put("is_newer_than_plugin", comparison > 0);
        info.put("existing_backups", countExistingBackups());
        return info;
    }
}