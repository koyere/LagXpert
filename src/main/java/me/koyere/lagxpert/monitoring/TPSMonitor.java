package me.koyere.lagxpert.monitoring;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.system.EmergencyController;
import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real-time TPS (Ticks Per Second) monitoring system.
 *
 * Uses server-reported TPS via Bukkit.getTPS() as the primary source
 * (available on Paper/Spigot 1.16+). Falls back to manual calculation
 * via System.nanoTime() on pure Bukkit servers.
 *
 * Fed into EmergencyController for state evaluation.
 */
public class TPSMonitor extends BukkitRunnable {

    private static final double TARGET_TPS = 20.0;
    private static final long TARGET_TICK_TIME_NS = 50_000_000L;

    private static TPSMonitor instance;

    // Whether server-reported TPS API is available
    private boolean serverTPSAvailable = true;

    // Fallback: manual TPS calculation via tick history
    private final AtomicLong lastTickTime = new AtomicLong(System.nanoTime());
    private final Queue<TickData> tickHistory = new ArrayDeque<>();

    // TPS calculation windows
    private int shortTermWindow;
    private int mediumTermWindow;
    private int longTermWindow;

    // Current TPS values
    private volatile double currentTPS = TARGET_TPS;
    private volatile double shortTermTPS = TARGET_TPS;
    private volatile double mediumTermTPS = TARGET_TPS;
    private volatile double longTermTPS = TARGET_TPS;

    // Performance statistics
    private volatile long totalTicks = 0;
    private volatile double averageTickTime = TARGET_TICK_TIME_NS / 1_000_000.0;
    private volatile double maxTickTime = 0.0;
    private volatile double minTickTime = Double.MAX_VALUE;

    // Lag spike tracking
    private final List<LagSpike> recentLagSpikes = Collections.synchronizedList(new ArrayList<>());
    private volatile int consecutiveLagSpikes = 0;
    private volatile long lastLagSpikeAlertTime = 0;
    private static final long LAG_SPIKE_MIN_ALERT_INTERVAL_MS = 5000;

    private static class TickData {
        private final long timestamp;
        private final double tickTime;
        TickData(long timestamp, double tickTime) {
            this.timestamp = timestamp;
            this.tickTime = tickTime;
        }
        long getTimestamp() { return timestamp; }
        double getTickTime() { return tickTime; }
    }

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

    private TPSMonitor() {
        loadConfiguration();
    }

    public static TPSMonitor getInstance() {
        if (instance == null) {
            instance = new TPSMonitor();
        }
        return instance;
    }

    public static void startMonitoring() {
        if (instance != null && !instance.isCancelled()) {
            instance.cancel();
        }
        instance = new TPSMonitor();
        int updateInterval = ConfigManager.getTPSUpdateIntervalTicks();
        instance.runTaskTimer(LagXpert.getInstance(), 1L, updateInterval);
    }

    public static void stopMonitoring() {
        if (instance != null) {
            instance.cancel();
            instance = null;
        }
    }

    private void loadConfiguration() {
        shortTermWindow = ConfigManager.getTPSShortTermWindow();
        mediumTermWindow = ConfigManager.getTPSMediumTermWindow();
        longTermWindow = ConfigManager.getTPSLongTermWindow();
    }

    @Override
    public void run() {
        totalTicks++;

        if (serverTPSAvailable) {
            readServerTPS();
        } else {
            calculateManualTPS();
        }

        // Detect lag spikes based on current TPS
        detectLagSpikes();

        // Memory usage for EmergencyController
        long maxMemory = Runtime.getRuntime().maxMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsagePercent = (maxMemory > 0) ? ((double) usedMemory / maxMemory) * 100.0 : 0.0;

        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        EmergencyController.getInstance().evaluate(shortTermTPS, memoryUsagePercent,
                onlinePlayers, onlinePlayers);
    }

    /**
     * Reads TPS directly from the server API via reflection (Paper/Spigot).
     * Provides accurate TPS unaffected by scheduler delays.
     * Falls back to manual calculation on API failure.
     */
    private void readServerTPS() {
        try {
            java.lang.reflect.Method getTPSMethod = Bukkit.getServer().getClass().getMethod("getTPS");
            double[] tpsArray = (double[]) getTPSMethod.invoke(Bukkit.getServer());
            if (tpsArray != null && tpsArray.length >= 3) {
                serverTPSAvailable = true;
                shortTermTPS = Math.min(tpsArray[0], TARGET_TPS);
                mediumTermTPS = Math.min(tpsArray[1], TARGET_TPS);
                longTermTPS = Math.min(tpsArray[2], TARGET_TPS);
                currentTPS = shortTermTPS;
                averageTickTime = shortTermTPS > 0 ? 1000.0 / shortTermTPS : 1000.0;
                if (averageTickTime > maxTickTime) maxTickTime = averageTickTime;
                if (averageTickTime < minTickTime) minTickTime = averageTickTime;
            }
        } catch (Exception e) {
            serverTPSAvailable = false;
            calculateManualTPS();
        }
    }

