package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
// import me.koyere.lagxpert.utils.SchedulerWrapper; // Unused
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * LagShield: Reactive protection system.
 * Reacts to low TPS or high Memory usage by enabling emergency restrictions.
 */
public class LagShield {

    private static LagShield instance;
    private boolean active = false;
    private boolean enabled;

    // Thresholds
    private double criticalTps;
    private double recoveryTps;
    private double criticalRam;
    private double recoveryRam;

    // Actions
    private double mobCapMultiplier;
    private boolean blockNaturalSpawns;

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
        File file = new File(LagXpert.getInstance().getDataFolder(), "lagshield.yml");
        if (!file.exists()) {
            // Should save default resource if not exists, skipping for now as we just
            // created it
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        this.enabled = config.getBoolean("enabled", true);
        this.criticalTps = config.getDouble("thresholds.tps.critical", 16.0);
        this.recoveryTps = config.getDouble("thresholds.tps.recovery", 18.5);
        this.criticalRam = config.getDouble("thresholds.ram.critical", 90.0);
        this.recoveryRam = config.getDouble("thresholds.ram.recovery", 80.0);

        this.mobCapMultiplier = config.getDouble("actions.reduce-limits.mob-cap-multiplier", 0.5);
        this.blockNaturalSpawns = config.getBoolean("actions.block-spawns.natural", true);
    }

    /**
     * Called by TPSMonitor on every check cycle.
     */
    public void onTick(double currentTps, double ramUsagePercent) {
        if (!enabled)
            return;

        boolean critical = (currentTps < criticalTps) || (ramUsagePercent > criticalRam);
        boolean safe = (currentTps > recoveryTps) && (ramUsagePercent < recoveryRam);

        if (!active && critical) {
            activateShield();
        } else if (active && safe) {
            deactivateShield();
        }
    }

    private void activateShield() {
        active = true;
        LagXpert.getInstance().getLogger().warning("[LagShield] 🛡️ Critical performance detected! Activating shield.");

        if (ConfigManager.isAlertsModuleEnabled()) {
            // Broadcast alert
            String msg = MessageManager.get("alerts.messages.lagshield.activated");
            if (msg != null && !msg.isEmpty()) {
                Bukkit.broadcastMessage(msg);
            }
        }

        // Execute emergency commands or adjustments here
        // Example: Run command to clear ground items if configured
        // In a real implementation, we would toggle flags in other managers
    }

    private void deactivateShield() {
        active = false;
        LagXpert.getInstance().getLogger().info("[LagShield] 🟢 Performance recovered. Deactivating shield.");

        if (ConfigManager.isAlertsModuleEnabled()) {
            String msg = MessageManager.get("alerts.messages.lagshield.deactivated");
            if (msg != null && !msg.isEmpty()) {
                Bukkit.broadcastMessage(msg);
            }
        }
    }

    public boolean isActive() {
        return enabled && active;
    }

    public double getMobCapMultiplier() {
        return isActive() ? mobCapMultiplier : 1.0;
    }

    public boolean shouldBlockNaturalSpawns() {
        return isActive() && blockNaturalSpawns;
    }
}
