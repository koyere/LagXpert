package me.koyere.lagxpert.utils;

import me.koyere.lagxpert.LagXpert;
import org.bukkit.Bukkit;

/**
 * Detects the server platform (Folia, Paper, Spigot, Bukkit) and Bedrock compatibility plugins.
 * Provides platform-specific optimizations and compatibility layers.
 * 
 * Performance considerations:
 * - Detection runs only once at startup
 * - Results are cached for the entire plugin lifecycle
 * - No reflection during runtime operations
 */
public class PlatformDetector {
    
    private static PlatformType platformType = null;
    private static boolean isGeyserEnabled = false;
    private static boolean isFloodgateEnabled = false;
    private static boolean hasRegionScheduler = false;
    private static boolean hasAsyncScheduler = false;
    private static boolean hasGlobalRegionScheduler = false;
    
    public enum PlatformType {
        FOLIA("Folia", true),
        PAPER("Paper", false), 
        SPIGOT("Spigot", false),
        BUKKIT("Bukkit", false);
        
        private final String displayName;
        private final boolean useRegionScheduler;
        
        PlatformType(String displayName, boolean useRegionScheduler) {
            this.displayName = displayName;
            this.useRegionScheduler = useRegionScheduler;
        }
        
        public String getDisplayName() { return displayName; }
        public boolean shouldUseRegionScheduler() { return useRegionScheduler; }
    }
    
    /**
     * Performs platform detection. Called only once during plugin initialization.
     * Uses lazy loading pattern for optimal performance.
     */
    public static void detectPlatform() {
        if (platformType != null) {
            return; // Already detected
        }
        
        try {
            // Check for Folia first (most specific)
            if (checkFoliaSupport()) {
                platformType = PlatformType.FOLIA;
                LagXpert.getInstance().getLogger().info("[PlatformDetector] Detected Folia server - Using region-based scheduling");
            }
            // Check for Paper
            else if (checkPaperSupport()) {
                platformType = PlatformType.PAPER;
                LagXpert.getInstance().getLogger().info("[PlatformDetector] Detected Paper server");
            }
            // Check for Spigot
            else if (checkSpigotSupport()) {
                platformType = PlatformType.SPIGOT;
                LagXpert.getInstance().getLogger().info("[PlatformDetector] Detected Spigot server");
            }
            // Fallback to Bukkit
            else {
                platformType = PlatformType.BUKKIT;
                LagXpert.getInstance().getLogger().info("[PlatformDetector] Detected Bukkit server");
            }
            
            // Detect Bedrock compatibility plugins
            detectBedrockPlugins();
            
        } catch (Exception e) {
            // Fallback to safest option
            platformType = PlatformType.BUKKIT;
            LagXpert.getInstance().getLogger().warning("[PlatformDetector] Platform detection failed, falling back to Bukkit: " + e.getMessage());
        }
    }
    
    /**
     * Checks if the server is running Folia by looking for Folia-specific classes.
     * Uses Class.forName to avoid compile-time dependencies.
     */
    private static boolean checkFoliaSupport() {
        try {
            // First check if we have the Folia classes
            Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            hasRegionScheduler = true;
            
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            hasAsyncScheduler = true;
            
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            hasGlobalRegionScheduler = true;
            
            // Now check if this is actually Folia or just Paper with Folia classes
            // Folia has a specific method that Paper doesn't implement the same way
            String serverVersion = Bukkit.getVersion().toLowerCase();
            String serverName = Bukkit.getName().toLowerCase();
            
            // Check for Folia-specific identifiers
            if (serverVersion.contains("folia") || serverName.contains("folia")) {
                return true;
            }
            
            // Additional check: Try to access Folia-specific runtime behavior
            try {
                // This method exists in Folia but behaves differently in Paper
                Class<?> serverClass = Class.forName("org.bukkit.Bukkit");
                java.lang.reflect.Method method = serverClass.getMethod("getRegionScheduler");
                Object scheduler = method.invoke(null);
                
                // If we got here and scheduler is not null, it's likely real Folia
                return scheduler != null && scheduler.getClass().getName().contains("folia");
            } catch (Exception e) {
                // If this fails, it's probably Paper with Folia classes but not real Folia
                return false;
            }
            
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Checks if the server is running Paper.
     */
    private static boolean checkPaperSupport() {
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            return true;
        } catch (ClassNotFoundException e) {
            try {
                // Check newer Paper versions
                Class.forName("io.papermc.paper.configuration.Configuration");
                return true;
            } catch (ClassNotFoundException ex) {
                return false;
            }
        }
    }
    
    /**
     * Checks if the server is running Spigot.
     */
    private static boolean checkSpigotSupport() {
        try {
            Class.forName("org.spigotmc.SpigotConfig");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Detects Bedrock compatibility plugins (Geyser and Floodgate).
     * Performance-optimized to avoid repeated checks.
     */
    private static void detectBedrockPlugins() {
        // Check for Geyser
        isGeyserEnabled = Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null ||
                         Bukkit.getPluginManager().getPlugin("Geyser-Standalone") != null;
        
        // Check for Floodgate
        isFloodgateEnabled = Bukkit.getPluginManager().getPlugin("floodgate") != null;
        
        if (isGeyserEnabled) {
            LagXpert.getInstance().getLogger().info("[PlatformDetector] Geyser detected - Bedrock compatibility enabled");
        }
        
        if (isFloodgateEnabled) {
            LagXpert.getInstance().getLogger().info("[PlatformDetector] Floodgate detected - Enhanced Bedrock support enabled");
        }
    }
    
    // Public getters for platform information
    public static PlatformType getPlatformType() {
        if (platformType == null) {
            detectPlatform();
        }
        return platformType;
    }
    
    public static boolean isFolia() {
        return getPlatformType() == PlatformType.FOLIA;
    }
    
    public static boolean isPaper() {
        return getPlatformType() == PlatformType.PAPER;
    }
    
    public static boolean isSpigot() {
        return getPlatformType() == PlatformType.SPIGOT;
    }
    
    public static boolean isBukkit() {
        return getPlatformType() == PlatformType.BUKKIT;
    }
    
    public static boolean isGeyserEnabled() {
        return isGeyserEnabled;
    }
    
    public static boolean isFloodgateEnabled() {
        return isFloodgateEnabled;
    }
    
    public static boolean hasBedrockSupport() {
        return isGeyserEnabled || isFloodgateEnabled;
    }
    
    public static boolean hasRegionScheduler() {
        return hasRegionScheduler;
    }
    
    public static boolean hasAsyncScheduler() {
        return hasAsyncScheduler;
    }
    
    public static boolean hasGlobalRegionScheduler() {
        return hasGlobalRegionScheduler;
    }
    
    /**
     * Returns a summary of detected platform features.
     */
    public static String getPlatformSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Platform: ").append(getPlatformType().getDisplayName());
        
        if (hasBedrockSupport()) {
            summary.append(" | Bedrock: ");
            if (isGeyserEnabled) summary.append("Geyser ");
            if (isFloodgateEnabled) summary.append("Floodgate");
        }
        
        if (isFolia()) {
            summary.append(" | Folia Features: ");
            if (hasRegionScheduler) summary.append("RegionScheduler ");
            if (hasAsyncScheduler) summary.append("AsyncScheduler ");
            if (hasGlobalRegionScheduler) summary.append("GlobalRegionScheduler");
        }
        
        return summary.toString();
    }
}