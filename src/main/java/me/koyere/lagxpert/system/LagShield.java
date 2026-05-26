package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;

/**
 * LagShield — deprecated passthrough to EmergencyController.
 *
 * This class is kept for backward compatibility with existing code that
 * references LagShield. All actual logic now lives in EmergencyController.
 *
 * @deprecated Use {@link EmergencyController#getInstance()} directly.
 */
@Deprecated
public class LagShield {

    private static LagShield instance;
    private boolean enabled;
    private double criticalTps;
    private double recoveryTps;
    private double criticalRam;
    private double recoveryRam;

    private LagShield() {
        reloadConfig();
    }

    public static LagShield getInstance() {
        if (instance == null) {
            instance = new LagShield();
        }
        return instance;
    }

    public void reloadConfig() {
        java.io.File file = new java.io.File(LagXpert.getInstance().getDataFolder(), "emergency-controller.yml");
        if (!file.exists()) {
            this.enabled = false;
            return;
        }
        org.bukkit.configuration.file.FileConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        this.enabled = config.getBoolean("enabled", true);
        this.criticalTps = config.getDouble("thresholds.tps.critical", 15.0);
        this.recoveryTps = config.getDouble("thresholds.tps.recovery", 19.0);
        this.criticalRam = config.getDouble("thresholds.ram.critical", 90.0);
        this.recoveryRam = config.getDouble("thresholds.ram.recovery", 75.0);
    }

    /**
     * Delegates to EmergencyController for actual state management.
     * Kept for backward compatibility with TPSMonitor's existing call.
     */
    public void onTick(double currentTps, double ramUsagePercent) {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int activePlayers = onlinePlayers; // Simplified; could track movement

        EmergencyController.getInstance().evaluate(
                currentTps, ramUsagePercent, onlinePlayers, activePlayers);
    }

    /**
     * @deprecated Use {@link EmergencyController#getCurrentState()} == ServerState.NORMAL instead.
     */
    @Deprecated
    public boolean isActive() {
        return enabled && EmergencyController.getInstance().getCurrentState() !=
                EmergencyController.ServerState.NORMAL;
    }

    /**
     * @deprecated Use {@link EmergencyController#getMobCapMultiplier()} instead.
     */
    @Deprecated
    public double getMobCapMultiplier() {
        return EmergencyController.getInstance().getMobCapMultiplier();
    }

    /**
     * @deprecated Use {@link EmergencyController#shouldBlockNaturalSpawns()} instead.
     */
    @Deprecated
    public boolean shouldBlockNaturalSpawns() {
        return EmergencyController.getInstance().shouldBlockNaturalSpawns();
    }
}
