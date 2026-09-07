package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.tasks.ItemCleanerTask;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.SchedulerWrapper;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Executes the one-shot corrective actions that the {@link EmergencyController}
 * requests when the server state changes.
 *
 * The controller itself only decides *what* should happen and exposes that as a
 * set of flags. Something has to actually act on those flags, and that is this
 * class. Before it existed, {@code shouldForceItemCleanup()} and
 * {@code shouldFreezeAllAI()} were configured in emergency-controller.yml,
 * reported by /lagxpert emergency, and then quietly ignored.
 *
 * Responsibilities:
 * <ul>
 *   <li>Force an off-cycle item cleanup when entering a state that requests it.</li>
 *   <li>Freeze mob AI server-wide in the most severe states, and lift the freeze
 *       on recovery without trampling the operator's own mobs.yml AI rules.</li>
 * </ul>
 *
 * Safety properties:
 * <ul>
 *   <li>Every action is dispatched through {@link SchedulerWrapper} so it never
 *       runs on the thread that evaluated the state transition, and never blocks
 *       the controller's listener notification loop.</li>
 *   <li>Forced cleanups are guarded by a CAS latch plus a cooldown, so a state
 *       that flaps cannot stack cleanup cycles on top of each other.</li>
 *   <li>Entity mutation is dispatched per chunk, which is the only form that is
 *       correct on Folia's regionised threading.</li>
 * </ul>
 */
public class EmergencyResponseCoordinator implements EmergencyController.StateChangeListener {

    private static EmergencyResponseCoordinator instance;

    /** Prevents two forced cleanups from overlapping. */
    private final AtomicBoolean forcedCleanupRunning = new AtomicBoolean(false);

    /** Timestamp of the last forced cleanup, for cooldown enforcement. */
    private final AtomicLong lastForcedCleanupAt = new AtomicLong(0L);

    /** Tracks whether an AI freeze is currently applied, so it can be lifted exactly once. */
    private final AtomicBoolean aiFrozen = new AtomicBoolean(false);

    /** Diagnostics counters surfaced through the status command and GUI. */
    private final AtomicInteger forcedCleanupCount = new AtomicInteger(0);
    private final AtomicInteger aiFreezeCount = new AtomicInteger(0);

    private volatile boolean registered = false;

    // Configuration
    private long forcedCleanupCooldownMs = 60_000L;
    private boolean forcedCleanupEnabled = true;
    private boolean aiFreezeEnabled = true;

    private EmergencyResponseCoordinator() {
        loadConfig();
    }

    public static EmergencyResponseCoordinator getInstance() {
        if (instance == null) {
            instance = new EmergencyResponseCoordinator();
        }
        return instance;
    }

    /**
     * Reads coordinator tuning from emergency-controller.yml.
     * Safe to call repeatedly for reloads.
     */
    public void loadConfig() {
        java.io.File file = new java.io.File(
                LagXpert.getInstance().getDataFolder(), "emergency-controller.yml");
        if (!file.exists()) {
            return;
        }

        org.bukkit.configuration.file.FileConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        this.forcedCleanupEnabled = config.getBoolean("actions.forced-item-cleanup.enabled", true);
        this.forcedCleanupCooldownMs =
                config.getLong("actions.forced-item-cleanup.cooldown-seconds", 60) * 1000L;
        this.aiFreezeEnabled = config.getBoolean("actions.ai-freeze.enabled", true);
    }

