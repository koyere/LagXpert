package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Action Logger — audit trail for every corrective action LagXpert takes.
 *
 * Stores a circular buffer of recent actions (capped at maxEntries).
 * Thread-safe for concurrent writes from different systems.
 *
 * Actions can be queried by type, world, or time range for commands/GUI/API.
 * Supports auto-rotation: when the buffer is full, oldest entries are dropped.
 */
public class ActionLogger {

    private static ActionLogger instance;

    private int maxEntries = 10000;

    // Thread-safe circular buffer using LinkedList with external synchronization
    private final LinkedList<ActionRecord> entries = new LinkedList<>();
    private final Object bufferLock = new Object();

    private final AtomicLong totalActionsLogged = new AtomicLong(0);
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // Counter per action type for statistics
    private final Map<ActionType, AtomicLong> typeCounters = new ConcurrentHashMap<>();

    /**
     * Enumeration of all possible corrective action types.
     */
    public enum ActionType {
        // State changes
        STATE_TRANSITION,

        // Entity actions
        MOB_REMOVED,
        MOB_REMOVED_BULK,
        ENTITY_CLEANED,
        ENTITY_CLEANED_BULK,
        SPAWN_BLOCKED,
        PLACEMENT_BLOCKED,
        VEHICLE_REMOVED,
        VEHICLE_BLOCKED,

        // Item actions
        ITEM_CLEARED,
        ITEM_CLEARED_BULK,

        // Redstone actions
        REDSTONE_DISABLED,
        REDSTONE_CIRCUIT_BROKEN,

        // Chunk actions
        CHUNK_UNLOADED,
        CHUNK_PRELOADED,
        CACHE_CLEARED,

        // Explosion actions
        EXPLOSION_LIMITED,
        EXPLOSION_BLOCKED,

        // Ability actions
        ABILITY_BLOCKED,

        // Manual actions
        MANUAL_OPTIMIZE,
        MANUAL_CLEAR,
        MANUAL_RELOAD,
        CONFIG_CHANGED,

        // Emergency actions
        EMERGENCY_ACTIVATED,
        EMERGENCY_DEACTIVATED,
        EMERGENCY_COMMAND_EXECUTED,

        // Misc
        AI_DISABLED,
        AI_ENABLED,
        UNKNOWN
    }

    /**
     * Immutable record of a single corrective action.
     */
    public static class ActionRecord {
        private final ActionType type;
        private final String world;
        private final String chunkKey;
        private final String detail;
        private final long timestamp;
        private final int count;
        private final String triggeredBy;
        private final boolean successful;
        private final long durationMs;

        public ActionRecord(ActionType type, String world, String chunkKey,
                            String detail, int count, String triggeredBy,
                            boolean successful, long durationMs) {
            this.type = type;
            this.world = world != null ? world : "-";
            this.chunkKey = chunkKey != null ? chunkKey : "-";
            this.detail = detail != null ? detail : "";
            this.timestamp = System.currentTimeMillis();
            this.count = count;
            this.triggeredBy = triggeredBy != null ? triggeredBy : "auto";
            this.successful = successful;
            this.durationMs = durationMs;
        }

        public ActionType getType() { return type; }
        public String getWorld() { return world; }
        public String getChunkKey() { return chunkKey; }
        public String getDetail() { return detail; }
        public long getTimestamp() { return timestamp; }
        public int getCount() { return count; }
        public String getTriggeredBy() { return triggeredBy; }
        public boolean isSuccessful() { return successful; }
        public long getDurationMs() { return durationMs; }
    }

    private ActionLogger() {
        // Initialize type counters
        for (ActionType type : ActionType.values()) {
            typeCounters.put(type, new AtomicLong(0));
        }
    }

    public static ActionLogger getInstance() {
        if (instance == null) {
            instance = new ActionLogger();
        }
        return instance;
    }