    /**
     * Fallback: manual TPS calculation using System.nanoTime() between task runs.
     * Only used when server-reported TPS is unavailable (pure Bukkit).
     */
    private void calculateManualTPS() {
        long currentTime = System.nanoTime();
        long lastTime = lastTickTime.getAndSet(currentTime);

        if (lastTime == 0) return;

        double tickTimeMs = (currentTime - lastTime) / 1_000_000.0;
        long timestampSeconds = System.currentTimeMillis() / 1000;

        if (tickTimeMs > maxTickTime) maxTickTime = tickTimeMs;
        if (tickTimeMs < minTickTime) minTickTime = tickTimeMs;

        synchronized (tickHistory) {
            tickHistory.offer(new TickData(timestampSeconds, tickTimeMs));
            long cutoffTime = timestampSeconds - longTermWindow;
            while (!tickHistory.isEmpty() && tickHistory.peek().getTimestamp() < cutoffTime) {
                tickHistory.poll();
            }
        }

        currentTPS = calculateTPSForWindow(5);
        shortTermTPS = calculateTPSForWindow(shortTermWindow);
        mediumTermTPS = calculateTPSForWindow(mediumTermWindow);
        longTermTPS = calculateTPSForWindow(longTermWindow);

        averageTickTime = (averageTickTime * (totalTicks - 1) + tickTimeMs) / totalTicks;
    }

    private double calculateTPSForWindow(int windowSeconds) {
        synchronized (tickHistory) {
            if (tickHistory.isEmpty()) return TARGET_TPS;

            long currentTime = System.currentTimeMillis() / 1000;
            long startTime = currentTime - windowSeconds;
            int tickCount = 0;

            for (TickData tick : tickHistory) {
                if (tick.getTimestamp() >= startTime) tickCount++;
            }

            if (tickCount == 0) return TARGET_TPS;

            double actualWindow = Math.min(windowSeconds,
                    currentTime - tickHistory.peek().getTimestamp());
            if (actualWindow <= 0) return TARGET_TPS;

            return Math.min(tickCount / actualWindow, TARGET_TPS);
        }
    }

    /**
     * Detects lag spikes based on short-term TPS.
     * Only fires max once every 5 seconds to prevent spam.
     */
    private void detectLagSpikes() {
        if (!ConfigManager.isLagDetectionEnabled()) return;

        double lagThresholdTPS = 1000.0 / ConfigManager.getLagDetectionThreshold();
        int consecutiveThreshold = ConfigManager.getConsecutiveLagSpikesThreshold();

        if (shortTermTPS < lagThresholdTPS) {
            consecutiveLagSpikes++;

            // Rate-limit: only create spike entries max every 5 seconds
            long now = System.currentTimeMillis();
            if (now - lastLagSpikeAlertTime < LAG_SPIKE_MIN_ALERT_INTERVAL_MS) {
                return;
            }
            lastLagSpikeAlertTime = now;

            String possibleCause = analyzeLagSpikeCause(shortTermTPS);
            LagSpike spike = new LagSpike(now, 0, 1000.0 / Math.max(shortTermTPS, 0.1), possibleCause);

            synchronized (recentLagSpikes) {
                recentLagSpikes.add(spike);
                int maxSpikes = ConfigManager.getMaxTrackedLagSpikes();
                while (recentLagSpikes.size() > maxSpikes) {
                    recentLagSpikes.remove(0);
                }
            }

            if (consecutiveLagSpikes >= consecutiveThreshold) {
                PerformanceTracker.getInstance().handleLagSpikeAlert(spike, consecutiveLagSpikes);
            }
        } else {
            consecutiveLagSpikes = 0;
        }
    }

    private String analyzeLagSpikeCause(double tps) {
        if (!ConfigManager.shouldAutoAnalyzeLagSpikes()) return "Unknown";
        if (tps < 5) return "Severe lag — possible plugin issue or world generation";
        if (tps < 10) return "Major lag — possible chunk loading or entity processing";
        if (tps < 15) return "Moderate lag — possible redstone or mob farms";
        return "Minor lag — normal server fluctuation";
    }

    // Public getters
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
        if (instance == null) return Collections.emptyList();
        synchronized (instance.recentLagSpikes) {
            return new ArrayList<>(instance.recentLagSpikes);
        }
    }

    public static String getTPSReport() {
        if (instance == null) return "TPS Monitor not initialized";
        return String.format(
                "TPS: Current: %.2f, 1m: %.2f, 5m: %.2f, 15m: %.2f | " +
                        "Tick: Avg: %.2fms, Max: %.2fms | Total Ticks: %d",
                getCurrentTPS(), getShortTermTPS(), getMediumTermTPS(), getLongTermTPS(),
                getAverageTickTime(), getMaxTickTime(), getTotalTicks());
    }

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
