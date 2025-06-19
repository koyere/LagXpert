package me.koyere.lagxpert.utils;

import me.koyere.lagxpert.LagXpert;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Cross-platform scheduler wrapper that automatically uses the appropriate 
 * scheduling system based on the detected platform (Folia vs Bukkit/Spigot/Paper).
 * 
 * Performance optimizations:
 * - Uses reflection only once during initialization
 * - Caches method references for optimal performance
 * - Falls back gracefully to Bukkit scheduler if Folia methods fail
 */
public class SchedulerWrapper {
    
    // Cached method references for Folia (reflection performance optimization)
    private static Method regionSchedulerMethod = null;
    private static Method asyncSchedulerMethod = null;
    private static Method globalRegionSchedulerMethod = null;
    private static Method runDelayedMethod = null;
    private static Method runAtFixedRateMethod = null;
    private static Method runAsyncMethod = null;
    
    // Cached scheduler instances
    private static Object regionScheduler = null;
    private static Object asyncScheduler = null;
    private static Object globalRegionScheduler = null;
    
    private static boolean foliaInitialized = false;
    
    /**
     * Initializes Folia scheduler references if running on Folia.
     * Called once during plugin startup for optimal performance.
     */
    public static void initializeFoliaSchedulers() {
        if (!PlatformDetector.isFolia() || foliaInitialized) {
            return;
        }
        
        try {
            // Get server instance and scheduler methods
            Object serverInstance = Bukkit.getServer();
            Class<?> serverClass = serverInstance.getClass();
            
            // Cache RegionScheduler
            if (PlatformDetector.hasRegionScheduler()) {
                regionSchedulerMethod = serverClass.getMethod("getRegionScheduler");
                regionScheduler = regionSchedulerMethod.invoke(serverInstance);
            }
            
            // Cache AsyncScheduler
            if (PlatformDetector.hasAsyncScheduler()) {
                asyncSchedulerMethod = serverClass.getMethod("getAsyncScheduler");
                asyncScheduler = asyncSchedulerMethod.invoke(serverInstance);
            }
            
            // Cache GlobalRegionScheduler
            if (PlatformDetector.hasGlobalRegionScheduler()) {
                globalRegionSchedulerMethod = serverClass.getMethod("getGlobalRegionScheduler");
                globalRegionScheduler = globalRegionSchedulerMethod.invoke(serverInstance);
            }
            
            foliaInitialized = true;
            LagXpert.getInstance().getLogger().info("[SchedulerWrapper] Folia schedulers initialized successfully");
            
        } catch (Exception e) {
            LagXpert.getInstance().getLogger().warning("[SchedulerWrapper] Failed to initialize Folia schedulers, falling back to Bukkit: " + e.getMessage());
        }
    }
    
    /**
     * Runs a task on the main thread with cross-platform compatibility.
     * On Folia: Uses GlobalRegionScheduler for global tasks
     * On Bukkit/Spigot/Paper: Uses standard BukkitScheduler
     */
    public static BukkitTask runTask(Runnable task) {
        if (PlatformDetector.isFolia() && globalRegionScheduler != null) {
            try {
                // Use Folia's GlobalRegionScheduler for main thread tasks
                Method runMethod = globalRegionScheduler.getClass().getMethod("run", Runnable.class);
                Object foliaTask = runMethod.invoke(globalRegionScheduler, task);
                
                // Return a wrapper that implements BukkitTask interface
                return new FoliaTaskWrapper(foliaTask);
                
            } catch (Exception e) {
                LagXpert.getInstance().getLogger().warning("[SchedulerWrapper] Folia task execution failed, falling back to Bukkit: " + e.getMessage());
            }
        }
        
        // Fallback to standard Bukkit scheduler
        return Bukkit.getScheduler().runTask(LagXpert.getInstance(), task);
    }
    
    /**
     * Runs a delayed task with cross-platform compatibility.
     */
    public static BukkitTask runTaskLater(Runnable task, long delayTicks) {
        if (PlatformDetector.isFolia() && globalRegionScheduler != null) {
            try {
                // Convert ticks to milliseconds for Folia (20 ticks = 1000ms)
                long delayMs = delayTicks * 50L;
                
                Method runDelayedMethod = globalRegionScheduler.getClass().getMethod("runDelayed", Runnable.class, long.class);
                Object foliaTask = runDelayedMethod.invoke(globalRegionScheduler, task, delayMs);
                
                return new FoliaTaskWrapper(foliaTask);
                
            } catch (Exception e) {
                LagXpert.getInstance().getLogger().warning("[SchedulerWrapper] Folia delayed task failed, falling back to Bukkit: " + e.getMessage());
            }
        }
        
        return Bukkit.getScheduler().runTaskLater(LagXpert.getInstance(), task, delayTicks);
    }
    
