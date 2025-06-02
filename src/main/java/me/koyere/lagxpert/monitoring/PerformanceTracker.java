package me.koyere.lagxpert.monitoring;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive performance tracking system that monitors memory usage,
 * chunk statistics, and coordinates with TPS monitoring to provide
 * complete server performance analysis and alerting.
 */
public class PerformanceTracker extends BukkitRunnable {

    private static PerformanceTracker instance;

    // Memory tracking
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private volatile double currentMemoryUsage = 0.0;
    private volatile double maxMemoryUsage = 0.0;
    private volatile long totalMemory = 0L;
    private volatile long usedMemory = 0L;
    private volatile long freeMemory = 0L;

    // Chunk tracking
    private final AtomicLong totalChunksLoaded = new AtomicLong(0);
    private final AtomicLong chunksLoadedThisMinute = new AtomicLong(0);
    private final AtomicLong chunksUnloadedThisMinute = new AtomicLong(0);
    private final Queue<Long> chunkLoadHistory = new ArrayDeque<>();

    // Performance history for analytics
    private final List<PerformanceSnapshot> performanceHistory = Collections.synchronizedList(new ArrayList<>());
    private volatile long lastSnapshotTime = 0L;

    // Alert cooldown tracking
    private final Map<String, Long> alertCooldowns = new ConcurrentHashMap<>();

    // Performance thresholds and state tracking
    private volatile PerformanceState lastTPSState = PerformanceState.GOOD;
    private volatile PerformanceState lastMemoryState = PerformanceState.GOOD;

    /**
     * Enumeration of performance states for tracking alerts.
     */
    public enum PerformanceState {
        GOOD, WARNING, CRITICAL
    }

    /**
     * Data class for storing performance snapshots.
     */
    public static class PerformanceSnapshot {
        private final long timestamp;
        private final double tps;
        private final double memoryUsage;
        private final long chunksLoaded;
        private final double averageTickTime;

        public PerformanceSnapshot(long timestamp, double tps, double memoryUsage, long chunksLoaded, double averageTickTime) {
            this.timestamp = timestamp;
            this.tps = tps;
            this.memoryUsage = memoryUsage;
            this.chunksLoaded = chunksLoaded;
            this.averageTickTime = averageTickTime;
        }

        public long getTimestamp() { return timestamp; }
        public double getTps() { return tps; }
        public double getMemoryUsage() { return memoryUsage; }
        public long getChunksLoaded() { return chunksLoaded; }
        public double getAverageTickTime() { return averageTickTime; }
    }

    /**
     * Private constructor for singleton pattern.
     */
    private PerformanceTracker() {
        updateChunkCount();
    }

    /**
     * Gets or creates the singleton instance.
     */
    public static PerformanceTracker getInstance() {
        if (instance == null) {
            instance = new PerformanceTracker();
        }
        return instance;
    }

