package me.koyere.lagxpert.monitoring;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real-time TPS (Ticks Per Second) monitoring system that tracks server performance,
 * calculates TPS averages over different time windows, and provides detailed statistics.
 * Designed to be lightweight and accurate for continuous monitoring.
 */
public class TPSMonitor extends BukkitRunnable {

    // TPS calculation constants
    private static final double TARGET_TPS = 20.0;
    private static final long TARGET_TICK_TIME_NS = 50_000_000L; // 50ms in nanoseconds

    // Instance variables for TPS tracking
    private static TPSMonitor instance;
    private final AtomicLong lastTickTime = new AtomicLong(System.nanoTime());
    private final Queue<TickData> tickHistory = new ArrayDeque<>();

    // TPS calculation windows (configured values)
    private int shortTermWindow;   // 1 minute default
    private int mediumTermWindow;  // 5 minutes default
    private int longTermWindow;    // 15 minutes default

    // Current TPS values
    private volatile double currentTPS = TARGET_TPS;
    private volatile double shortTermTPS = TARGET_TPS;
    private volatile double mediumTermTPS = TARGET_TPS;
    private volatile double longTermTPS = TARGET_TPS;

    // Performance statistics
    private volatile long totalTicks = 0;
    private volatile double averageTickTime = TARGET_TICK_TIME_NS / 1_000_000.0; // in milliseconds
    private volatile double maxTickTime = 0.0;
    private volatile double minTickTime = Double.MAX_VALUE;

    // Lag spike tracking
    private final List<LagSpike> recentLagSpikes = Collections.synchronizedList(new ArrayList<>());
    private volatile int consecutiveLagSpikes = 0;

    /**
     * Inner class to store individual tick data for TPS calculations.
     */
    private static class TickData {
        private final long timestamp;
        private final double tickTime;

        public TickData(long timestamp, double tickTime) {
            this.timestamp = timestamp;
            this.tickTime = tickTime;
        }

        public long getTimestamp() { return timestamp; }
        public double getTickTime() { return tickTime; }
    }

    /**
     * Inner class to store lag spike information.
     */
    public static class LagSpike {
        private final long timestamp;
        private final double duration;
        private final double tickTime;
        private final String possibleCause;

        public LagSpike(long timestamp, double duration, double tickTime, String possibleCause) {
            this.timestamp = timestamp;
            this.duration = duration;
            this.tickTime = tickTime;
            this.possibleCause = possibleCause;
        }

        public long getTimestamp() { return timestamp; }
        public double getDuration() { return duration; }
        public double getTickTime() { return tickTime; }
        public String getPossibleCause() { return possibleCause; }
    }

    /**
     * Private constructor for singleton pattern.
     */
    private TPSMonitor() {
        loadConfiguration();
    }

    /**
     * Gets or creates the singleton instance of TPSMonitor.
     * @return The TPSMonitor instance
     */
    public static TPSMonitor getInstance() {
        if (instance == null) {
            instance = new TPSMonitor();
        }
        return instance;
    }

