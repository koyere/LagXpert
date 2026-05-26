package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.monitoring.TPSMonitor;
import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptive Threshold Engine — computes dynamic limit multipliers based on
 * real-time server health metrics.
 *
 * All limit types (mobs, storage, entities, redstone) are multiplied by a
 * server health factor derived from current TPS and memory usage.
 *
 * Health factor ranges from 0.25 (emergency) to 1.0 (healthy).
 * Individual limit categories can have their own sensitivity via config.
 *
 * Configurable via adaptive-thresholds section in config.yml.
 */
public class AdaptiveThresholdEngine {

    private static AdaptiveThresholdEngine instance;

    private boolean enabled;
    private double mobSensitivity = 1.0;
    private double storageSensitivity = 0.7;
    private double entitySensitivity = 1.0;
    private double redstoneSensitivity = 1.0;

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
        this.mobSensitivity = config.getDouble("adaptive-thresholds.sensitivity.mobs", 1.0);
        this.storageSensitivity = config.getDouble("adaptive-thresholds.sensitivity.storage", 0.7);
        this.entitySensitivity = config.getDouble("adaptive-thresholds.sensitivity.entities", 1.0);
        this.redstoneSensitivity = config.getDouble("adaptive-thresholds.sensitivity.redstone", 1.0);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Computes the server health factor from TPS and memory.
     * Range: 0.25 (worst) to 1.0 (best).
     */
    public double getHealthFactor() {
        if (!enabled) return 1.0;

        double tps = TPSMonitor.getShortTermTPS();
        long maxMem = Runtime.getRuntime().maxMemory();
        long totalMem = Runtime.getRuntime().totalMemory();
        long freeMem = Runtime.getRuntime().freeMemory();
        double memUsage = maxMem > 0 ? ((double)(totalMem - freeMem) / maxMem) * 100.0 : 0;

        // TPS component: 1.0 at 20TPS, 0.25 at 10TPS
        double tpsFactor = Math.max(0.25, Math.min(1.0, (tps - 10.0) / 10.0));

        // Memory component: 1.0 at 50%, 0.25 at 95%
        double memFactor = Math.max(0.25, Math.min(1.0, (95.0 - memUsage) / 45.0));

        // Combined factor: average of TPS and memory health
        return (tpsFactor + memFactor) / 2.0;
    }

    /**
     * Returns the effective limit multiplier for mobs.
     * Factors in both server health and mob sensitivity.
     */
    public double getMobMultiplier() {
        if (!enabled) return 1.0;
        double health = getHealthFactor();
        return 1.0 - (1.0 - health) * mobSensitivity;
    }

    /**
     * Returns the effective limit multiplier for storage blocks.
     * Storage is less sensitive by default (0.7 sensitivity).
     */
    public double getStorageMultiplier() {
        if (!enabled) return 1.0;
        double health = getHealthFactor();
        return 1.0 - (1.0 - health) * storageSensitivity;
    }

    /**
     * Returns the effective limit multiplier for entity cleanup thresholds.
     */
    public double getEntityMultiplier() {
        if (!enabled) return 1.0;
        double health = getHealthFactor();
        return 1.0 - (1.0 - health) * entitySensitivity;
    }

    /**
     * Returns the effective limit multiplier for redstone circuit thresholds.
     */
    public double getRedstoneMultiplier() {
        if (!enabled) return 1.0;
        double health = getHealthFactor();
        return 1.0 - (1.0 - health) * redstoneSensitivity;
    }

    /**
     * Returns effective mob limit for a world, factoring in both
     * the adaptive threshold AND the EmergencyController state.
     */
    public int getEffectiveMobLimit(World world) {
        int baseLimit = ConfigManager.getMaxMobsPerChunk(world);
        double multiplier = getMobMultiplier();
        return Math.max(1, (int)(baseLimit * multiplier));
    }

    /**
     * Returns effective storage limit for a world.
     */
    public int getEffectiveStorageLimit(int baseLimit) {
        double multiplier = getStorageMultiplier();
        return Math.max(1, (int)(baseLimit * multiplier));
    }

    /**
     * Returns statistics for API/commands.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("enabled", enabled);
        stats.put("health_factor", String.format("%.2f", getHealthFactor()));
        stats.put("mob_multiplier", String.format("%.2f", getMobMultiplier()));
        stats.put("storage_multiplier", String.format("%.2f", getStorageMultiplier()));
        stats.put("entity_multiplier", String.format("%.2f", getEntityMultiplier()));
        stats.put("redstone_multiplier", String.format("%.2f", getRedstoneMultiplier()));
        return stats;
    }
}