    /**
     * Registers this coordinator with the EmergencyController.
     *
     * Must be called explicitly during plugin enable. Relying on lazy singleton
     * construction is what left the previous generation of listeners dormant.
     */
    public void register() {
        if (registered) {
            return;
        }
        EmergencyController.getInstance().addStateChangeListener(this);
        registered = true;

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info(
                    "[EmergencyResponseCoordinator] Registered as state change listener.");
        }
    }

    /**
     * Reacts to a server state transition.
     *
     * This runs inline on the controller's notification loop, so it must return
     * quickly. All real work is handed off to the scheduler.
     */
    @Override
    public void onStateChanged(EmergencyController.ServerState oldState,
                               EmergencyController.ServerState newState) {
        EmergencyController controller = EmergencyController.getInstance();

        try {
            // ── Forced item cleanup ─────────────────────────────────────────
            // Only on escalation. De-escalating into a state that happens to
            // request cleanup should not trigger another sweep.
            boolean escalating = newState.ordinal() > oldState.ordinal();
            if (escalating && controller.shouldForceItemCleanup()) {
                requestForcedItemCleanup(newState);
            }

            // ── Server-wide AI freeze ───────────────────────────────────────
            if (controller.shouldFreezeAllAI()) {
                applyAiFreeze(newState);
            } else if (aiFrozen.get()) {
                liftAiFreeze(newState);
            }

        } catch (Exception e) {
            // A failure here must never propagate into the controller's loop,
            // or one bad response would stop every other listener from running.
            LagXpert.getInstance().getLogger().warning(
                    "[EmergencyResponseCoordinator] Error handling transition " +
                            oldState + " -> " + newState + ": " + e.getMessage());
        }
    }

    /**
     * Triggers an immediate item cleanup outside the normal schedule.
     *
     * Skips the usual countdown warning: when the server is already critical,
     * waiting ten seconds to announce a cleanup defeats the purpose.
     */
    public void requestForcedItemCleanup(EmergencyController.ServerState state) {
        if (!forcedCleanupEnabled || !ConfigManager.isItemCleanerModuleEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        long last = lastForcedCleanupAt.get();
        if (last != 0L && (now - last) < forcedCleanupCooldownMs) {
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info(
                        "[EmergencyResponseCoordinator] Forced cleanup suppressed by cooldown (" +
                                ((forcedCleanupCooldownMs - (now - last)) / 1000) + "s remaining).");
            }
            return;
        }

        // CAS latch: if a forced cleanup is already in flight, do nothing.
        if (!forcedCleanupRunning.compareAndSet(false, true)) {
            return;
        }
        lastForcedCleanupAt.set(now);

        final long start = System.currentTimeMillis();

        try {
            // The sweep is chunk-dispatched and asynchronous, so the latch is
            // released and the audit entry written from the completion callback.
            ItemCleanerTask.runForcedCleanup("emergency:" + state.name(), removed -> {
                try {
                    forcedCleanupCount.incrementAndGet();

                    LagXpert.getInstance().getLogger().info(
                            "[EmergencyResponseCoordinator] Forced item cleanup in state " +
                                    state.name() + " removed " + removed + " item(s).");

                    ActionLogger.getInstance().log(
                            ActionLogger.ActionType.ITEM_CLEARED_BULK,
                            null, null,
                            "Forced cleanup on entering " + state.name(),
                            removed, "emergency", true,
                            System.currentTimeMillis() - start);
                } finally {
                    forcedCleanupRunning.set(false);
                }
            });
        } catch (Exception e) {
            // Release the latch if the sweep could not even be started, otherwise
            // no further forced cleanup would ever be permitted.
            forcedCleanupRunning.set(false);
            LagXpert.getInstance().getLogger().warning(
                    "[EmergencyResponseCoordinator] Forced item cleanup failed to start: " + e.getMessage());
        }
    }

    /**
     * Disables AI on every living entity in every loaded chunk.
     *
     * This is deliberately blunt and is only reachable from the most severe
     * states. Dispatch is per chunk so that it remains correct under Folia.
     */
    private void applyAiFreeze(EmergencyController.ServerState state) {
        if (!aiFreezeEnabled) {
            return;
        }
        // Only apply once per freeze episode.
        if (!aiFrozen.compareAndSet(false, true)) {
            return;
        }

        aiFreezeCount.incrementAndGet();
        LagXpert.getInstance().getLogger().warning(
                "[EmergencyResponseCoordinator] Freezing mob AI server-wide (state: " + state.name() + ").");

        forEachLoadedChunk(chunk -> {
            int affected = 0;
            for (Entity entity : chunk.getEntities()) {
                if (!(entity instanceof LivingEntity) || entity instanceof Player) {
                    continue;
                }
                if (entity.hasMetadata("NPC")) {
                    continue; // Leave Citizens-style NPCs alone.
                }
                LivingEntity living = (LivingEntity) entity;
                if (living.hasAI()) {
                    living.setAI(false);
                    affected++;
                }
            }
            if (affected > 0) {
                ActionLogger.getInstance().log(
                        ActionLogger.ActionType.AI_DISABLED,
                        chunk.getWorld().getName(),
                        chunkKey(chunk),
                        "Emergency AI freeze",
                        affected, "emergency", true, 0);
            }
        });
    }

    /**
     * Re-enables AI after a freeze, respecting the operator's own mobs.yml rules.
     *
     * Mobs that are disabled by type or world configuration stay disabled; only
     * the ones frozen purely because of the emergency are re-animated.
     */
    private void liftAiFreeze(EmergencyController.ServerState state) {
        if (!aiFrozen.compareAndSet(true, false)) {
            return;
        }

        LagXpert.getInstance().getLogger().info(
                "[EmergencyResponseCoordinator] Lifting mob AI freeze (state: " + state.name() + ").");

        MobAIOptimizer optimizer = MobAIOptimizer.getInstance();

        forEachLoadedChunk(chunk -> {
            int affected = 0;
            for (Entity entity : chunk.getEntities()) {
                if (!(entity instanceof LivingEntity) || entity instanceof Player) {
                    continue;
                }
                if (entity.hasMetadata("NPC")) {
                    continue;
                }
                LivingEntity living = (LivingEntity) entity;

                // Respect configuration-driven AI suppression.
                if (optimizer.isAiDisabledByConfig(living)) {
                    continue;
                }
                if (!living.hasAI()) {
                    living.setAI(true);
                    affected++;
                }
            }
            if (affected > 0) {
                ActionLogger.getInstance().log(
                        ActionLogger.ActionType.AI_ENABLED,
                        chunk.getWorld().getName(),
                        chunkKey(chunk),
                        "Emergency AI freeze lifted",
                        affected, "recovery", true, 0);
            }
        });
    }

    /**
     * Dispatches a per-chunk action across every loaded chunk in every world.
     *
     * Each chunk is handled in its own region task. On Spigot/Paper this collapses
     * to ordinary main-thread execution; on Folia it is the only correct way to
     * touch entities that belong to different regions.
     */
    private void forEachLoadedChunk(java.util.function.Consumer<Chunk> action) {
        for (World world : Bukkit.getWorlds()) {
            Chunk[] loaded;
            try {
                loaded = world.getLoadedChunks();
            } catch (Exception e) {
                continue;
            }
            for (Chunk chunk : loaded) {
                SchedulerWrapper.runTaskForChunk(chunk, () -> {
                    if (!chunk.isLoaded()) {
                        return;
                    }
                    try {
                        action.accept(chunk);
                    } catch (Exception e) {
                        if (ConfigManager.isDebugEnabled()) {
                            LagXpert.getInstance().getLogger().warning(
                                    "[EmergencyResponseCoordinator] Chunk action failed at " +
                                            chunkKey(chunk) + ": " + e.getMessage());
                        }
                    }
                });
            }
        }
    }

    private static String chunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
    }

    // ─── Diagnostics accessors ──────────────────────────────────────────

    public boolean isAiCurrentlyFrozen() {
        return aiFrozen.get();
    }

    public int getForcedCleanupCount() {
        return forcedCleanupCount.get();
    }

    public int getAiFreezeCount() {
        return aiFreezeCount.get();
    }

    public long getLastForcedCleanupAt() {
        return lastForcedCleanupAt.get();
    }

    /**
     * Unregisters the listener and lifts any active freeze so that mobs are not
     * left permanently frozen if the plugin is disabled mid-emergency.
     */
    public void shutdown() {
        if (registered) {
            EmergencyController.getInstance().removeStateChangeListener(this);
            registered = false;
        }
        if (aiFrozen.get()) {
            liftAiFreeze(EmergencyController.ServerState.NORMAL);
        }
    }
}
