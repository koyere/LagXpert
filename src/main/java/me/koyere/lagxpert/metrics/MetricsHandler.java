package me.koyere.lagxpert.metrics;

import me.koyere.lagxpert.system.AbyssTracker; // Asegúrate que el import es correcto
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;

/**
 * Handles custom bStats charts for LagXpert.
 * This class is initialized by the main plugin class, receiving an existing Metrics instance.
 */
public class MetricsHandler {

    /**
     * Initializes custom bStats charts using the provided Metrics instance.
     *
     * @param metrics The bStats Metrics instance created by the main plugin class.
     */
    public static void init(Metrics metrics) {
        // Chart: Total items added to the Abyss system
        // (e.g., by /clearitems or automatic item cleanup)
        metrics.addCustomChart(new SingleLineChart("items_added_to_abyss", () -> {
            // Uses the updated method name from AbyssTracker
            int count = AbyssTracker.pollItemsAddedToAbyss();
            // AtomicInteger's getAndSet(0) will return non-negative if only positive values were added.
            // The check "count >= 0 ? count : 0" is generally not needed here but harmless.
            return count;
        }));

        // Chart: Total items successfully recovered by players from the Abyss
        metrics.addCustomChart(new SingleLineChart("items_recovered_from_abyss", () -> {
            // Uses the updated method name from AbyssTracker
            int count = AbyssTracker.pollItemsRecoveredFromAbyss();
            return count;
        }));

        // You can add more custom charts here using the 'metrics' instance
        // For example:
        // metrics.addCustomChart(new SimplePie("abyss_enabled_status", () -> {
        //     return ConfigManager.isAbyssEnabled() ? "Enabled" : "Disabled";
        // }));
    }
}