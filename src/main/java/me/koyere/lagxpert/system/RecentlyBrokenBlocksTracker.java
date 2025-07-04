package me.koyere.lagxpert.system;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks recently broken blocks by players to provide a grace period
 * for item cleanup. This prevents the item cleaner from removing items
 * that players just broke but haven't collected yet.
 * 
 * Thread-safe implementation with automatic cleanup of expired entries.
 */
public class RecentlyBrokenBlocksTracker {
    
    /**
     * Stores information about a recently broken block.
     */
    public static class BrokenBlockInfo {
        private final UUID playerUUID;
        private final Material material;
        private final Location location;
        private final long timestamp;
        private final long gracePeriodMs;
        
        public BrokenBlockInfo(UUID playerUUID, Material material, Location location, long gracePeriodMs) {
            this.playerUUID = playerUUID;
            this.material = material;
            this.location = location.clone(); // Clone to avoid reference issues
            this.timestamp = System.currentTimeMillis();
            this.gracePeriodMs = gracePeriodMs;
        }
        
        public UUID getPlayerUUID() { return playerUUID; }
        public Material getMaterial() { return material; }
        public Location getLocation() { return location; }
        public long getTimestamp() { return timestamp; }
        public long getGracePeriodMs() { return gracePeriodMs; }
        
        /**
         * Checks if this broken block is still within its grace period.
         * @return true if the grace period is still active
         */
        public boolean isWithinGracePeriod() {
            return (System.currentTimeMillis() - timestamp) < gracePeriodMs;
        }
        
        /**
         * Gets the remaining grace period in milliseconds.
         * @return milliseconds remaining, or 0 if expired
         */
        public long getRemainingGracePeriodMs() {
            long remaining = gracePeriodMs - (System.currentTimeMillis() - timestamp);
            return Math.max(0, remaining);
        }
    }
    
    // Map: Location key -> BrokenBlockInfo
    private static final Map<String, BrokenBlockInfo> recentlyBrokenBlocks = new ConcurrentHashMap<>();
    
    // Default grace period: 3 minutes (configurable)
    private static long defaultGracePeriodMs = 180000; // 3 minutes
    
    // Cleanup interval: run cleanup every 30 seconds
    private static final long CLEANUP_INTERVAL_MS = 30000;
    private static long lastCleanupTime = System.currentTimeMillis();
    