    /**
     * Starts the performance tracking system.
     */
    public static void startTracking() {
        if (instance != null && !instance.isCancelled()) {
            instance.cancel();
        }

        instance = new PerformanceTracker();
        int updateInterval = ConfigManager.getMemoryUpdateIntervalSeconds() * 20; // Convert to ticks
        instance.runTaskTimer(LagXpert.getInstance(), 20L, updateInterval);

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info("[PerformanceTracker] Performance tracking started with update interval: " + updateInterval + " ticks");
        }
    }

    /**
     * Stops the performance tracking system.
     */
    public static void stopTracking() {
        if (instance != null) {
            instance.cancel();
            instance = null;
        }
    }

    @Override
    public void run() {
        // Update memory statistics
        updateMemoryStatistics();

        // Update chunk statistics
        updateChunkStatistics();

        // Check for performance alerts
        checkPerformanceAlerts();

        // Create performance snapshot if enabled
        createPerformanceSnapshot();

        // Log performance details if enabled
        if (ConfigManager.shouldLogMemoryDetails()) {
            LagXpert.getInstance().getLogger().info(String.format(
                    "[PerformanceTracker] Memory: %.2f%% (%.2f/%.2f GB), Chunks: %d, TPS: %.2f",
                    currentMemoryUsage, usedMemory / 1024.0 / 1024.0 / 1024.0,
                    totalMemory / 1024.0 / 1024.0 / 1024.0, totalChunksLoaded.get(), TPSMonitor.getCurrentTPS()
            ));
        }
    }

    /**
     * Updates memory usage statistics.
     */
    private void updateMemoryStatistics() {
        if (!ConfigManager.isMemoryMonitoringEnabled()) {
            return;
        }

        try {
            MemoryUsage heapMemory = memoryBean.getHeapMemoryUsage();
            usedMemory = heapMemory.getUsed();
            totalMemory = heapMemory.getMax();
            freeMemory = totalMemory - usedMemory;

            currentMemoryUsage = totalMemory > 0 ? (double) usedMemory / totalMemory * 100.0 : 0.0;

            if (currentMemoryUsage > maxMemoryUsage) {
                maxMemoryUsage = currentMemoryUsage;
            }

        } catch (Exception e) {
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().warning("[PerformanceTracker] Failed to update memory statistics: " + e.getMessage());
            }
        }
    }

    /**
     * Updates chunk loading statistics.
     */
    private void updateChunkStatistics() {
        if (!ConfigManager.isChunkMonitoringEnabled()) {
            return;
        }

        updateChunkCount();
        updateChunkLoadingRate();
    }

    /**
     * Updates the current chunk count across all worlds.
     */
    private void updateChunkCount() {
        long totalChunks = 0;
        try {
            totalChunks = Bukkit.getWorlds().stream()
                    .mapToLong(world -> world.getLoadedChunks().length)
                    .sum();
        } catch (Exception e) {
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().warning("[PerformanceTracker] Failed to count chunks: " + e.getMessage());
            }
        }
        totalChunksLoaded.set(totalChunks);
    }

    /**
     * Updates chunk loading rate tracking.
     */
    private void updateChunkLoadingRate() {
        if (!ConfigManager.isChunkLoadingRateMonitoring()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        synchronized (chunkLoadHistory) {
            chunkLoadHistory.offer(currentTime);

            // Remove entries older than 1 minute
            long cutoffTime = currentTime - 60000;
            while (!chunkLoadHistory.isEmpty() && chunkLoadHistory.peek() < cutoffTime) {
                chunkLoadHistory.poll();
            }
        }
    }

    /**
     * Checks for performance alerts and sends them if thresholds are exceeded.
     */
    private void checkPerformanceAlerts() {
        if (!ConfigManager.isMonitoringAlertsEnabled()) {
            return;
        }

        // Check TPS alerts
        checkTPSAlerts();

        // Check memory alerts
        checkMemoryAlerts();

        // Check chunk loading alerts
        checkChunkAlerts();
    }

    /**
     * Checks TPS performance and sends alerts if necessary.
     */
    private void checkTPSAlerts() {
        double currentTPS = TPSMonitor.getCurrentTPS();
        PerformanceState currentState = determineTPSState(currentTPS);

        if (currentState != lastTPSState) {
            String alertType = "tps_" + currentState.name().toLowerCase();

            if (canSendAlert(alertType, ConfigManager.getTPSAlertCooldown())) {
                sendTPSAlert(currentState, currentTPS);
                recordAlert(alertType);
            }

            lastTPSState = currentState;
        }
    }

    /**
     * Checks memory performance and sends alerts if necessary.
     */
    private void checkMemoryAlerts() {
        PerformanceState currentState = determineMemoryState(currentMemoryUsage);

        if (currentState != lastMemoryState) {
            String alertType = "memory_" + currentState.name().toLowerCase();

            if (canSendAlert(alertType, ConfigManager.getMemoryAlertCooldown())) {
                sendMemoryAlert(currentState, currentMemoryUsage);
                recordAlert(alertType);
            }

            lastMemoryState = currentState;
        }
    }

    /**
     * Checks chunk loading performance and sends alerts if necessary.
     */
    private void checkChunkAlerts() {
        long currentChunks = totalChunksLoaded.get();
        int maxChunksWarning = ConfigManager.getMaxLoadedChunksWarning();

        if (currentChunks > maxChunksWarning) {
            String alertType = "chunks_overload";

            if (canSendAlert(alertType, 300)) { // 5 minute cooldown for chunk alerts
                sendChunkAlert(currentChunks, maxChunksWarning);
                recordAlert(alertType);
            }
        }

        // Check chunk loading rate
        if (ConfigManager.isChunkLoadingRateMonitoring()) {
            int loadingRate = getCurrentChunkLoadingRate();
            int threshold = ConfigManager.getChunkLoadingRateThreshold();

            if (loadingRate > threshold) {
                String alertType = "chunk_loading_rate";

                if (canSendAlert(alertType, 120)) { // 2 minute cooldown
                    sendChunkLoadingRateAlert(loadingRate, threshold);
                    recordAlert(alertType);
                }
            }
        }
    }

    /**
     * Handles lag spike alerts from TPSMonitor.
     */
    public void handleLagSpikeAlert(TPSMonitor.LagSpike lagSpike, int consecutiveSpikes) {
        String alertType = "lag_spike";

        if (canSendAlert(alertType, ConfigManager.getLagSpikeAlertCooldown())) {
            sendLagSpikeAlert(lagSpike, consecutiveSpikes);
            recordAlert(alertType);
        }
    }

    /**
     * Determines TPS performance state based on current TPS.
     */
    private PerformanceState determineTPSState(double tps) {
        if (tps <= ConfigManager.getTPSCriticalThreshold()) {
            return PerformanceState.CRITICAL;
        } else if (tps <= ConfigManager.getTPSWarningThreshold()) {
            return PerformanceState.WARNING;
        } else {
            return PerformanceState.GOOD;
        }
    }

    /**
     * Determines memory performance state based on current usage.
     */
    private PerformanceState determineMemoryState(double memoryUsage) {
        if (memoryUsage >= ConfigManager.getMemoryCriticalThreshold()) {
            return PerformanceState.CRITICAL;
        } else if (memoryUsage >= ConfigManager.getMemoryWarningThreshold()) {
            return PerformanceState.WARNING;
        } else {
            return PerformanceState.GOOD;
        }
    }

    /**
     * Sends TPS-related alerts.
     */
    private void sendTPSAlert(PerformanceState state, double tps) {
        String messageKey;
        switch (state) {
            case CRITICAL:
                messageKey = "alerts.messages.tps-critical";
                break;
            case WARNING:
                messageKey = "alerts.messages.tps-warning";
                break;
            case GOOD:
                messageKey = "alerts.messages.tps-recovery";
                break;
            default:
                return;
        }

        String message = getAlertMessage(messageKey).replace("{tps}", String.format("%.2f", tps));
        broadcastAlert(message);
    }

    /**
     * Sends memory-related alerts.
     */
    private void sendMemoryAlert(PerformanceState state, double usage) {
        String messageKey;
        switch (state) {
            case CRITICAL:
                messageKey = "alerts.messages.memory-critical";
                break;
            case WARNING:
                messageKey = "alerts.messages.memory-warning";
                break;
            case GOOD:
                messageKey = "alerts.messages.memory-recovery";
                break;
            default:
                return;
        }

        String message = getAlertMessage(messageKey).replace("{usage}", String.format("%.1f", usage));
        broadcastAlert(message);
    }

    /**
     * Sends chunk-related alerts.
     */
    private void sendChunkAlert(long currentChunks, int threshold) {
        String message = MessageManager.color("&e[Performance Alert] &fHigh chunk count: &e" + currentChunks + "&f. Threshold: &e" + threshold + "&f.");
        broadcastAlert(message);
    }

    /**
     * Sends chunk loading rate alerts.
     */
    private void sendChunkLoadingRateAlert(int rate, int threshold) {
        String message = MessageManager.color("&e[Performance Alert] &fHigh chunk loading rate: &e" + rate + "&f chunks/min. Threshold: &e" + threshold + "&f.");
        broadcastAlert(message);
    }

    /**
     * Sends lag spike alerts.
     */
    private void sendLagSpikeAlert(TPSMonitor.LagSpike lagSpike, int consecutiveSpikes) {
        String message = getAlertMessage("alerts.messages.lag-spike")
                .replace("{duration}", String.format("%.2f", lagSpike.getDuration()))
                .replace("{tick_time}", String.format("%.2f", lagSpike.getTickTime()));

        broadcastAlert(message + " &7(Consecutive: &e" + consecutiveSpikes + "&7)");
    }

    /**
     * Gets an alert message from configuration with fallback.
     */
    private String getAlertMessage(String key) {
        try {
            return MessageManager.get(key);
        } catch (Exception e) {
            return "&c[Performance Alert] &fPerformance issue detected!";
        }
    }

    /**
     * Broadcasts an alert to console and eligible players.
     */
    private void broadcastAlert(String message) {
        // Send to console if enabled
        if (ConfigManager.shouldSendAlertsToConsole()) {
            LagXpert.getInstance().getLogger().warning(MessageManager.color(message).replaceAll("§[0-9a-fk-or]", ""));
        }

        // Send to players if enabled
        if (ConfigManager.shouldSendAlertsToPlayers()) {
            String permission = ConfigManager.getPlayerAlertPermission();
            String coloredMessage = MessageManager.color(message);

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission(permission)) {
                    player.sendMessage(coloredMessage);
                }
            }
        }
    }

    /**
     * Checks if an alert can be sent based on cooldown.
     */
    private boolean canSendAlert(String alertType, int cooldownSeconds) {
        long currentTime = System.currentTimeMillis();
        Long lastSent = alertCooldowns.get(alertType);

        if (lastSent == null) {
            return true;
        }

        return (currentTime - lastSent) >= (cooldownSeconds * 1000L);
    }

    /**
     * Records that an alert was sent.
     */
    private void recordAlert(String alertType) {
        alertCooldowns.put(alertType, System.currentTimeMillis());
    }

    /**
     * Creates a performance snapshot for analytics.
     */
    private void createPerformanceSnapshot() {
        if (!ConfigManager.isTPSHistoryEnabled()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        int snapshotInterval = ConfigManager.getTPSSnapshotIntervalSeconds() * 1000;

        if (currentTime - lastSnapshotTime >= snapshotInterval) {
            PerformanceSnapshot snapshot = new PerformanceSnapshot(
                    currentTime,
                    TPSMonitor.getCurrentTPS(),
                    currentMemoryUsage,
                    totalChunksLoaded.get(),
                    TPSMonitor.getAverageTickTime()
            );

            synchronized (performanceHistory) {
                performanceHistory.add(snapshot);

                // Cleanup old snapshots if auto-cleanup is enabled
                if (ConfigManager.isTPSAutoCleanup()) {
                    int maxRecords = ConfigManager.getTPSMaxRecords();
                    while (performanceHistory.size() > maxRecords) {
                        performanceHistory.remove(0);
                    }
                }
            }

            lastSnapshotTime = currentTime;
        }
    }

    /**
     * Gets the current chunk loading rate (chunks per minute).
     */
    private int getCurrentChunkLoadingRate() {
        synchronized (chunkLoadHistory) {
            return chunkLoadHistory.size();
        }
    }

    // Public getters for performance data
    public static double getCurrentMemoryUsage() {
        return instance != null ? instance.currentMemoryUsage : 0.0;
    }

    public static double getMaxMemoryUsage() {
        return instance != null ? instance.maxMemoryUsage : 0.0;
    }

    public static long getTotalMemory() {
        return instance != null ? instance.totalMemory : 0L;
    }

    public static long getUsedMemory() {
        return instance != null ? instance.usedMemory : 0L;
    }

    public static long getFreeMemory() {
        return instance != null ? instance.freeMemory : 0L;
    }

    public static long getTotalChunksLoaded() {
        return instance != null ? instance.totalChunksLoaded.get() : 0L;
    }

    public static int getChunkLoadingRate() {
        return instance != null ? instance.getCurrentChunkLoadingRate() : 0;
    }

    public static List<PerformanceSnapshot> getPerformanceHistory() {
        if (instance == null) {
            return Collections.emptyList();
        }
        synchronized (instance.performanceHistory) {
            return new ArrayList<>(instance.performanceHistory);
        }
    }

    /**
     * Gets comprehensive performance statistics.
     */
    public static Map<String, Object> getPerformanceStatistics() {
        Map<String, Object> stats = new HashMap<>();

        if (instance != null) {
            stats.put("memory_usage_percent", instance.currentMemoryUsage);
            stats.put("memory_used_mb", instance.usedMemory / 1024 / 1024);
            stats.put("memory_total_mb", instance.totalMemory / 1024 / 1024);
            stats.put("memory_free_mb", instance.freeMemory / 1024 / 1024);
            stats.put("chunks_loaded", instance.totalChunksLoaded.get());
            stats.put("chunk_loading_rate", instance.getCurrentChunkLoadingRate());
            stats.put("performance_snapshots", instance.performanceHistory.size());
        }

        stats.put("current_tps", TPSMonitor.getCurrentTPS());
        stats.put("average_tick_time", TPSMonitor.getAverageTickTime());
        stats.put("max_tick_time", TPSMonitor.getMaxTickTime());

        return stats;
    }

    /**
     * Resets all performance statistics.
     */
    public static void resetStatistics() {
        if (instance != null) {
            instance.maxMemoryUsage = 0.0;
            instance.chunksLoadedThisMinute.set(0);
            instance.chunksUnloadedThisMinute.set(0);

            synchronized (instance.chunkLoadHistory) {
                instance.chunkLoadHistory.clear();
            }

            synchronized (instance.performanceHistory) {
                instance.performanceHistory.clear();
            }

            instance.alertCooldowns.clear();
        }

        TPSMonitor.resetStatistics();
    }
}