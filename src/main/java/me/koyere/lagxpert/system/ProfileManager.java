package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.SchedulerWrapper;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies named optimization profiles from profiles.yml.
 *
 * A profile is a small set of config overrides that let an operator switch the
 * whole plugin between, say, "relaxed" and "performance" with one command
 * instead of editing six YAML files. Before this class existed, profiles.yml was
 * generated on disk and documented in the changelog but had no code behind it at
 * all.
 *
 * Design decisions worth knowing:
 *
 * <ul>
 *   <li><b>Profile keys are abstract.</b> profiles.yml uses friendly names like
 *       {@code limits.mobs-per-chunk}, which do not map one-to-one onto the real
 *       config layout. {@link #KEY_MAPPINGS} is the translation table, so the
 *       profile file stays readable while writes land in the correct file.</li>
 *   <li><b>Applying a profile snapshots what it replaced.</b> The previous values
 *       are captured before the first write, so {@code revert} restores the exact
 *       prior configuration rather than guessing at a default profile.</li>
 *   <li><b>Auto-revert is a safety net, not a scheduler.</b> If an operator
 *       applies an aggressive profile and forgets, the snapshot is restored after
 *       {@code safety.auto-revert-minutes}.</li>
 * </ul>
 */
public class ProfileManager {

    private static ProfileManager instance;

    /** Maps a profile key to the real config file and YAML path it controls. */
    private static final class ConfigTarget {
        final String fileName;
        final String path;

        ConfigTarget(String fileName, String path) {
            this.fileName = fileName;
            this.path = path;
        }
    }

    /**
     * Translation table from the friendly keys used in profiles.yml to the actual
     * config file and path each one writes to.
     *
     * Any profile key not present here is reported to the operator as unknown
     * rather than being silently dropped, which is how a profile file can look
     * like it is working while doing nothing.
     */
    private static final Map<String, ConfigTarget> KEY_MAPPINGS = new LinkedHashMap<>();

    static {
        // Per-chunk limits
        KEY_MAPPINGS.put("limits.mobs-per-chunk", new ConfigTarget("mobs.yml", "limits.mobs-per-chunk"));
        KEY_MAPPINGS.put("limits.hoppers-per-chunk", new ConfigTarget("storage.yml", "limits.hoppers-per-chunk"));
        KEY_MAPPINGS.put("limits.chests-per-chunk", new ConfigTarget("storage.yml", "limits.chests-per-chunk"));
        KEY_MAPPINGS.put("limits.furnaces-per-chunk", new ConfigTarget("storage.yml", "limits.furnaces-per-chunk"));
        KEY_MAPPINGS.put("limits.barrels-per-chunk", new ConfigTarget("storage.yml", "limits.barrels-per-chunk"));
        KEY_MAPPINGS.put("limits.droppers-per-chunk", new ConfigTarget("storage.yml", "limits.droppers-per-chunk"));
        KEY_MAPPINGS.put("limits.dispensers-per-chunk", new ConfigTarget("storage.yml", "limits.dispensers-per-chunk"));
        KEY_MAPPINGS.put("limits.shulker_boxes-per-chunk", new ConfigTarget("storage.yml", "limits.shulker_boxes-per-chunk"));
        KEY_MAPPINGS.put("limits.tnt-per-chunk", new ConfigTarget("storage.yml", "limits.tnt-per-chunk"));
        KEY_MAPPINGS.put("limits.pistons-per-chunk", new ConfigTarget("storage.yml", "limits.pistons-per-chunk"));
        KEY_MAPPINGS.put("limits.observers-per-chunk", new ConfigTarget("storage.yml", "limits.observers-per-chunk"));

        // Item cleaner
        KEY_MAPPINGS.put("item-cleaner.interval-ticks", new ConfigTarget("itemcleaner.yml", "item-cleaner.interval-ticks"));

        // Chunk management
        KEY_MAPPINGS.put("chunks.unload-inactivity-minutes",
                new ConfigTarget("chunks.yml", "chunk-management.auto-unload.inactivity-threshold-minutes"));
        KEY_MAPPINGS.put("chunks.preload-radius",
                new ConfigTarget("chunks.yml", "chunk-management.preload.preload-radius"));

        // Mob AI optimizer
        KEY_MAPPINGS.put("mobs.ai-optimizer-enabled", new ConfigTarget("mobs.yml", "ai-optimizer.enabled"));
        KEY_MAPPINGS.put("mobs.ai-distance-threshold",
                new ConfigTarget("mobs.yml", "ai-optimizer.distance-optimization.distance-threshold"));

        // Adaptive thresholds
        KEY_MAPPINGS.put("adaptive-thresholds.enabled", new ConfigTarget("config.yml", "adaptive-thresholds.enabled"));

        // Redstone
        KEY_MAPPINGS.put("redstone.active-ticks",
                new ConfigTarget("redstone.yml", "control.redstone-active-ticks"));
        KEY_MAPPINGS.put("redstone.clock-max-duration-ms",
                new ConfigTarget("redstone.yml", "circuit-tracker.max-duration.clock-circuit"));
        KEY_MAPPINGS.put("redstone.clock-grace-period-ms",
                new ConfigTarget("redstone.yml", "circuit-tracker.grace-periods.clock-circuit"));

        // Entity cleanup
        KEY_MAPPINGS.put("entity-cleanup.max-entities-per-chunk",
                new ConfigTarget("entitycleanup.yml", "entity-cleanup.advanced.max-entities-per-chunk"));
    }

    /** Profile definitions loaded from profiles.yml, keyed by lower-case name. */
    private final Map<String, ConfigurationSection> profiles = new ConcurrentHashMap<>();

    /** Descriptions per profile name, for command and GUI display. */
    private final Map<String, String> descriptions = new ConcurrentHashMap<>();

    /** The values replaced by the currently active profile, for exact revert. */
    private final Map<String, Object> snapshot = new ConcurrentHashMap<>();

    /** Marker for snapshot entries whose original key was absent from the file. */
    private static final Object ABSENT = new Object();

    private volatile String activeProfile = null;
    private volatile long activeSince = 0L;
    private volatile String appliedBy = null;

    private int autoRevertMinutes = 10;
    private String requiredPermission = "lagxpert.admin.profile";

    private BukkitTask autoRevertTask = null;

    private ProfileManager() {
        loadConfig();
    }

    public static ProfileManager getInstance() {
        if (instance == null) {
            instance = new ProfileManager();
        }
        return instance;
    }

    /**
     * Loads profile definitions and safety settings from profiles.yml.
     *
     * Deliberately does not touch the currently applied profile, so a reload
     * cannot silently discard an active override or its revert snapshot.
     */
    public void loadConfig() {
        profiles.clear();
        descriptions.clear();

        File file = new File(LagXpert.getInstance().getDataFolder(), "profiles.yml");
        if (!file.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection profilesSection = config.getConfigurationSection("profiles");
        if (profilesSection != null) {
            for (String name : profilesSection.getKeys(false)) {
                ConfigurationSection profile = profilesSection.getConfigurationSection(name);
                if (profile == null) {
                    continue;
                }
                profiles.put(name.toLowerCase(), profile);
                descriptions.put(name.toLowerCase(),
                        profile.getString("description", "No description provided."));
            }
        }

        this.autoRevertMinutes = config.getInt("safety.auto-revert-minutes", 10);
        this.requiredPermission = config.getString("safety.permission", "lagxpert.admin.profile");

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info(
                    "[ProfileManager] Loaded " + profiles.size() + " profile(s): " + profiles.keySet());
        }
    }

    /** Result of an apply attempt, so callers can report precisely what happened. */
    public static class ApplyResult {
        private final boolean success;
        private final String message;
        private final int keysApplied;
        private final List<String> unknownKeys;

        ApplyResult(boolean success, String message, int keysApplied, List<String> unknownKeys) {
            this.success = success;
            this.message = message;
            this.keysApplied = keysApplied;
            this.unknownKeys = unknownKeys == null ? Collections.emptyList() : unknownKeys;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getKeysApplied() { return keysApplied; }
        public List<String> getUnknownKeys() { return unknownKeys; }
    }

    /**
     * Applies a named profile.
     *
     * Values are written to the relevant config files, persisted to disk, and
     * then the whole configuration is reloaded so every subsystem observes the
     * new values immediately.
     *
     * @param profileName the profile to apply, case-insensitive
     * @param actorName   who requested it, for logging and revert attribution
     */
    public synchronized ApplyResult apply(String profileName, String actorName) {
        if (profileName == null || profileName.trim().isEmpty()) {
            return new ApplyResult(false, "No profile name given.", 0, null);
        }

        String key = profileName.trim().toLowerCase();
        ConfigurationSection profile = profiles.get(key);
        if (profile == null) {
            return new ApplyResult(false,
                    "Unknown profile '" + profileName + "'. Available: " + String.join(", ", getProfileNames()),
                    0, null);
        }

        // Capture the pre-profile state exactly once, so that applying a second
        // profile on top of a first still reverts all the way back to the
        // operator's original configuration.
        boolean firstApplication = snapshot.isEmpty();

        int applied = 0;
        List<String> unknownKeys = new ArrayList<>();

        for (String profileKey : flattenProfileKeys(profile)) {
            ConfigTarget target = KEY_MAPPINGS.get(profileKey);
            if (target == null) {
                unknownKeys.add(profileKey);
                continue;
            }

            Object value = profile.get(profileKey);
            if (value == null) {
                continue;
            }

            if (firstApplication) {
                String snapshotKey = target.fileName + "|" + target.path;
                if (!snapshot.containsKey(snapshotKey)) {
                    Object previous = ConfigManager.hasRawConfigValue(target.fileName, target.path)
                            ? ConfigManager.getRawConfigValue(target.fileName, target.path)
                            : ABSENT;
                    snapshot.put(snapshotKey, previous);
                }
            }

            if (ConfigManager.setRawConfigValue(target.fileName, target.path, value)) {
                applied++;
            }
        }

        if (applied == 0) {
            return new ApplyResult(false,
                    "Profile '" + key + "' contained no applicable settings.", 0, unknownKeys);
        }

        boolean saved = ConfigManager.saveModifiedConfigs();
        // Reload so the cached static fields and every subsystem pick up the change.
        LagXpert.getInstance().reloadAllConfigurations();

        this.activeProfile = key;
        this.activeSince = System.currentTimeMillis();
        this.appliedBy = actorName;

        scheduleAutoRevert();

        ActionLogger.getInstance().log(
                ActionLogger.ActionType.CONFIG_CHANGED,
                null, null,
                "Applied profile '" + key + "' (" + applied + " setting(s))",
                applied, actorName == null ? "console" : actorName, saved, 0);

        LagXpert.getInstance().getLogger().info(
                "[ProfileManager] Profile '" + key + "' applied by " +
                        (actorName == null ? "CONSOLE" : actorName) + " (" + applied + " setting(s)).");

        if (!unknownKeys.isEmpty()) {
            LagXpert.getInstance().getLogger().warning(
                    "[ProfileManager] Profile '" + key + "' contains unrecognised keys that were ignored: " +
                            unknownKeys);
        }

        String message = "Profile '" + key + "' applied (" + applied + " setting(s))." +
                (saved ? "" : " Warning: some files could not be saved to disk.");
        return new ApplyResult(true, message, applied, unknownKeys);
    }

    /**
     * Restores the configuration captured before the first profile was applied.
     *
     * @param actorName who requested the revert
     */
    public synchronized ApplyResult revert(String actorName) {
        if (snapshot.isEmpty()) {
            return new ApplyResult(false, "No profile is currently applied, nothing to revert.", 0, null);
        }

        int restored = 0;
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            if (parts.length != 2) {
                continue;
            }
            Object value = entry.getValue() == ABSENT ? null : entry.getValue();
            if (ConfigManager.setRawConfigValue(parts[0], parts[1], value)) {
                restored++;
            }
        }

        boolean saved = ConfigManager.saveModifiedConfigs();
        LagXpert.getInstance().reloadAllConfigurations();

        String previous = activeProfile;
        snapshot.clear();
        activeProfile = null;
        activeSince = 0L;
        appliedBy = null;
        cancelAutoRevert();

        ActionLogger.getInstance().log(
                ActionLogger.ActionType.CONFIG_CHANGED,
                null, null,
                "Reverted profile '" + previous + "' (" + restored + " setting(s) restored)",
                restored, actorName == null ? "console" : actorName, saved, 0);

        LagXpert.getInstance().getLogger().info(
                "[ProfileManager] Profile '" + previous + "' reverted by " +
                        (actorName == null ? "CONSOLE" : actorName) + ".");

        return new ApplyResult(true,
                "Reverted to the configuration from before profile '" + previous + "' was applied (" +
                        restored + " setting(s)).", restored, null);
    }

    /**
     * Schedules the safety auto-revert, replacing any previously scheduled one.
     */
    private void scheduleAutoRevert() {
        cancelAutoRevert();

        if (autoRevertMinutes <= 0) {
            return; // Auto-revert disabled.
        }

        long delayTicks = autoRevertMinutes * 60L * 20L;
        autoRevertTask = SchedulerWrapper.runTaskLater(() -> {
            if (activeProfile == null) {
                return;
            }
            LagXpert.getInstance().getLogger().info(
                    "[ProfileManager] Auto-reverting profile '" + activeProfile + "' after " +
                            autoRevertMinutes + " minute(s).");
            revert("auto-revert");
        }, delayTicks);
    }

    private void cancelAutoRevert() {
        if (autoRevertTask != null) {
            try {
                autoRevertTask.cancel();
            } catch (Exception ignored) {
                // Task may already have run; nothing to do.
            }
            autoRevertTask = null;
        }
    }

    /**
     * Flattens a profile section into the dotted keys used by the mapping table,
     * skipping the {@code description} metadata field.
     */
    private List<String> flattenProfileKeys(ConfigurationSection profile) {
        List<String> keys = new ArrayList<>();
        Set<String> deepKeys = profile.getKeys(true);
        for (String candidate : deepKeys) {
            if (candidate.equals("description")) {
                continue;
            }
            // Only leaf nodes carry values; skip intermediate sections.
            if (profile.isConfigurationSection(candidate)) {
                continue;
            }
            keys.add(candidate);
        }
        return keys;
    }

    // ─── Query API ──────────────────────────────────────────────────────

    public List<String> getProfileNames() {
        List<String> names = new ArrayList<>(profiles.keySet());
        Collections.sort(names);
        return names;
    }

    public String getDescription(String profileName) {
        if (profileName == null) {
            return null;
        }
        return descriptions.get(profileName.toLowerCase());
    }

    public boolean hasProfile(String profileName) {
        return profileName != null && profiles.containsKey(profileName.toLowerCase());
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public String getAppliedBy() {
        return appliedBy;
    }

    public long getActiveSince() {
        return activeSince;
    }

    public int getAutoRevertMinutes() {
        return autoRevertMinutes;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }

    /**
     * Returns the number of settings that would be restored by a revert.
     */
    public int getSnapshotSize() {
        return snapshot.size();
    }

    /**
     * Cancels the auto-revert timer on shutdown.
     *
     * The snapshot is intentionally left on disk-backed config as-is: whatever
     * profile was applied stays applied across a restart, which matches operator
     * expectations for a config-editing command.
     */
    public void shutdown() {
        cancelAutoRevert();
    }
}