    /**
     * Marks the logger as initialized. Must be called once during plugin startup.
     */
    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            loadConfig();
            LagXpert.getInstance().getLogger().info(
                    "[ActionLogger] Initialized with max " + maxEntries + " entries.");
        }
    }

    private void loadConfig() {
        java.io.File configFile = new java.io.File(
                LagXpert.getInstance().getDataFolder(), "config.yml");
        if (configFile.exists()) {
            org.bukkit.configuration.file.FileConfiguration config =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
            this.maxEntries = config.getInt("action-logger.max-entries", 10000);
        }
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Logs a single action to the audit trail.
     *
     * @param type        The type of action taken
     * @param world       The world name (nullable)
     * @param chunkKey    The chunk identifier (nullable, format: "world_x_z")
     * @param detail      Human-readable description
     * @param count       How many entities/blocks were affected
     * @param triggeredBy What triggered this action ("auto", "emergency", "player:Name")
     * @param successful  Whether the action completed successfully
     * @param durationMs  How long the action took in milliseconds
     */
    public void log(ActionType type, String world, String chunkKey,
                    String detail, int count, String triggeredBy,
                    boolean successful, long durationMs) {
        ActionRecord record = new ActionRecord(
                type, world, chunkKey, detail, count,
                triggeredBy, successful, durationMs);

        synchronized (bufferLock) {
            entries.addLast(record);
            while (entries.size() > maxEntries) {
                entries.removeFirst();
            }
        }

        totalActionsLogged.incrementAndGet();

        AtomicLong counter = typeCounters.get(type);
        if (counter != null) {
            counter.incrementAndGet();
        }
    }

    /**
     * Convenience: log a successful action with default triggeredBy="auto".
     */
    public void log(ActionType type, String world, int count) {
        log(type, world, null, null, count, "auto", true, 0);
    }

    /**
     * Convenience: log a state transition.
     */
    public void logStateTransition(String oldState, String newState, String reason) {
        log(ActionType.STATE_TRANSITION, null, null,
                oldState + " → " + newState + " (" + reason + ")", 0,
                "system", true, 0);
    }

    /**
     * Returns the most recent N action records (newest first).
     */
    public List<ActionRecord> getRecent(int count) {
        synchronized (bufferLock) {
            int size = entries.size();
            if (size == 0) return Collections.emptyList();

            int fromIndex = Math.max(0, size - count);
            List<ActionRecord> slice = new ArrayList<>(entries.subList(fromIndex, size));
            Collections.reverse(slice);
            return slice;
        }
    }

    /**
     * Returns the last N records as-is (oldest first), useful for exports.
     */
    public List<ActionRecord> getLastN(int count) {
        synchronized (bufferLock) {
            int size = entries.size();
            if (size == 0) return Collections.emptyList();

            int fromIndex = Math.max(0, size - count);
            return new ArrayList<>(entries.subList(fromIndex, size));
        }
    }

    /**
     * Returns all records since the given timestamp.
     */
    public List<ActionRecord> getSince(long timestampMs) {
        List<ActionRecord> result = new ArrayList<>();
        synchronized (bufferLock) {
            for (ActionRecord record : entries) {
                if (record.getTimestamp() >= timestampMs) {
                    result.add(record);
                }
            }
        }
        return result;
    }

    /**
     * Returns the number of actions logged for a specific type.
     */
    public long getCount(ActionType type) {
        AtomicLong counter = typeCounters.get(type);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Returns total actions logged since startup.
     */
    public long getTotalActionsLogged() {
        return totalActionsLogged.get();
    }

    /**
     * Returns current buffer size.
     */
    public int getBufferSize() {
        synchronized (bufferLock) {
            return entries.size();
        }
    }

    /**
     * Returns aggregate statistics for commands/API.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("total_actions", totalActionsLogged.get());
        stats.put("buffer_size", getBufferSize());
        stats.put("max_entries", maxEntries);

        Map<String, Long> byType = new ConcurrentHashMap<>();
        for (Map.Entry<ActionType, AtomicLong> entry : typeCounters.entrySet()) {
            long count = entry.getValue().get();
            if (count > 0) {
                byType.put(entry.getKey().name(), count);
            }
        }
        stats.put("actions_by_type", byType);

        return stats;
    }

    /**
     * Clears all stored records and resets counters.
     */
    public void reset() {
        synchronized (bufferLock) {
            entries.clear();
        }
        totalActionsLogged.set(0);
        for (AtomicLong counter : typeCounters.values()) {
            counter.set(0);
        }
        LagXpert.getInstance().getLogger().info("[ActionLogger] All records and counters reset.");
    }

    /**
     * Shuts down the logger gracefully.
     */
    public void shutdown() {
        LagXpert.getInstance().getLogger().info(
                "[ActionLogger] Shutdown. Total actions logged: " + totalActionsLogged.get());
    }
}