    /**
     * Generates a unique key for a location.
     * Format: "world_x_y_z"
     * @param location The location to generate key for
     * @return Unique location key
     */
    private static String generateLocationKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return "invalid_location";
        }
        return location.getWorld().getName() + "_" + 
               location.getBlockX() + "_" + 
               location.getBlockY() + "_" + 
               location.getBlockZ();
    }
    
    /**
     * Records a block that was recently broken by a player.
     * This creates a grace period during which dropped items from this block
     * should not be cleaned up by the item cleaner.
     * 
     * @param player The player who broke the block
     * @param material The material of the broken block
     * @param location The location where the block was broken
     */
    public static void recordBrokenBlock(Player player, Material material, Location location) {
        recordBrokenBlock(player, material, location, defaultGracePeriodMs);
    }
    
    /**
     * Records a block that was recently broken by a player with a custom grace period.
     * 
     * @param player The player who broke the block
     * @param material The material of the broken block
     * @param location The location where the block was broken
     * @param gracePeriodMs Custom grace period in milliseconds
     */
    public static void recordBrokenBlock(Player player, Material material, Location location, long gracePeriodMs) {
        if (player == null || material == null || location == null) {
            return;
        }
        
        String locationKey = generateLocationKey(location);
        BrokenBlockInfo info = new BrokenBlockInfo(player.getUniqueId(), material, location, gracePeriodMs);
        
        recentlyBrokenBlocks.put(locationKey, info);
        
        // Trigger cleanup if needed
        performPeriodicCleanup();
    }
    
    /**
     * Checks if a location has a recently broken block within its grace period.
     * Items dropped at or near this location should not be cleaned up.
     * 
     * @param location The location to check
     * @return true if there's a recently broken block at this location
     */
    public static boolean hasRecentlyBrokenBlock(Location location) {
        if (location == null) {
            return false;
        }
        
        String locationKey = generateLocationKey(location);
        BrokenBlockInfo info = recentlyBrokenBlocks.get(locationKey);
        
        if (info == null) {
            return false;
        }
        
        if (info.isWithinGracePeriod()) {
            return true;
        } else {
            // Clean up expired entry
            recentlyBrokenBlocks.remove(locationKey);
            return false;
        }
    }
    
    /**
     * Checks if a location has a recently broken block by a specific player.
     * 
     * @param location The location to check
     * @param playerUUID The UUID of the player
     * @return true if the specified player recently broke a block at this location
     */
    public static boolean hasRecentlyBrokenBlockByPlayer(Location location, UUID playerUUID) {
        if (location == null || playerUUID == null) {
            return false;
        }
        
        String locationKey = generateLocationKey(location);
        BrokenBlockInfo info = recentlyBrokenBlocks.get(locationKey);
        
        if (info == null) {
            return false;
        }
        
        if (info.isWithinGracePeriod() && info.getPlayerUUID().equals(playerUUID)) {
            return true;
        } else if (!info.isWithinGracePeriod()) {
            // Clean up expired entry
            recentlyBrokenBlocks.remove(locationKey);
        }
        
        return false;
    }
    
    /**
     * Gets information about a recently broken block at a location.
     * 
     * @param location The location to get information for
     * @return BrokenBlockInfo if found and within grace period, null otherwise
     */
    public static BrokenBlockInfo getBrokenBlockInfo(Location location) {
        if (location == null) {
            return null;
        }
        
        String locationKey = generateLocationKey(location);
        BrokenBlockInfo info = recentlyBrokenBlocks.get(locationKey);
        
        if (info == null) {
            return null;
        }
        
        if (info.isWithinGracePeriod()) {
            return info;
        } else {
            // Clean up expired entry
            recentlyBrokenBlocks.remove(locationKey);
            return null;
        }
    }
    
    /**
     * Manually removes a broken block record.
     * This can be called when items are collected or when manual cleanup is needed.
     * 
     * @param location The location to remove from tracking
     */
    public static void removeBrokenBlock(Location location) {
        if (location == null) {
            return;
        }
        
        String locationKey = generateLocationKey(location);
        recentlyBrokenBlocks.remove(locationKey);
    }
    
    /**
     * Clears all broken block records for a specific player.
     * Useful when a player leaves the server.
     * 
     * @param playerUUID The UUID of the player
     */
    public static void clearPlayerRecords(UUID playerUUID) {
        if (playerUUID == null) {
            return;
        }
        
        recentlyBrokenBlocks.entrySet().removeIf(entry -> 
            entry.getValue().getPlayerUUID().equals(playerUUID));
    }
    
    /**
     * Clears all broken block records for a specific world.
     * Useful during world unload or plugin reload.
     * 
     * @param worldName The name of the world
     */
    public static void clearWorldRecords(String worldName) {
        if (worldName == null) {
            return;
        }
        
        recentlyBrokenBlocks.entrySet().removeIf(entry -> 
            entry.getKey().startsWith(worldName + "_"));
    }
    
    /**
     * Clears all broken block records.
     * Called during plugin shutdown or reload.
     */
    public static void clearAll() {
        recentlyBrokenBlocks.clear();
    }
    
    /**
     * Performs cleanup of expired broken block records.
     * Called automatically during normal operations.
     */
    private static void performPeriodicCleanup() {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            recentlyBrokenBlocks.entrySet().removeIf(entry -> 
                !entry.getValue().isWithinGracePeriod());
            lastCleanupTime = currentTime;
        }
    }
    
    /**
     * Forces immediate cleanup of all expired broken block records.
     * Can be called manually for maintenance.
     */
    public static void forceCleanup() {
        recentlyBrokenBlocks.entrySet().removeIf(entry -> 
            !entry.getValue().isWithinGracePeriod());
        lastCleanupTime = System.currentTimeMillis();
    }
    
    /**
     * Gets the current default grace period in milliseconds.
     * @return Default grace period in milliseconds
     */
    public static long getDefaultGracePeriodMs() {
        return defaultGracePeriodMs;
    }
    
    /**
     * Sets the default grace period for newly broken blocks.
     * @param gracePeriodMs Grace period in milliseconds
     */
    public static void setDefaultGracePeriodMs(long gracePeriodMs) {
        defaultGracePeriodMs = Math.max(0, gracePeriodMs);
    }
    
    /**
     * Gets statistics about the broken block tracker.
     * @return Map containing tracker statistics
     */
    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("total_records", recentlyBrokenBlocks.size());
        stats.put("default_grace_period_ms", defaultGracePeriodMs);
        stats.put("cleanup_interval_ms", CLEANUP_INTERVAL_MS);
        stats.put("last_cleanup_time", lastCleanupTime);
        
        // Count active records (within grace period)
        long activeRecords = recentlyBrokenBlocks.values().stream()
            .mapToLong(info -> info.isWithinGracePeriod() ? 1 : 0)
            .sum();
        stats.put("active_records", activeRecords);
        stats.put("expired_records", recentlyBrokenBlocks.size() - activeRecords);
        
        return stats;
    }
}