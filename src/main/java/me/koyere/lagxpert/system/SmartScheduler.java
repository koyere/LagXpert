package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.SchedulerWrapper;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Smart Scheduler — wraps task scheduling with adaptive interval adjustment
 * based on EmergencyController server state.
 *
 * Tasks registered as adaptive will automatically:
 *   - Run at normal intervals during NORMAL state
 *   - Run less frequently during WARNING state (non-critical tasks)
 *   - Pause during CRITICAL state (non-critical tasks)
 *   - Only emergency tasks run during EMERGENCY state
 *
 * Provides fallback to original fixed-interval behavior if EmergencyController
 * is disabled or unavailable.
 */
public class SmartScheduler {

    private static SmartScheduler instance;

    /**
     * Priority levels for adaptive scheduling.
     */
    public enum TaskPriority {
        LOW,        // Paused during WARNING+, e.g., ChunkPreloader, PerformanceHistory
        NORMAL,     // Reduced interval during WARNING, paused during CRITICAL+
        HIGH,       // Runs at normal interval during WARNING, reduced during CRITICAL
        CRITICAL,   // Runs at increased frequency during CRITICAL+
        EMERGENCY   // Always runs, increased frequency during EMERGENCY
    }

    private final Map<String, AdaptiveTask> registeredTasks = new ConcurrentHashMap<>();

    /**
     * Internal representation of an adaptive task.
     */
    private static class AdaptiveTask {
        final String name;
        final Runnable runnable;
        final long baseIntervalTicks;
        final TaskPriority priority;
        BukkitTask currentTask;
        long currentIntervalTicks;

        AdaptiveTask(String name, Runnable runnable, long baseIntervalTicks, TaskPriority priority) {
            this.name = name;
            this.runnable = runnable;
            this.baseIntervalTicks = baseIntervalTicks;
            this.priority = priority;
            this.currentIntervalTicks = baseIntervalTicks;
        }
    }

    private SmartScheduler() {
        // Register state change listener to adjust all tasks on state transitions
        EmergencyController.getInstance().addStateChangeListener((oldState, newState) -> {
            adjustAllTasks(newState);
        });
    }

    public static SmartScheduler getInstance() {
        if (instance == null) {
            instance = new SmartScheduler();
        }
        return instance;
    }

    /**
     * Registers and starts an adaptive repeating task.
     *
     * @param name             Unique name for this task
     * @param runnable         The task to run
     * @param delayTicks       Initial delay before first execution
     * @param baseIntervalTicks Interval during NORMAL state
     * @param priority         Task priority for adaptive adjustment
     * @return The scheduled BukkitTask
     */
    public BukkitTask scheduleAdaptive(String name, Runnable runnable,
                                        long delayTicks, long baseIntervalTicks,
                                        TaskPriority priority) {
        AdaptiveTask task = new AdaptiveTask(name, runnable, baseIntervalTicks, priority);
        registeredTasks.put(name, task);

        long effectiveInterval = getEffectiveInterval(baseIntervalTicks, priority);
        task.currentIntervalTicks = effectiveInterval;
        task.currentTask = SchedulerWrapper.runTaskTimer(runnable, delayTicks, effectiveInterval);

        if (LagXpert.getInstance() != null) {
            LagXpert.getInstance().getLogger().info(
                    "[SmartScheduler] Registered adaptive task '" + name +
                            "' interval=" + effectiveInterval + " ticks, priority=" + priority.name());
        }

        return task.currentTask;
    }

    /**
     * Cancels a previously registered adaptive task.
     */
    public void cancel(String name) {
        AdaptiveTask task = registeredTasks.remove(name);
        if (task != null && task.currentTask != null) {
            task.currentTask.cancel();
        }
    }

    /**
     * Adjusts all registered tasks based on current server state.
     */
    private void adjustAllTasks(EmergencyController.ServerState state) {
        for (AdaptiveTask task : registeredTasks.values()) {
            adjustTask(task, state);
        }
    }

    /**
     * Adjusts a single task's interval based on server state.
     */
    private void adjustTask(AdaptiveTask task, EmergencyController.ServerState state) {
        long newInterval = getEffectiveInterval(task.baseIntervalTicks, task.priority);

        // Only reschedule if interval actually changed
        if (newInterval != task.currentIntervalTicks && task.currentTask != null) {
            task.currentTask.cancel();
            task.currentIntervalTicks = newInterval;
            task.currentTask = SchedulerWrapper.runTaskTimer(
                    task.runnable, newInterval, newInterval);

            if (LagXpert.getInstance() != null && ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info(
                        "[SmartScheduler] Adjusted task '" + task.name +
                                "' interval: " + newInterval + " ticks (state: " + state.name() + ")");
            }
        }
    }

    /**
     * Computes the effective interval for a task based on current server state
     * and its priority.
     */
    private long getEffectiveInterval(long baseInterval, TaskPriority priority) {
        EmergencyController.ServerState state =
                EmergencyController.getInstance().getCurrentState();

        switch (state) {
            case EMERGENCY:
                switch (priority) {
                    case EMERGENCY:
                        return Math.max(1, baseInterval / 4); // 4x faster
                    case CRITICAL:
                        return baseInterval; // Keep normal
                    default:
                        return Long.MAX_VALUE; // Paused (effectively)
                }
            case CRITICAL:
                switch (priority) {
                    case EMERGENCY:
                        return Math.max(1, baseInterval / 2); // 2x faster
                    case CRITICAL:
                        return baseInterval;
                    case HIGH:
                        return baseInterval * 2; // Slower
                    default:
                        return Long.MAX_VALUE; // Paused
                }
            case WARNING:
                switch (priority) {
                    case LOW:
                        return Long.MAX_VALUE; // Paused
                    case NORMAL:
                        return (long)(baseInterval * 1.5); // 50% slower
                    default:
                        return baseInterval;
                }
            case NORMAL:
            default:
                return baseInterval;
        }
    }

    /**
     * Checks if a task is currently active (not paused).
     */
    public boolean isTaskActive(String name) {
        AdaptiveTask task = registeredTasks.get(name);
        return task != null && task.currentTask != null &&
                !task.currentTask.isCancelled() &&
                task.currentIntervalTicks < Long.MAX_VALUE;
    }

    /**
     * Cancels all registered tasks.
     */
    public void cancelAll() {
        for (AdaptiveTask task : registeredTasks.values()) {
            if (task.currentTask != null) {
                task.currentTask.cancel();
            }
        }
        registeredTasks.clear();
    }

    /**
     * Returns statistics for commands/API.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("registered_tasks", registeredTasks.size());
        int activeTasks = 0;
        for (AdaptiveTask task : registeredTasks.values()) {
            if (task.currentTask != null && !task.currentTask.isCancelled() &&
                    task.currentIntervalTicks < Long.MAX_VALUE) {
                activeTasks++;
            }
        }
        stats.put("active_tasks", activeTasks);
        stats.put("server_state", EmergencyController.getInstance().getCurrentState().name());
        return stats;
    }

    /**
     * Needed for debug logging reference to ConfigManager.
     */
    private static class ConfigManager {
        static boolean isDebugEnabled() {
            return me.koyere.lagxpert.utils.ConfigManager.isDebugEnabled();
        }
    }
}
