package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.monitoring.TPSMonitor;
import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptive Threshold Engine — the single authority for "what is the limit right now".
 *
 * Every per-chunk limit in the plugin is nominally a fixed number from a YAML
 * file. This engine turns those fixed numbers into live values by scaling them
 * against two independent inputs:
 *
 * <ol>
 *   <li><b>Continuous server health</b> — a factor derived from current TPS and
 *       heap usage, so limits tighten smoothly as the server degrades and relax
 *       as it recovers.</li>
 *   <li><b>Discrete emergency state</b> — the {@link EmergencyController}'s
 *       per-state multipliers, which are step changes applied on top.</li>
 * </ol>
 *
 * The two are combined by taking the <em>most restrictive</em> of the pair. A
 * server that is both continuously degraded and in CRITICAL gets the tighter of
 * the two limits rather than the product, which would compound into absurdly
 * small values.
 *
 * Limits are only ever scaled <em>down</em>. The value configured by the
 * operator is always treated as the ceiling.
 *
 * All effective-limit lookups in the plugin should route through this class so
 * that adaptive behavior cannot be silently bypassed by a caller reading the
 * raw config value directly.
 *
 * Configured via the {@code adaptive-thresholds} section of config.yml.
 */
public class AdaptiveThresholdEngine {

    private static AdaptiveThresholdEngine instance;

    /**
     * Categories of limit that can be scaled independently.
     *
     * Each maps to its own sensitivity value so operators can, for example,
     * let mob limits react aggressively while leaving storage limits nearly
     * fixed so player builds are not disrupted.
     */
    public enum LimitCategory {
        MOBS,
        STORAGE,
        ENTITIES,
        REDSTONE
    }

    private boolean enabled;
    private double mobSensitivity = 1.0;
    private double storageSensitivity = 0.7;
    private double entitySensitivity = 1.0;
    private double redstoneSensitivity = 1.0;

    /**
     * Hard floor for any computed multiplier.
     *
     * Prevents a pathological health reading from collapsing a limit to
     * something unplayable. Also guarantees the engine can never produce zero,
     * which would block all placement and all spawning outright.
     */
    private double minimumMultiplier = 0.25;

    /** TPS at or above which the server is considered fully healthy. */
    private double healthyTps = 20.0;

    /** TPS at or below which the TPS component of health bottoms out. */
    private double degradedTps = 10.0;

    /** Heap usage percentage at or below which memory is considered fully healthy. */
    private double healthyMemoryPercent = 50.0;

    /** Heap usage percentage at or above which the memory component bottoms out. */
    private double degradedMemoryPercent = 95.0;

    private AdaptiveThresholdEngine() {
        loadConfig();
    }

    public static AdaptiveThresholdEngine getInstance() {
        if (instance == null) {
            instance = new AdaptiveThresholdEngine();
        }
        return instance;
    }

    public void loadConfig() {
        java.io.File file = new java.io.File(
                LagXpert.getInstance().getDataFolder(), "config.yml");
        if (!file.exists()) {
            this.enabled = false;
            return;
        }
        org.bukkit.configuration.file.FileConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        this.enabled = config.getBoolean("adaptive-thresholds.enabled", true);

        this.mobSensitivity = clampSensitivity(
                config.getDouble("adaptive-thresholds.sensitivity.mobs", 1.0));
        this.storageSensitivity = clampSensitivity(
                config.getDouble("adaptive-thresholds.sensitivity.storage", 0.7));
        this.entitySensitivity = clampSensitivity(
                config.getDouble("adaptive-thresholds.sensitivity.entities", 1.0));
        this.redstoneSensitivity = clampSensitivity(
                config.getDouble("adaptive-thresholds.sensitivity.redstone", 1.0));

        this.minimumMultiplier = Math.max(0.05, Math.min(1.0,
                config.getDouble("adaptive-thresholds.minimum-multiplier", 0.25)));

        this.healthyTps = config.getDouble("adaptive-thresholds.health.healthy-tps", 20.0);
        this.degradedTps = config.getDouble("adaptive-thresholds.health.degraded-tps", 10.0);
        this.healthyMemoryPercent =
                config.getDouble("adaptive-thresholds.health.healthy-memory-percent", 50.0);
        this.degradedMemoryPercent =
                config.getDouble("adaptive-thresholds.health.degraded-memory-percent", 95.0);

        // Guard against inverted ranges, which would produce nonsense factors.
        if (this.degradedTps >= this.healthyTps) {
            LagXpert.getInstance().getLogger().warning(
                    "[AdaptiveThresholdEngine] health.degraded-tps (" + degradedTps +
                            ") must be lower than health.healthy-tps (" + healthyTps +
                            "). Falling back to defaults 10.0/20.0.");
            this.degradedTps = 10.0;
            this.healthyTps = 20.0;
        }
        if (this.degradedMemoryPercent <= this.healthyMemoryPercent) {
            LagXpert.getInstance().getLogger().warning(
                    "[AdaptiveThresholdEngine] health.degraded-memory-percent (" + degradedMemoryPercent +
                            ") must be higher than health.healthy-memory-percent (" + healthyMemoryPercent +
                            "). Falling back to defaults 50.0/95.0.");
            this.healthyMemoryPercent = 50.0;
            this.degradedMemoryPercent = 95.0;
        }
    }

