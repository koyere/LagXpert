package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.monitoring.TPSMonitor;
import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Performance History — collects periodic snapshots of server metrics and
 * provides trend analysis for proactive optimization.
 *
 * Stores up to 7 days of 5-minute snapshots in a circular buffer.
 * Provides queries for average TPS by hour, peak player counts,
 * and world-specific entity growth trends.
 *
 * Data is persisted to plugins/LagXpert/performance-history.dat on shutdown
 * and loaded on startup for continuity across restarts.
 */
public class PerformanceHistory {

    private static PerformanceHistory instance;

    /** Floor for the snapshot interval; anything shorter is pointless overhead. */
    private static final long MIN_SNAPSHOT_INTERVAL_MS = 60_000L;

    /** Absolute cap on retained snapshots, protecting memory from a huge config value. */
    private static final int ABSOLUTE_MAX_SNAPSHOTS = 20_000;

    private final LinkedList<ServerSnapshot> snapshots = new LinkedList<>();
    private final Object bufferLock = new Object();
    private long lastSnapshotTime = 0;
    private boolean enabled = true;

    /**
     * Interval between snapshots, from
     * {@code performance-history.snapshot-interval-seconds}.
     *
     * Previously a hardcoded constant, which meant the documented config key did
     * nothing.
     */
    private long snapshotIntervalMs = 300_000L;

    /**
     * Retention window in days, from {@code performance-history.max-history-days}.
     * Also previously ignored.
     */
    private int maxHistoryDays = 7;

    /** Derived from the interval and retention window rather than hardcoded. */
    private int maxSnapshots = 2016;

    /**
     * Immutable snapshot of server state at a point in time.
     */
    public static class ServerSnapshot {
        private final long timestamp;
        private final double tps;
        private final double memoryPercent;
        private final int totalChunksLoaded;
        private final int totalEntities;
        private final int onlinePlayers;
        private final String serverState;

        public ServerSnapshot(long timestamp, double tps, double memoryPercent,
                              int totalChunksLoaded, int totalEntities,
                              int onlinePlayers, String serverState) {
            this.timestamp = timestamp;
            this.tps = tps;
            this.memoryPercent = memoryPercent;
            this.totalChunksLoaded = totalChunksLoaded;
            this.totalEntities = totalEntities;
            this.onlinePlayers = onlinePlayers;
            this.serverState = serverState;
        }

        public long getTimestamp() { return timestamp; }
        public double getTps() { return tps; }
        public double getMemoryPercent() { return memoryPercent; }
        public int getTotalChunksLoaded() { return totalChunksLoaded; }
        public int getTotalEntities() { return totalEntities; }
        public int getOnlinePlayers() { return onlinePlayers; }
        public String getServerState() { return serverState; }

        public int getHourOfDay() {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(timestamp);
            return cal.get(java.util.Calendar.HOUR_OF_DAY);
        }

        public int getDayOfWeek() {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(timestamp);
            return cal.get(java.util.Calendar.DAY_OF_WEEK);
        }
    }

    /**
     * Trend analysis result for a specific metric.
     */
    public static class TrendAnalysis {
        private final String metric;
        private final double currentValue;
        private final double hourlyChange;
        private final double projected24h;
        private final String direction; // "increasing", "decreasing", "stable"

        public TrendAnalysis(String metric, double currentValue, double hourlyChange,
                             double projected24h, String direction) {
            this.metric = metric;
            this.currentValue = currentValue;
            this.hourlyChange = hourlyChange;
            this.projected24h = projected24h;
            this.direction = direction;
        }

        public String getMetric() { return metric; }
        public double getCurrentValue() { return currentValue; }
        public double getHourlyChange() { return hourlyChange; }
        public double getProjected24h() { return projected24h; }
        public String getDirection() { return direction; }
    }

    private PerformanceHistory() {
        loadConfig();
        loadFromDisk();
    }

    public static PerformanceHistory getInstance() {
        if (instance == null) {
            instance = new PerformanceHistory();
        }
        return instance;
    }

    /**
     * Reads history settings from config.yml. Public so that
     * {@code /lagxpert reload} can refresh them without a restart.
     */
    public void loadConfig() {
        java.io.File configFile = new java.io.File(
                LagXpert.getInstance().getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            recalculateCapacity();
            return;
        }

        org.bukkit.configuration.file.FileConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);

        this.enabled = config.getBoolean("performance-history.enabled", true);

        long intervalSeconds = config.getLong("performance-history.snapshot-interval-seconds", 300);
        long requestedIntervalMs = intervalSeconds * 1000L;
        if (requestedIntervalMs < MIN_SNAPSHOT_INTERVAL_MS) {
            LagXpert.getInstance().getLogger().warning(
                    "[PerformanceHistory] snapshot-interval-seconds is " + intervalSeconds +
                            "s, which is below the 60s minimum. Using 60s.");
            requestedIntervalMs = MIN_SNAPSHOT_INTERVAL_MS;
        }
        this.snapshotIntervalMs = requestedIntervalMs;