    /**
     * Runs a repeating task with cross-platform compatibility.
     */
    public static BukkitTask runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        if (PlatformDetector.isFolia() && globalRegionScheduler != null) {
            try {
                // Convert to milliseconds
                long delayMs = delayTicks * 50L;
                long periodMs = periodTicks * 50L;
                
                Method runAtFixedRateMethod = globalRegionScheduler.getClass().getMethod("runAtFixedRate", Runnable.class, long.class, long.class);
                Object foliaTask = runAtFixedRateMethod.invoke(globalRegionScheduler, task, delayMs, periodMs);
                
                return new FoliaTaskWrapper(foliaTask);
                
            } catch (Exception e) {
                LagXpert.getInstance().getLogger().warning("[SchedulerWrapper] Folia timer task failed, falling back to Bukkit: " + e.getMessage());
            }
        }
        
        return Bukkit.getScheduler().runTaskTimer(LagXpert.getInstance(), task, delayTicks, periodTicks);
    }
    
    /**
     * Runs an async task with cross-platform compatibility.
     */
    public static BukkitTask runTaskAsynchronously(Runnable task) {
        if (PlatformDetector.isFolia() && asyncScheduler != null) {
            try {
                Method runNowMethod = asyncScheduler.getClass().getMethod("runNow", Runnable.class);
                Object foliaTask = runNowMethod.invoke(asyncScheduler, task);
                
                return new FoliaTaskWrapper(foliaTask);
                
            } catch (Exception e) {
                LagXpert.getInstance().getLogger().warning("[SchedulerWrapper] Folia async task failed, falling back to Bukkit: " + e.getMessage());
            }
        }
        
        return Bukkit.getScheduler().runTaskAsynchronously(LagXpert.getInstance(), task);
    }
    
    /**
     * Runs a task for a specific region/chunk (Folia-specific optimization).
     * On non-Folia servers, falls back to regular task scheduling.
     */
    public static BukkitTask runTaskForRegion(World world, int chunkX, int chunkZ, Runnable task) {
        if (PlatformDetector.isFolia() && regionScheduler != null) {
            try {
                Method runMethod = regionScheduler.getClass().getMethod("run", World.class, int.class, int.class, Runnable.class);
                Object foliaTask = runMethod.invoke(regionScheduler, world, chunkX, chunkZ, task);
                
                return new FoliaTaskWrapper(foliaTask);
                
            } catch (Exception e) {
                LagXpert.getInstance().getLogger().warning("[SchedulerWrapper] Folia region task failed, falling back to Bukkit: " + e.getMessage());
            }
        }
        
        // Fallback to regular task
        return runTask(task);
    }
    
    /**
     * Convenience method for running region tasks by chunk.
     */
    public static BukkitTask runTaskForChunk(Chunk chunk, Runnable task) {
        return runTaskForRegion(chunk.getWorld(), chunk.getX(), chunk.getZ(), task);
    }
    
    /**
     * Convenience method for running region tasks by location.
     */
    public static BukkitTask runTaskForLocation(Location location, Runnable task) {
        Chunk chunk = location.getChunk();
        return runTaskForRegion(chunk.getWorld(), chunk.getX(), chunk.getZ(), task);
    }
    
    /**
     * Cancels all tasks for this plugin across all schedulers.
     */
    public static void cancelAllTasks() {
        // Cancel standard Bukkit tasks
        Bukkit.getScheduler().cancelTasks(LagXpert.getInstance());
        
        // On Folia, tasks are automatically cleaned up when the plugin disables
        // No additional cleanup needed for Folia schedulers
    }
    
    /**
     * Wrapper class to make Folia tasks compatible with BukkitTask interface.
     * Provides basic functionality while maintaining compatibility.
     */
    private static class FoliaTaskWrapper implements BukkitTask {
        private final Object foliaTask;
        private boolean cancelled = false;
        
        public FoliaTaskWrapper(Object foliaTask) {
            this.foliaTask = foliaTask;
        }
        
        @Override
        public int getTaskId() {
            return -1; // Folia doesn't use integer IDs
        }
        
        @Override
        public org.bukkit.plugin.Plugin getOwner() {
            return LagXpert.getInstance();
        }
        
        @Override
        public boolean isSync() {
            return true; // Assume sync for region-based tasks
        }
        
        @Override
        public boolean isCancelled() {
            return cancelled;
        }
        
        @Override
        public void cancel() {
            if (foliaTask != null && !cancelled) {
                try {
                    Method cancelMethod = foliaTask.getClass().getMethod("cancel");
                    cancelMethod.invoke(foliaTask);
                    cancelled = true;
                } catch (Exception e) {
                    // Ignore cancellation errors
                }
            }
        }
    }
    
    /**
     * Utility method to get scheduler information for debugging.
     */
    public static String getSchedulerInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Platform: ").append(PlatformDetector.getPlatformType().getDisplayName());
        
        if (PlatformDetector.isFolia()) {
            info.append(" | Folia Schedulers: ");
            info.append("Region=").append(regionScheduler != null);
            info.append(", Async=").append(asyncScheduler != null);
            info.append(", Global=").append(globalRegionScheduler != null);
        }
        
        return info.toString();
    }
}