    /**
     * Starts the TPS monitoring system.
     */
    public static void startMonitoring() {
        if (instance != null && !instance.isCancelled()) {
            instance.cancel();
        }

        instance = new TPSMonitor();
        int updateInterval = ConfigManager.getTPSUpdateIntervalTicks();
        instance.runTaskTimer(LagXpert.getInstance(), 1L, updateInterval);

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info("[TPSMonitor] TPS monitoring started with update interval: " + updateInterval + " ticks");
        }
    }

    /**
     * Stops the TPS monitoring system.
     */
    public static void stopMonitoring() {
        if (instance != null) {
            instance.cancel();
            instance = null;
        }
    }

    /**
     * Loads configuration values for TPS monitoring.
     */
    private void loadConfiguration() {
        shortTermWindow = ConfigManager.getTPSShortTermWindow();
        mediumTermWindow = ConfigManager.getTPSMediumTermWindow();
        longTermWindow = ConfigManager.getTPSLongTermWindow();
    }

    @Override
    public void run() {
        long currentTime = System.nanoTime();
        long lastTime = lastTickTime.getAndSet(currentTime);

        if (lastTime == 0) {
            return; // First run, no previous time to compare
        }

        double tickTimeMs = (currentTime - lastTime) / 1_000_000.0;
        long timestampSeconds = System.currentTimeMillis() / 1000;

        // Update tick statistics
        totalTicks++;
        updateTickStatistics(tickTimeMs);

        // Store tick data for TPS calculations
        synchronized (tickHistory) {
            tickHistory.offer(new TickData(timestampSeconds, tickTimeMs));

            // Clean old data (keep only data within the longest window)
            long cutoffTime = timestampSeconds - longTermWindow;
            while (!tickHistory.isEmpty() && tickHistory.peek().getTimestamp() < cutoffTime) {
                tickHistory.poll();
            }
        }

        // Calculate TPS for different time windows
        calculateTPS();

        // Detect and handle lag spikes
        detectLagSpikes(tickTimeMs, timestampSeconds);

        // Log debug information if enabled
        if (ConfigManager.shouldLogTPSCalculations()) {
            LagXpert.getInstance().getLogger().info(
                    String.format("[TPSMonitor] Current: %.2f, 1m: %.2f, 5m: %.2f, 15m: %.2f, Tick: %.2fms",
                            currentTPS, shortTermTPS, mediumTermTPS, longTermTPS, tickTimeMs)
            );
        }
    }

    /**
     * Updates tick time statistics.
     */
    private void updateTickStatistics(double tickTimeMs) {
        // Update average tick time (simple moving average)
        averageTickTime = (averageTickTime * (totalTicks - 1) + tickTimeMs) / totalTicks;

        // Update min/max tick times
        if (tickTimeMs > maxTickTime) {
            maxTickTime = tickTimeMs;
        }
        if (tickTimeMs < minTickTime) {
            minTickTime = tickTimeMs;
        }
    }

    /**
     * Calculates TPS for all configured time windows.
     */
    private void calculateTPS() {
        long currentTimeSeconds = System.currentTimeMillis() / 1000;

        synchronized (tickHistory) {
            if (tickHistory.isEmpty()) {
                return;
            }

            // Calculate current TPS (based on last few ticks)
            currentTPS = calculateTPSForWindow(5); // Last 5 seconds

            // Calculate TPS for different windows
            shortTermTPS = calculateTPSForWindow(shortTermWindow);
            mediumTermTPS = calculateTPSForWindow(mediumTermWindow);
            longTermTPS = calculateTPSForWindow(longTermWindow);
        }
    }

    /**
     * Calculates TPS for a specific time window.
     * @param windowSeconds The time window in seconds
     * @return The calculated TPS for the window
     */
    private double calculateTPSForWindow(int windowSeconds) {
        if (tickHistory.isEmpty()) {
            return TARGET_TPS;
        }

        long currentTime = System.currentTimeMillis() / 1000;
        long startTime = currentTime - windowSeconds;

        int tickCount = 0;
        for (TickData tick : tickHistory) {
            if (tick.getTimestamp() >= startTime) {
                tickCount++;
            }
        }

        if (tickCount == 0) {
            return TARGET_TPS;
        }

        // Calculate actual TPS based on tick count and time window
        double actualWindow = Math.min(windowSeconds, currentTime - tickHistory.peek().getTimestamp());
        if (actualWindow <= 0) {
            return TARGET_TPS;
        }

        double tps = tickCount / actualWindow;
        return Math.min(tps, TARGET_TPS); // Cap at target TPS
    }

    /**
     * Detects lag spikes and handles them appropriately.
     */
    private void detectLagSpikes(double tickTimeMs, long timestamp) {
        if (!ConfigManager.isLagDetectionEnabled()) {
            return;
        }

        double lagThreshold = ConfigManager.getLagDetectionThreshold();

        if (tickTimeMs > lagThreshold) {
            consecutiveLagSpikes++;

            // Create lag spike record
            String possibleCause = analyzeLagSpikeCause(tickTimeMs);
            LagSpike spike = new LagSpike(timestamp * 1000, tickTimeMs - TARGET_TICK_TIME_NS / 1_000_000.0, tickTimeMs, possibleCause);

            // Store lag spike (with size limit)
            synchronized (recentLagSpikes) {
                recentLagSpikes.add(spike);
                int maxSpikes = ConfigManager.getMaxTrackedLagSpikes();
                while (recentLagSpikes.size() > maxSpikes) {
                    recentLagSpikes.remove(0);
                }
            }

            // Trigger alert if threshold reached
            int consecutiveThreshold = ConfigManager.getConsecutiveLagSpikesThreshold();
            if (consecutiveLagSpikes >= consecutiveThreshold) {
                PerformanceTracker.getInstance().handleLagSpikeAlert(spike, consecutiveLagSpikes);
            }

            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().warning(
                        String.format("[TPSMonitor] Lag spike detected! Tick time: %.2fms, Consecutive: %d, Cause: %s",
                                tickTimeMs, consecutiveLagSpikes, possibleCause)
                );
            }
        } else {
            consecutiveLagSpikes = 0; // Reset consecutive counter
        }
    }

    /**
     * Analyzes potential causes of lag spikes.
     * @param tickTimeMs The tick time that caused the lag spike
     * @return A string describing the possible cause
     */
    private String analyzeLagSpikeCause(double tickTimeMs) {
        if (!ConfigManager.shouldAutoAnalyzeLagSpikes()) {
            return "Unknown";
        }

        // Simple heuristic analysis
        if (tickTimeMs > 500) {
            return "Severe lag - possible plugin issue or world generation";
        } else if (tickTimeMs > 200) {
            return "Major lag - possible chunk loading or entity processing";
        } else if (tickTimeMs > 100) {
            return "Moderate lag - possible redstone or mob farms";
        } else {
            return "Minor lag - normal server fluctuation";
        }
    }

    // Public getters for TPS values
    public static double getCurrentTPS() {
        return instance != null ? instance.currentTPS : TARGET_TPS;
    }

    public static double getShortTermTPS() {
        return instance != null ? instance.shortTermTPS : TARGET_TPS;
    }

    public static double getMediumTermTPS() {
        return instance != null ? instance.mediumTermTPS : TARGET_TPS;
    }

    public static double getLongTermTPS() {
        return instance != null ? instance.longTermTPS : TARGET_TPS;
    }

    public static double getAverageTickTime() {
        return instance != null ? instance.averageTickTime : TARGET_TICK_TIME_NS / 1_000_000.0;
    }

    public static double getMaxTickTime() {
        return instance != null ? instance.maxTickTime : 0.0;
    }

    public static double getMinTickTime() {
        return instance != null ? instance.minTickTime : TARGET_TICK_TIME_NS / 1_000_000.0;
    }

    public static long getTotalTicks() {
        return instance != null ? instance.totalTicks : 0;
    }

    public static List<LagSpike> getRecentLagSpikes() {
        if (instance == null) {
            return Collections.emptyList();
        }
        synchronized (instance.recentLagSpikes) {
            return new ArrayList<>(instance.recentLagSpikes);
        }
    }

    /**
     * Gets comprehensive TPS statistics.
     * @return A formatted string with TPS information
     */
    public static String getTPSReport() {
        if (instance == null) {
            return "TPS Monitor not initialized";
        }

        return String.format(
                "TPS: Current: %.2f, 1m: %.2f, 5m: %.2f, 15m: %.2f | " +
                        "Tick: Avg: %.2fms, Max: %.2fms, Min: %.2fms | " +
                        "Total Ticks: %d",
                getCurrentTPS(), getShortTermTPS(), getMediumTermTPS(), getLongTermTPS(),
                getAverageTickTime(), getMaxTickTime(), getMinTickTime(), getTotalTicks()
        );
    }

    /**
     * Resets all TPS statistics.
     */
    public static void resetStatistics() {
        if (instance != null) {
            instance.totalTicks = 0;
            instance.averageTickTime = TARGET_TICK_TIME_NS / 1_000_000.0;
            instance.maxTickTime = 0.0;
            instance.minTickTime = Double.MAX_VALUE;
            instance.consecutiveLagSpikes = 0;

            synchronized (instance.tickHistory) {
                instance.tickHistory.clear();
            }

            synchronized (instance.recentLagSpikes) {
                instance.recentLagSpikes.clear();
            }
        }
    }
}