package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Emergency Controller — reactive state machine that replaces the inert LagShield.
 *
 * Evaluates server health metrics (TPS, memory, player count) on every tick
 * and transitions between states: NORMAL, WARNING, CRITICAL, EMERGENCY.
 *
 * Each state applies graduated corrective responses that are queried by other
 * systems (EntityListener, SmartMobManager, ChunkPreloader, etc.) to dynamically
 * adjust their limits and behavior.
 *
 * Hysteresis prevents state flapping:
 *   - Escalation requires confirmationEscalate consecutive bad readings
 *   - De-escalation requires confirmationDeescalate consecutive good readings
 *   - Minimum state duration prevents rapid cycling
 *
 * Thread-safe: all state fields use AtomicReference / volatile.
 */
public class EmergencyController {

    private static EmergencyController instance;

    // Server health state
    public enum ServerState {
        NORMAL,
        WARNING,
        CRITICAL,
        EMERGENCY
    }

    // Hysteresis constants (loaded from config)
    private int confirmationEscalate = 3;
    private int confirmationDeescalate = 5;
    private long minStateDurationMs = 10_000L;

    // State tracking
    private final AtomicReference<ServerState> currentState = new AtomicReference<>(ServerState.NORMAL);
    private final AtomicLong stateEnteredAt = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger consecutiveBadReadings = new AtomicInteger(0);
    private final AtomicInteger consecutiveGoodReadings = new AtomicInteger(0);
    private final AtomicLong lastEvaluationTime = new AtomicLong(0);

    // Cached response values (updated on state transition)
    private volatile double cachedMobCapMultiplier = 1.0;
    private volatile boolean cachedBlockNaturalSpawns = false;
    private volatile boolean cachedForceItemCleanup = false;
    private volatile boolean cachedAggressiveChunkUnload = false;
    private volatile boolean cachedPauseChunkPreloader = false;
    private volatile boolean cachedDisableRedstoneClocks = false;
    private volatile int cachedAIDistanceThreshold = 64;
    private volatile boolean cachedBroadcastAlert = false;
    private volatile long cachedUnloadInactivityMinutes = 15;
    private volatile boolean cachedFreezeAllAI = false;

    // Config values (loaded once, can be refreshed)
    private boolean enabled;
    private boolean skipWhenNoPlayers;
    private int minPlayersForEmergency;
    private boolean allowForceNormal;
    private List<String> emergencyCommands;

    // Thresholds
    private double criticalTps;
    private double warningTps;
    private double recoveryTps;
    private double criticalRam;
    private double warningRam;
    private double recoveryRam;

    // Per-state response configs
    private final Map<ServerState, ResponseConfig> responseConfigs = new ConcurrentHashMap<>();

    // State change listeners
    private final List<StateChangeListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Listener interface for systems that need to react to state changes.
     */
    public interface StateChangeListener {
        void onStateChanged(ServerState oldState, ServerState newState);
    }

    /**
     * Internal holder for per-state response configuration.
     */
    private static class ResponseConfig {
        double mobCapMultiplier = 1.0;
        boolean blockNaturalSpawns = false;
        boolean forceItemCleanup = false;
        boolean aggressiveChunkUnload = false;
        boolean pauseChunkPreloader = false;
        boolean disableRedstoneClocks = false;
        int aiDistanceThreshold = 64;
        boolean broadcastAlert = false;
        long unloadInactivityMinutes = 15;
        boolean freezeAllAI = false;
    }

    private EmergencyController() {
        loadConfig();
    }

    public static EmergencyController getInstance() {
        if (instance == null) {
            instance = new EmergencyController();
        }
        return instance;
    }

