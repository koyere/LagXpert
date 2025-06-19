package me.koyere.lagxpert.utils;

import me.koyere.lagxpert.LagXpert;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for detecting and handling Bedrock players through Geyser/Floodgate integration.
 * Provides cross-platform player detection and GUI optimization for Bedrock users.
 * 
 * Performance optimizations:
 * - Caches Bedrock player status to avoid repeated API calls
 * - Uses reflection only once during initialization
 * - Graceful fallback when Geyser/Floodgate are not available
 */
public class BedrockPlayerUtils {
    
    // Cached method references for optimal performance
    private static Method geyserIsBedrockPlayerMethod = null;
    private static Method floodgateIsFloodgatePlayerMethod = null;
    private static Object geyserApiInstance = null;
    private static Object floodgateApiInstance = null;
    
    // Cache for player platform detection (performance optimization)
    private static final ConcurrentHashMap<UUID, Boolean> bedrockPlayerCache = new ConcurrentHashMap<>();
    
    // Initialization status
    private static boolean initialized = false;
    
    /**
     * Initializes Bedrock player detection APIs.
     * Called once during plugin startup for optimal performance.
     */
    public static void initializeBedrockAPIs() {
        if (initialized) {
            return;
        }
        
        try {
            // Initialize Geyser API if available
            if (PlatformDetector.isGeyserEnabled()) {
                initializeGeyserAPI();
            }
            
            // Initialize Floodgate API if available
            if (PlatformDetector.isFloodgateEnabled()) {
                initializeFloodgateAPI();
            }
            
            initialized = true;
            
            if (hasBedrockSupport()) {
                LagXpert.getInstance().getLogger().info("[BedrockPlayerUtils] Bedrock player detection initialized successfully");
            }
            
        } catch (Exception e) {
            LagXpert.getInstance().getLogger().warning("[BedrockPlayerUtils] Failed to initialize Bedrock APIs: " + e.getMessage());
        }
    }
    
    /**
     * Initializes Geyser API for Bedrock player detection.
     */
    private static void initializeGeyserAPI() {
        try {
            // Try to get Geyser API instance
            Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Method getInstanceMethod = geyserApiClass.getMethod("api");
            geyserApiInstance = getInstanceMethod.invoke(null);
            
            // Get the isBedrockPlayer method
            geyserIsBedrockPlayerMethod = geyserApiClass.getMethod("isBedrockPlayer", UUID.class);
            
            LagXpert.getInstance().getLogger().info("[BedrockPlayerUtils] Geyser API initialized");
            
        } catch (Exception e) {
            LagXpert.getInstance().getLogger().warning("[BedrockPlayerUtils] Failed to initialize Geyser API: " + e.getMessage());
        }
    }
    