        this.maxHistoryDays = Math.max(1, config.getInt("performance-history.max-history-days", 7));

        recalculateCapacity();
    }

    /**
     * Derives the snapshot capacity from the interval and retention window, then
     * trims any excess that a shortened window has made obsolete.
     */
    private void recalculateCapacity() {
        long snapshotsPerDay = Math.max(1L, 86_400_000L / snapshotIntervalMs);
        long computed = snapshotsPerDay * maxHistoryDays;

        this.maxSnapshots = (int) Math.max(1, Math.min(ABSOLUTE_MAX_SNAPSHOTS, computed));

        // A reload that lowers retention should take effect immediately rather
        // than waiting for the buffer to churn through the old entries.
        synchronized (bufferLock) {
            while (snapshots.size() > maxSnapshots) {
                snapshots.removeFirst();
            }
        }
    }

    /**
     * Returns the interval between snapshots in server ticks, for scheduling.
     */
    public long getSnapshotIntervalTicks() {
        return Math.max(20L, snapshotIntervalMs / 50L);
    }

    public int getMaxSnapshots() {
        return maxSnapshots;
    }

    public int getMaxHistoryDays() {
        return maxHistoryDays;
    }

    /**
     * Called periodically (e.g., every 5 minutes) to record a snapshot.
     * Should be called from a scheduled task.
     */
    public void recordSnapshot() {
        if (!enabled) return;

        long now = System.currentTimeMillis();
        if (now - lastSnapshotTime < snapshotIntervalMs) return;
        lastSnapshotTime = now;

        int totalChunks = 0;
        java.util.List<World> worlds = Bukkit.getWorlds();
        for (World world : worlds) {
            totalChunks += world.getLoadedChunks().length;
        }

        final int chunkCount = totalChunks;

        // Entity counting walks loaded chunks under region dispatch rather than
        // calling world.getEntities(), which reaches across region boundaries and
        // is therefore not safe on Folia. The snapshot is recorded in the callback.
        me.koyere.lagxpert.utils.RegionizedSweeper.countEntities(worlds, totalEntities ->
                storeSnapshot(now, chunkCount, totalEntities));
    }

    /**
     * Records a completed snapshot into the ring buffer.
     */
    private void storeSnapshot(long timestamp, int totalChunks, int totalEntities) {
        ServerSnapshot snapshot = new ServerSnapshot(
                timestamp,
                TPSMonitor.getShortTermTPS(),
                getMemoryPercent(),
                totalChunks,
                totalEntities,
                Bukkit.getOnlinePlayers().size(),
                EmergencyController.getInstance().getCurrentState().name()
        );

        synchronized (bufferLock) {
            snapshots.addLast(snapshot);
            while (snapshots.size() > maxSnapshots) {
                snapshots.removeFirst();
            }
        }
    }

    private double getMemoryPercent() {
        long max = Runtime.getRuntime().maxMemory();
        if (max <= 0) return 0;
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return (double) used / max * 100.0;
    }

    /**
     * Returns average TPS for a specific hour of day (0-23).
     */
    public double getAverageTpsForHour(int hour) {
        double sum = 0;
        int count = 0;
        synchronized (bufferLock) {
            for (ServerSnapshot s : snapshots) {
                if (s.getHourOfDay() == hour) {
                    sum += s.getTps();
                    count++;
                }
            }
        }
        return count > 0 ? sum / count : 20.0;
    }

    /**
     * Returns peak player count observed.
     */
    public int getPeakPlayerCount() {
        int peak = 0;
        synchronized (bufferLock) {
            for (ServerSnapshot s : snapshots) {
                if (s.getOnlinePlayers() > peak) {
                    peak = s.getOnlinePlayers();
                }
            }
        }
        return peak;
    }

    /**
     * Returns average player count for a specific hour.
     */
    public double getAveragePlayersForHour(int hour) {
        double sum = 0;
        int count = 0;
        synchronized (bufferLock) {
            for (ServerSnapshot s : snapshots) {
                if (s.getHourOfDay() == hour) {
                    sum += s.getOnlinePlayers();
                    count++;
                }
            }
        }
        return count > 0 ? sum / count : 0;
    }

    /**
     * Analyzes entity count trend over the last N hours.
     * Positive hourly change = entities growing (potential issue).
     */
    public TrendAnalysis getEntityTrend(int hoursBack) {
        long cutoff = System.currentTimeMillis() - (hoursBack * 3600_000L);
        List<ServerSnapshot> recent = new ArrayList<>();

        synchronized (bufferLock) {
            for (ServerSnapshot s : snapshots) {
                if (s.getTimestamp() >= cutoff) {
                    recent.add(s);
                }
            }
        }

        if (recent.size() < 2) {
            return new TrendAnalysis("entities", 0, 0, 0, "stable");
        }

        double firstEntities = recent.get(0).getTotalEntities();
        double lastEntities = recent.get(recent.size() - 1).getTotalEntities();
        double hoursElapsed = (recent.get(recent.size() - 1).getTimestamp() -
                recent.get(0).getTimestamp()) / 3600_000.0;

        double hourlyChange = hoursElapsed > 0 ?
                (lastEntities - firstEntities) / hoursElapsed : 0;
        double projected24h = lastEntities + (hourlyChange * 24);

        String direction;
        if (Math.abs(hourlyChange) < 50) direction = "stable";
        else if (hourlyChange > 0) direction = "increasing";
        else direction = "decreasing";

        return new TrendAnalysis("entities", lastEntities, hourlyChange,
                projected24h, direction);
    }

    /**
     * Returns the hour with the lowest average TPS (peak lag hour).
     */
    public int getPeakLagHour() {
        int worstHour = 0;
        double worstTps = 20.0;
        for (int h = 0; h < 24; h++) {
            double avg = getAverageTpsForHour(h);
            if (avg < worstTps && avg > 0) {
                worstTps = avg;
                worstHour = h;
            }
        }
        return worstHour;
    }

    /**
     * Returns all snapshots (for export/API).
     */
    public List<ServerSnapshot> getAllSnapshots() {
        synchronized (bufferLock) {
            return new ArrayList<>(snapshots);
        }
    }

    /**
     * Returns the number of stored snapshots.
     */
    public int getSnapshotCount() {
        synchronized (bufferLock) {
            return snapshots.size();
        }
    }

    /**
     * Persists snapshots to disk as CSV.
     */
    public void saveToDisk() {
        File file = new File(LagXpert.getInstance().getDataFolder(), "performance-history.dat");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("timestamp,tps,memory,chunks,entities,players,state\n");
            synchronized (bufferLock) {
                for (ServerSnapshot s : snapshots) {
                    writer.write(String.format("%d,%.2f,%.1f,%d,%d,%d,%s\n",
                            s.getTimestamp(), s.getTps(), s.getMemoryPercent(),
                            s.getTotalChunksLoaded(), s.getTotalEntities(),
                            s.getOnlinePlayers(), s.getServerState()));
                }
            }
        } catch (IOException e) {
            LagXpert.getInstance().getLogger().warning(
                    "[PerformanceHistory] Failed to save history: " + e.getMessage());
        }
    }

    /**
     * Loads snapshots from disk on startup.
     */
    private void loadFromDisk() {
        File file = new File(LagXpert.getInstance().getDataFolder(), "performance-history.dat");
        if (!file.exists()) return;

        try {
            List<String> lines = Files.readAllLines(file.toPath());
            synchronized (bufferLock) {
                snapshots.clear();
                for (int i = 1; i < lines.size() && snapshots.size() < maxSnapshots; i++) {
                    String[] parts = lines.get(i).split(",");
                    if (parts.length >= 7) {
                        try {
                            ServerSnapshot s = new ServerSnapshot(
                                    Long.parseLong(parts[0]),
                                    Double.parseDouble(parts[1]),
                                    Double.parseDouble(parts[2]),
                                    Integer.parseInt(parts[3]),
                                    Integer.parseInt(parts[4]),
                                    Integer.parseInt(parts[5]),
                                    parts[6]
                            );
                            snapshots.addLast(s);
                        } catch (NumberFormatException ignored) {
                            // Skip corrupted lines
                        }
                    }
                }
            }
            LagXpert.getInstance().getLogger().info(
                    "[PerformanceHistory] Loaded " + snapshots.size() + " snapshots from disk.");
        } catch (IOException e) {
            LagXpert.getInstance().getLogger().warning(
                    "[PerformanceHistory] Failed to load history: " + e.getMessage());
        }
    }

    /**
     * Returns statistics for API/commands.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("enabled", enabled);
        stats.put("snapshots", getSnapshotCount());
        stats.put("peak_players", getPeakPlayerCount());
        stats.put("peak_lag_hour", getPeakLagHour());
        stats.put("peak_lag_hour_tps", String.format("%.2f", getAverageTpsForHour(getPeakLagHour())));

        TrendAnalysis trend = getEntityTrend(6);
        stats.put("entity_trend_direction", trend.getDirection());
        stats.put("entity_trend_hourly", String.format("%.0f", trend.getHourlyChange()));

        return stats;
    }

    /**
     * Resets all stored snapshots.
     */
    public void reset() {
        synchronized (bufferLock) {
            snapshots.clear();
        }
        lastSnapshotTime = 0;
    }
}