    /**
     * Loads configuration from emergency-controller.yml.
     * Safe to call multiple times for reload.
     */
    public void loadConfig() {
        java.io.File file = new java.io.File(LagXpert.getInstance().getDataFolder(), "emergency-controller.yml");
        if (!file.exists()) {
            this.enabled = false;
            return;
        }

        org.bukkit.configuration.file.FileConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        this.enabled = config.getBoolean("enabled", true);

        // Stability settings
        this.skipWhenNoPlayers = config.getBoolean("stability.skip-when-no-players-online", true);
        this.minPlayersForEmergency = config.getInt("stability.min-players-for-emergency", 5);
        this.allowForceNormal = config.getBoolean("manual.allow-force-normal", true);

        // Hysteresis settings from config
        this.confirmationEscalate = config.getInt("stability.escalation-confirmation-readings", 3);
        this.confirmationDeescalate = config.getInt("stability.de-escalation-confirmation-readings", 5);
        this.minStateDurationMs = config.getLong("stability.min-state-duration-seconds", 10) * 1000L;

        // Thresholds
        this.criticalTps = config.getDouble("thresholds.tps.critical", 15.0);
        this.warningTps = config.getDouble("thresholds.tps.warning", 18.0);
        this.recoveryTps = config.getDouble("thresholds.tps.recovery", 19.0);
        this.criticalRam = config.getDouble("thresholds.ram.critical", 90.0);
        this.warningRam = config.getDouble("thresholds.ram.warning", 85.0);
        this.recoveryRam = config.getDouble("thresholds.ram.recovery", 75.0);

        // Emergency commands
        this.emergencyCommands = config.getStringList("responses.emergency.emergency-commands");

        // Load per-state response configs
        loadResponseConfig(config, ServerState.WARNING, "responses.warning");
        loadResponseConfig(config, ServerState.CRITICAL, "responses.critical");
        loadResponseConfig(config, ServerState.EMERGENCY, "responses.emergency");

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info(
                    "[EmergencyController] Configuration loaded. Enabled: " + enabled);
        }
    }

    private void loadResponseConfig(org.bukkit.configuration.file.FileConfiguration config,
                                     ServerState state, String path) {
        ResponseConfig rc = new ResponseConfig();
        rc.mobCapMultiplier = config.getDouble(path + ".mob-cap-multiplier", 1.0);
        rc.blockNaturalSpawns = config.getBoolean(path + ".block-natural-spawns", false);
        rc.forceItemCleanup = config.getBoolean(path + ".force-item-cleanup", false);
        rc.aggressiveChunkUnload = config.getBoolean(path + ".aggressive-chunk-unload", false);
        rc.pauseChunkPreloader = config.getBoolean(path + ".pause-chunk-preloader", false);
        rc.disableRedstoneClocks = config.getBoolean(path + ".disable-redstone-clocks", false);
        rc.aiDistanceThreshold = config.getInt(path + ".ai-distance-threshold", 64);
        rc.broadcastAlert = config.getBoolean(path + ".broadcast-alert", false);
        rc.unloadInactivityMinutes = config.getLong(path + ".unload-inactivity-minutes", 15);
        rc.freezeAllAI = config.getBoolean(path + ".freeze-all-ai", false);
        responseConfigs.put(state, rc);
    }

    /**
     * Main evaluation entry point. Called by TPSMonitor on every monitoring cycle.
     *
     * @param currentTps         Instant TPS value
     * @param memoryUsagePercent Current heap usage as percentage (0-100)
     * @param onlinePlayerCount  Number of players currently online
     * @param activePlayerCount  Number of players who moved recently (for smarter empty detection)
     */
    public void evaluate(double currentTps, double memoryUsagePercent,
                         int onlinePlayerCount, int activePlayerCount) {
        if (!enabled) {
            return;
        }

        lastEvaluationTime.set(System.currentTimeMillis());

        // Skip when no players online (false positives during restarts / off-hours)
        if (skipWhenNoPlayers && onlinePlayerCount == 0) {
            if (currentState.get() != ServerState.NORMAL) {
                transitionTo(ServerState.NORMAL, "no players online");
            }
            consecutiveBadReadings.set(0);
            consecutiveGoodReadings.set(confirmationDeescalate);
            return;
        }

        // Determine target state based on current metrics
        ServerState targetState = computeTargetState(currentTps, memoryUsagePercent);

        // EMERGENCY requires minimum player count to avoid false positives
        if (targetState == ServerState.EMERGENCY && onlinePlayerCount < minPlayersForEmergency) {
            targetState = ServerState.CRITICAL;
        }

        ServerState current = currentState.get();

        if (targetState.ordinal() > current.ordinal()) {
            // Escalation needed
            consecutiveGoodReadings.set(0);
            int badCount = consecutiveBadReadings.incrementAndGet();
            if (badCount >= confirmationEscalate) {
                transitionIfStable(targetState, "metrics degraded");
            }
        } else if (targetState.ordinal() < current.ordinal()) {
            // De-escalation possible
            consecutiveBadReadings.set(0);
            int goodCount = consecutiveGoodReadings.incrementAndGet();
            if (goodCount >= confirmationDeescalate) {
                transitionIfStable(targetState, "metrics recovered");
            }
        } else {
            // Stable in current state
            consecutiveBadReadings.set(0);
            consecutiveGoodReadings.set(0);
        }
    }

    /**
     * Computes the target state purely from current metric values.
     */
    private ServerState computeTargetState(double tps, double ramPercent) {
        boolean tpsCritical = tps < criticalTps && tps > 0;
        boolean tpsWarning = tps < warningTps && tps > 0;
        boolean ramCritical = ramPercent > criticalRam;
        boolean ramWarning = ramPercent > warningRam;
        boolean recovered = tps >= recoveryTps && ramPercent <= recoveryRam;

        if (recovered) {
            return ServerState.NORMAL;
        }
        if (tpsCritical || ramCritical) {
            return ServerState.CRITICAL;
        }
        if (tpsWarning || ramWarning) {
            return ServerState.WARNING;
        }
        return ServerState.NORMAL;
    }

    /**
     * Transitions to target state only if minimum state duration has elapsed.
     */
    private void transitionIfStable(ServerState target, String reason) {
        long elapsed = System.currentTimeMillis() - stateEnteredAt.get();
        if (elapsed < minStateDurationMs) {
            return; // Wait for minimum state duration
        }
        transitionTo(target, reason);
    }

    /**
     * Performs the actual state transition.
     */
    private void transitionTo(ServerState newState, String reason) {
        ServerState oldState = currentState.getAndSet(newState);
        if (oldState == newState) {
            return;
        }

        stateEnteredAt.set(System.currentTimeMillis());
        applyResponseConfig(newState);

        // Log the transition
        String logMsg = String.format(
                "[EmergencyController] State transition: %s → %s (reason: %s, TPS: %.2f)",
                oldState.name(), newState.name(), reason,
                me.koyere.lagxpert.monitoring.TPSMonitor.getCurrentTPS());
        LagXpert.getInstance().getLogger().info(logMsg);

        // Log to ActionLogger if available
        if (ActionLogger.getInstance().isInitialized()) {
            ActionLogger.getInstance().logStateTransition(oldState.name(), newState.name(), reason);
        }

        // Broadcast if configured for this state
        if (cachedBroadcastAlert) {
            broadcastStateChange(oldState, newState);
        }

        // Execute emergency commands on entering EMERGENCY or CRITICAL
        if (newState == ServerState.EMERGENCY && emergencyCommands != null) {
            executeEmergencyCommands();
        }

        // Notify listeners
        for (StateChangeListener listener : listeners) {
            try {
                listener.onStateChanged(oldState, newState);
            } catch (Exception e) {
                LagXpert.getInstance().getLogger().warning(
                        "[EmergencyController] Listener error: " + e.getMessage());
            }
        }

        // Reset hysteresis counters
        consecutiveBadReadings.set(0);
        consecutiveGoodReadings.set(0);
    }

    /**
     * Applies the response configuration for the given state to cached fields.
     */
    private void applyResponseConfig(ServerState state) {
        ResponseConfig rc = responseConfigs.get(state);
        if (rc == null) {
            // NORMAL or unconfigured state: reset all to defaults
            cachedMobCapMultiplier = 1.0;
            cachedBlockNaturalSpawns = false;
            cachedForceItemCleanup = false;
            cachedAggressiveChunkUnload = false;
            cachedPauseChunkPreloader = false;
            cachedDisableRedstoneClocks = false;
            cachedAIDistanceThreshold = 64;
            cachedBroadcastAlert = false;
            cachedUnloadInactivityMinutes = 15;
            cachedFreezeAllAI = false;
            return;
        }

        cachedMobCapMultiplier = rc.mobCapMultiplier;
        cachedBlockNaturalSpawns = rc.blockNaturalSpawns;
        cachedForceItemCleanup = rc.forceItemCleanup;
        cachedAggressiveChunkUnload = rc.aggressiveChunkUnload;
        cachedPauseChunkPreloader = rc.pauseChunkPreloader;
        cachedDisableRedstoneClocks = rc.disableRedstoneClocks;
        cachedAIDistanceThreshold = rc.aiDistanceThreshold;
        cachedBroadcastAlert = rc.broadcastAlert;
        cachedUnloadInactivityMinutes = rc.unloadInactivityMinutes;
        cachedFreezeAllAI = rc.freezeAllAI;
    }

    /**
     * Broadcasts state change to players with permission and console.
     */
    private void broadcastStateChange(ServerState oldState, ServerState newState) {
        String alertType;
        String messagePath;

        if (newState.ordinal() > oldState.ordinal()) {
            switch (newState) {
                case WARNING:
                    alertType = "state-warning";
                    messagePath = "alerts.messages.emergency-controller.warning-activated";
                    break;
                case CRITICAL:
                    alertType = "state-critical";
                    messagePath = "alerts.messages.emergency-controller.critical-activated";
                    break;
                case EMERGENCY:
                    alertType = "state-emergency";
                    messagePath = "alerts.messages.emergency-controller.emergency-activated";
                    break;
                default:
                    return;
            }
        } else {
            alertType = "state-recovery";
            messagePath = "alerts.messages.emergency-controller.recovery-activated";
        }

        String contextKey = newState.name();

        AlertPipeline.AlertContext ctx = AlertPipeline.AlertContext
                .builder(AlertPipeline.AlertLevel.WARNING, alertType)
                .messagePath(messagePath)
                .contextKey(contextKey)
                .target(AlertPipeline.AlertTarget.PLAYERS_WITH_PERMISSION)
                .permission("lagxpert.emergency.notify")
                .logToConsole(true)
                .logToActionLogger(true)
                .build();

        AlertPipeline.getInstance().send(ctx);
    }

    /**
     * Executes configured emergency commands on the server.
     */
    private void executeEmergencyCommands() {
        if (emergencyCommands == null || emergencyCommands.isEmpty()) {
            return;
        }

        LagXpert.getInstance().getLogger().warning(
                "[EmergencyController] Executing " + emergencyCommands.size() + " emergency command(s)...");

        for (String command : emergencyCommands) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (Exception e) {
                LagXpert.getInstance().getLogger().warning(
                        "[EmergencyController] Failed to execute emergency command: " + command +
                                " - " + e.getMessage());
            }
        }
    }

    /**
     * Force-transitions to NORMAL state (admin panic button).
     * Resets all hysteresis counters.
     *
     * @return true if transition occurred
     */
    public boolean forceNormal() {
        if (!allowForceNormal) {
            return false;
        }
        LagXpert.getInstance().getLogger().info(
                "[EmergencyController] Force-normal triggered by admin.");
        consecutiveBadReadings.set(0);
        consecutiveGoodReadings.set(confirmationDeescalate);
        transitionTo(ServerState.NORMAL, "manual force-normal");
        return true;
    }

    /**
     * Registers a listener for state changes.
     */
    public void addStateChangeListener(StateChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a previously registered state change listener.
     */
    public void removeStateChangeListener(StateChangeListener listener) {
        listeners.remove(listener);
    }

    // ─── Public Query Methods ───────────────────────────────────────────

    public ServerState getCurrentState() {
        return currentState.get();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the effective mob cap multiplier based on current server state.
     * Called by EntityListener and SmartMobManager to compute dynamic limits.
     *
     * @return multiplier in range (0.0, 1.0], 1.0 = no reduction
     */
    public double getMobCapMultiplier() {
        return enabled ? cachedMobCapMultiplier : 1.0;
    }

    /**
     * Returns the effective mob limit for a world, factoring in state multiplier.
     *
     * @param world the world to check
     * @return effective mob limit
     */
    public int getEffectiveMobLimit(org.bukkit.World world) {
        int baseLimit = ConfigManager.getMaxMobsPerChunk(world);
        return Math.max((int) (baseLimit * getMobCapMultiplier()), 1);
    }

    public boolean shouldBlockNaturalSpawns() {
        return enabled && cachedBlockNaturalSpawns;
    }

    public boolean shouldForceItemCleanup() {
        return enabled && cachedForceItemCleanup;
    }

    public boolean shouldAggressiveChunkUnload() {
        return enabled && cachedAggressiveChunkUnload;
    }

    public boolean shouldPauseChunkPreloader() {
        return enabled && cachedPauseChunkPreloader;
    }

    public boolean shouldDisableRedstoneClocks() {
        return enabled && cachedDisableRedstoneClocks;
    }

    public int getAIDistanceThreshold() {
        return enabled ? cachedAIDistanceThreshold : 64;
    }

    public long getUnloadInactivityMinutes() {
        return enabled ? cachedUnloadInactivityMinutes : 15;
    }

    public boolean shouldFreezeAllAI() {
        return enabled && cachedFreezeAllAI;
    }

    public long getLastEvaluationTime() {
        return lastEvaluationTime.get();
    }

    public long getTimeInCurrentStateMs() {
        return System.currentTimeMillis() - stateEnteredAt.get();
    }

    /**
     * Returns a snapshot of current controller status for commands/API.
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("state", currentState.get().name());
        status.put("enabled", enabled);
        status.put("time_in_state_ms", getTimeInCurrentStateMs());
        status.put("mob_cap_multiplier", getMobCapMultiplier());
        status.put("block_natural_spawns", shouldBlockNaturalSpawns());
        status.put("force_item_cleanup", shouldForceItemCleanup());
        status.put("aggressive_chunk_unload", shouldAggressiveChunkUnload());
        status.put("pause_preloader", shouldPauseChunkPreloader());
        status.put("disable_redstone_clocks", shouldDisableRedstoneClocks());
        status.put("ai_distance_threshold", getAIDistanceThreshold());
        status.put("freeze_all_ai", shouldFreezeAllAI());
        return status;
    }

    /**
     * Shuts down the controller, notifying listeners and resetting state.
     */
    public void shutdown() {
        if (currentState.get() != ServerState.NORMAL) {
            transitionTo(ServerState.NORMAL, "plugin shutdown");
        }
        listeners.clear();
        LagXpert.getInstance().getLogger().info("[EmergencyController] Shutdown complete.");
    }
}