    private static double clampSensitivity(double raw) {
        return Math.max(0.0, Math.min(1.0, raw));
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Computes the continuous server health factor from TPS and heap usage.
     *
     * @return a value in [minimumMultiplier, 1.0]; 1.0 means fully healthy.
     */
    public double getHealthFactor() {
        if (!enabled) {
            return 1.0;
        }

        double tps = TPSMonitor.getShortTermTPS();

        long maxMem = Runtime.getRuntime().maxMemory();
        long totalMem = Runtime.getRuntime().totalMemory();
        long freeMem = Runtime.getRuntime().freeMemory();
        double memUsage = maxMem > 0 ? ((double) (totalMem - freeMem) / maxMem) * 100.0 : 0.0;

        // A TPS of zero means "not yet measured", not "server frozen". Treating it
        // as a catastrophic reading would clamp every limit during startup.
        double tpsFactor = 1.0;
        if (tps > 0) {
            tpsFactor = (tps - degradedTps) / (healthyTps - degradedTps);
            tpsFactor = clamp01(tpsFactor);
        }

        double memFactor = (degradedMemoryPercent - memUsage)
                / (degradedMemoryPercent - healthyMemoryPercent);
        memFactor = clamp01(memFactor);

        // The worse of the two dominates. Averaging would let healthy memory mask
        // a genuinely bad TPS reading, which is the case that actually matters.
        double combined = Math.min(tpsFactor, memFactor);

        return Math.max(minimumMultiplier, combined);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double sensitivityFor(LimitCategory category) {
        switch (category) {
            case MOBS:
                return mobSensitivity;
            case STORAGE:
                return storageSensitivity;
            case ENTITIES:
                return entitySensitivity;
            case REDSTONE:
                return redstoneSensitivity;
            default:
                return 1.0;
        }
    }

    /**
     * Returns the effective multiplier for a category, combining continuous
     * health scaling with the discrete emergency-state multiplier.
     *
     * @return a value in (0.0, 1.0]; 1.0 means no reduction.
     */
    public double getMultiplier(LimitCategory category) {
        double adaptive = 1.0;

        if (enabled) {
            double health = getHealthFactor();
            double sensitivity = sensitivityFor(category);
            // Sensitivity interpolates between "ignore health" and "track health fully".
            adaptive = 1.0 - (1.0 - health) * sensitivity;
        }

        // Layer the emergency state on top. Mob caps are the only category the
        // EmergencyController defines an explicit multiplier for; the rest inherit
        // it at reduced strength via their own sensitivity so that a CRITICAL
        // server tightens storage and redstone too, just less aggressively.
        EmergencyController controller = EmergencyController.getInstance();
        double emergency = 1.0;
        if (controller.isEnabled()) {
            double stateMultiplier = controller.getMobCapMultiplier();
            if (category == LimitCategory.MOBS) {
                emergency = stateMultiplier;
            } else {
                double sensitivity = sensitivityFor(category);
                emergency = 1.0 - (1.0 - stateMultiplier) * sensitivity;
            }
        }

        // Most restrictive of the two wins; never compound them.
        double combined = Math.min(adaptive, emergency);

        return Math.max(minimumMultiplier, Math.min(1.0, combined));
    }

    /**
     * Scales a configured limit into the value that should be enforced right now.
     *
     * Non-positive base limits are passed through untouched, because the codebase
     * uses {@code <= 0} to mean "this limit is disabled" and scaling that would
     * accidentally turn it into an active limit of 1.
     *
     * @param category  which sensitivity profile to apply
     * @param baseLimit the operator-configured limit
     * @return the limit to enforce, never below 1 for an active limit
     */
    public int getEffectiveLimit(LimitCategory category, int baseLimit) {
        if (baseLimit <= 0) {
            return baseLimit; // Disabled limit stays disabled.
        }
        if (!enabled && !EmergencyController.getInstance().isEnabled()) {
            return baseLimit;
        }
        int scaled = (int) Math.floor(baseLimit * getMultiplier(category));
        return Math.max(1, Math.min(baseLimit, scaled));
    }

    /**
     * Convenience overload for the mob limit of a specific world, resolving the
     * per-world configured value before scaling.
     */
    public int getEffectiveMobLimit(World world) {
        return getEffectiveLimit(LimitCategory.MOBS, ConfigManager.getMaxMobsPerChunk(world));
    }

    // ─── Backwards-compatible category accessors ────────────────────────
    // Retained because the status command and API expose them.

    public double getMobMultiplier() {
        return getMultiplier(LimitCategory.MOBS);
    }

    public double getStorageMultiplier() {
        return getMultiplier(LimitCategory.STORAGE);
    }

    public double getEntityMultiplier() {
        return getMultiplier(LimitCategory.ENTITIES);
    }

    public double getRedstoneMultiplier() {
        return getMultiplier(LimitCategory.REDSTONE);
    }

    /**
     * Returns true when limits are currently being reduced below their
     * configured values, for display purposes.
     */
    public boolean isCurrentlyThrottling() {
        return getMultiplier(LimitCategory.MOBS) < 0.999d
                || getMultiplier(LimitCategory.STORAGE) < 0.999d
                || getMultiplier(LimitCategory.ENTITIES) < 0.999d
                || getMultiplier(LimitCategory.REDSTONE) < 0.999d;
    }

    /**
     * Returns statistics for API, commands and the diagnostics GUI.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("enabled", enabled);
        stats.put("throttling", isCurrentlyThrottling());
        stats.put("health_factor", String.format("%.2f", getHealthFactor()));
        stats.put("minimum_multiplier", String.format("%.2f", minimumMultiplier));
        stats.put("mob_multiplier", String.format("%.2f", getMobMultiplier()));
        stats.put("storage_multiplier", String.format("%.2f", getStorageMultiplier()));
        stats.put("entity_multiplier", String.format("%.2f", getEntityMultiplier()));
        stats.put("redstone_multiplier", String.format("%.2f", getRedstoneMultiplier()));
        return stats;
    }
}
