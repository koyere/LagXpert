package me.koyere.lagxpert.metrics;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.system.AbyssTracker;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;

/**
 * Handles custom bStats metrics for LagXpert.
 * Tracks items removed and items recovered through Abyss.
 */
public class MetricsHandler {

    private static Metrics metrics;

    /**
     * Initializes bStats metrics and registers custom charts.
     */
    public static void init(LagXpert plugin) {
        metrics = new Metrics(plugin, 25746); // Plugin ID from bStats

        // 🧹 Chart: Total items removed by /clearitems or auto-cleanup
        metrics.addCustomChart(new SingleLineChart("items_removed", () -> {
            int count = AbyssTracker.pollRemoved();
            return count >= 0 ? count : 0;
        }));

        // 🕳️ Chart: Items successfully recovered from Abyss
        metrics.addCustomChart(new SingleLineChart("items_recovered", () -> {
            int count = AbyssTracker.pollRecovered();
            return count >= 0 ? count : 0;
        }));
    }
}