    /**
     * Initializes Floodgate API for Bedrock player detection.
     */
    private static void initializeFloodgateAPI() {
        try {
            // Try to get Floodgate API instance
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstanceMethod = floodgateApiClass.getMethod("getInstance");
            floodgateApiInstance = getInstanceMethod.invoke(null);
            
            // Get the isFloodgatePlayer method
            floodgateIsFloodgatePlayerMethod = floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class);
            
            LagXpert.getInstance().getLogger().info("[BedrockPlayerUtils] Floodgate API initialized");
            
        } catch (Exception e) {
            LagXpert.getInstance().getLogger().warning("[BedrockPlayerUtils] Failed to initialize Floodgate API: " + e.getMessage());
        }
    }
    
    /**
     * Checks if a player is playing on Bedrock Edition.
     * Uses caching for optimal performance.
     * 
     * @param player The player to check
     * @return true if the player is on Bedrock, false otherwise
     */
    public static boolean isBedrockPlayer(Player player) {
        if (player == null) {
            return false;
        }
        
        UUID playerId = player.getUniqueId();
        
        // Check cache first for performance
        Boolean cached = bedrockPlayerCache.get(playerId);
        if (cached != null) {
            return cached;
        }
        
        // Perform detection and cache result
        boolean isBedrock = detectBedrockPlayer(playerId);
        bedrockPlayerCache.put(playerId, isBedrock);
        
        return isBedrock;
    }
    
    /**
     * Performs actual Bedrock player detection using available APIs.
     */
    private static boolean detectBedrockPlayer(UUID playerId) {
        // Try Geyser API first
        if (geyserApiInstance != null && geyserIsBedrockPlayerMethod != null) {
            try {
                Boolean result = (Boolean) geyserIsBedrockPlayerMethod.invoke(geyserApiInstance, playerId);
                if (result != null && result) {
                    return true;
                }
            } catch (Exception e) {
                // Continue to next method
            }
        }
        
        // Try Floodgate API
        if (floodgateApiInstance != null && floodgateIsFloodgatePlayerMethod != null) {
            try {
                Boolean result = (Boolean) floodgateIsFloodgatePlayerMethod.invoke(floodgateApiInstance, playerId);
                if (result != null && result) {
                    return true;
                }
            } catch (Exception e) {
                // Continue to fallback
            }
        }
        
        // Fallback: Check username pattern (Floodgate prefixes Bedrock users)
        return checkUsernamePattern(playerId);
    }
    
    /**
     * Fallback method to detect Bedrock players by username pattern.
     * Floodgate typically prefixes Bedrock usernames with a dot or specific pattern.
     */
    private static boolean checkUsernamePattern(UUID playerId) {
        try {
            // This is a basic fallback - not 100% reliable
            // Floodgate users typically have a specific UUID pattern or prefix
            String uuidString = playerId.toString();
            
            // Floodgate UUIDs often start with specific patterns
            // This is a simplified check - production code should use proper APIs
            return uuidString.startsWith("00000000-0000-0000");
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Gets the platform type for a player as a string.
     */
    public static String getPlayerPlatform(Player player) {
        return isBedrockPlayer(player) ? "Bedrock" : "Java";
    }
    
    /**
     * Checks if GUI optimizations should be applied for this player.
     * Bedrock players may need different inventory layouts or item handling.
     */
    public static boolean shouldUseBedrockOptimizedGUI(Player player) {
        return isBedrockPlayer(player) && ConfigManager.isBedrockGUIOptimizationEnabled();
    }
    
    /**
     * Gets the maximum inventory size safe for this player platform.
     * Bedrock has some limitations compared to Java Edition.
     */
    public static int getSafeInventorySize(Player player) {
        if (isBedrockPlayer(player)) {
            // Bedrock is more limited in some GUI operations
            return ConfigManager.getBedrockMaxInventorySize();
        }
        return 54; // Standard large chest size for Java
    }
    
    /**
     * Checks if item NBT data should be simplified for this player.
     * Bedrock handles NBT differently than Java Edition.
     */
    public static boolean shouldSimplifyItemData(Player player) {
        return isBedrockPlayer(player) && ConfigManager.shouldSimplifyBedrockItemData();
    }
    
    /**
     * Removes a player from the cache when they disconnect.
     * Should be called from a player quit event to prevent memory leaks.
     */
    public static void removePlayerFromCache(Player player) {
        if (player != null) {
            bedrockPlayerCache.remove(player.getUniqueId());
        }
    }
    
    /**
     * Clears the entire player cache. Useful for reloads or debugging.
     */
    public static void clearCache() {
        bedrockPlayerCache.clear();
    }
    
    /**
     * Gets cache statistics for debugging purposes.
     */
    public static String getCacheStats() {
        int totalCached = bedrockPlayerCache.size();
        long bedrockCount = bedrockPlayerCache.values().stream().mapToLong(b -> b ? 1 : 0).sum();
        long javaCount = totalCached - bedrockCount;
        
        return String.format("Player Cache: %d total, %d Bedrock, %d Java", totalCached, bedrockCount, javaCount);
    }
    
    /**
     * Checks if any Bedrock support is available.
     */
    public static boolean hasBedrockSupport() {
        return geyserApiInstance != null || floodgateApiInstance != null;
    }
    
    /**
     * Gets a summary of available Bedrock detection methods.
     */
    public static String getBedrockSupportSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Bedrock Support: ");
        
        if (geyserApiInstance != null) {
            summary.append("Geyser API ");
        }
        
        if (floodgateApiInstance != null) {
            summary.append("Floodgate API ");
        }
        
        if (!hasBedrockSupport()) {
            summary.append("None (Fallback detection only)");
        }
        
        return summary.toString();
    }
}