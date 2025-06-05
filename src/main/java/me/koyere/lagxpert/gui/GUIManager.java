package me.koyere.lagxpert.gui;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Central manager for all LagXpert GUI operations.
 * Handles registration, initialization, and cleanup of GUI systems.
 * Provides centralized access to all GUI functionalities.
 */
public class GUIManager {

    private static GUIManager instance;
    private static boolean initialized = false;

    // GUI instances
    private ConfigGUI configGUI;

    // Track active GUI sessions
    private final Map<UUID, String> activeSessions = new HashMap<>();
    private final Map<UUID, Long> sessionStartTimes = new HashMap<>();

    // Configuration
    private static final long SESSION_TIMEOUT_MS = 300000; // 5 minutes
    private static final int MAX_CONCURRENT_SESSIONS = 10;

    /**
     * Private constructor for singleton pattern.
     */
    private GUIManager() {
        // Initialize GUI components
        this.configGUI = new ConfigGUI();
    }

    /**
     * Gets the singleton instance of GUIManager.
     *
     * @return The GUIManager instance
     */
    public static GUIManager getInstance() {
        if (instance == null) {
            instance = new GUIManager();
        }
        return instance;
    }

    /**
     * Initializes the GUI system by registering event listeners.
     * Should be called during plugin startup.
     *
     * @param plugin The LagXpert plugin instance
     * @return true if initialization was successful
     */
    public static boolean initialize(LagXpert plugin) {
        if (initialized) {
            plugin.getLogger().warning("GUIManager is already initialized!");
            return false;
        }

        try {
            GUIManager manager = getInstance();

            // Register event listeners
            Bukkit.getPluginManager().registerEvents(manager.configGUI, plugin);

            // Start session cleanup task
            manager.startSessionCleanupTask(plugin);

            initialized = true;
            plugin.getLogger().info("GUI System initialized successfully");
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize GUI System", e);
            return false;
        }
    }

    /**
     * Shuts down the GUI system and cleans up resources.
     * Should be called during plugin shutdown.
     */
    public static void shutdown() {
        if (!initialized) return;

        try {
            GUIManager manager = getInstance();

            // Close all open GUIs
            manager.closeAllGUIs();

            // Unregister event listeners
            HandlerList.unregisterAll(manager.configGUI);

            // Clear all tracking data
            manager.activeSessions.clear();
            manager.sessionStartTimes.clear();

            initialized = false;

            if (LagXpert.getInstance() != null) {
                LagXpert.getInstance().getLogger().info("GUI System shut down successfully");
            }

        } catch (Exception e) {
            if (LagXpert.getInstance() != null) {
                LagXpert.getInstance().getLogger().log(Level.SEVERE, "Error during GUI System shutdown", e);
            }
        }
    }

    /**
     * Opens the main configuration GUI for a player.
     * Includes permission checking and session management.
     *
     * @param player The player to open the GUI for
     * @return true if the GUI was opened successfully
     */
    public boolean openConfigGUI(Player player) {
        // Check permission
        if (!player.hasPermission("lagxpert.gui") && !player.hasPermission("lagxpert.admin")) {
            player.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
            return false;
        }

        // Check if player already has a GUI open
        if (hasActiveSession(player)) {
            player.sendMessage(MessageManager.getPrefixedMessage("gui.already-open"));
            return false;
        }

        // Check concurrent session limit
        if (activeSessions.size() >= MAX_CONCURRENT_SESSIONS) {
            player.sendMessage(MessageManager.getPrefixedMessage("gui.server-busy"));
            return false;
        }

        try {
            // Start new session
            startSession(player, "config_main");

            // Open the GUI
            ConfigGUI.openMainMenu(player);

            player.sendMessage(MessageManager.getPrefixedMessage("gui.opened"));
            return true;

        } catch (Exception e) {
            LagXpert.getInstance().getLogger().log(Level.WARNING,
                    "Failed to open config GUI for player " + player.getName(), e);
            player.sendMessage(MessageManager.getPrefixedMessage("general.error-occurred"));
            return false;
        }
    }

    /**
     * Closes any open GUI for the specified player.
     *
     * @param player The player whose GUI should be closed
     */
    public void closeGUI(Player player) {
        if (hasActiveSession(player)) {
            player.closeInventory();
            endSession(player);
            ConfigGUI.clearPlayerData(player);
            player.sendMessage(MessageManager.getPrefixedMessage("gui.closed"));
        }
    }

    /**
     * Closes all open GUIs for all players.
     * Used during plugin shutdown or reload.
     */
    public void closeAllGUIs() {
        // Create a copy of the keySet to avoid ConcurrentModificationException
        UUID[] playerIds = activeSessions.keySet().toArray(new UUID[0]);

        for (UUID playerId : playerIds) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                closeGUI(player);
            } else {
                // Clean up orphaned sessions
                endSession(playerId);
            }
        }
    }

    /**
     * Checks if a player has an active GUI session.
     *
     * @param player The player to check
     * @return true if the player has an active session
     */
    public boolean hasActiveSession(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    /**
     * Gets the type of GUI currently open for a player.
     *
     * @param player The player to check
     * @return The GUI type, or null if no GUI is open
     */
    public String getActiveGUIType(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    /**
     * Gets the number of currently active GUI sessions.
     *
     * @return The number of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Gets session statistics for monitoring purposes.
     *
     * @return A map containing session statistics
     */
    public Map<String, Object> getSessionStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("active_sessions", activeSessions.size());
        stats.put("max_concurrent_sessions", MAX_CONCURRENT_SESSIONS);
        stats.put("session_timeout_minutes", SESSION_TIMEOUT_MS / 60000);

        // Count sessions by type
        Map<String, Integer> sessionTypes = new HashMap<>();
        for (String type : activeSessions.values()) {
            sessionTypes.put(type, sessionTypes.getOrDefault(type, 0) + 1);
        }
        stats.put("sessions_by_type", sessionTypes);

        return stats;
    }

    /**
     * Starts a new GUI session for a player.
     *
     * @param player The player starting the session
     * @param guiType The type of GUI being opened
     */
    private void startSession(Player player, String guiType) {
        UUID playerId = player.getUniqueId();
        activeSessions.put(playerId, guiType);
        sessionStartTimes.put(playerId, System.currentTimeMillis());

        if (LagXpert.getInstance() != null && ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info(
                    "Started GUI session for " + player.getName() + " (type: " + guiType + ")");
        }
    }

    /**
     * Ends a GUI session for a player.
     *
     * @param player The player whose session should end
     */
    private void endSession(Player player) {
        endSession(player.getUniqueId());
    }

    /**
     * Ends a GUI session by player UUID.
     *
     * @param playerId The UUID of the player whose session should end
     */
    private void endSession(UUID playerId) {
        String sessionType = activeSessions.remove(playerId);
        Long startTime = sessionStartTimes.remove(playerId);

        if (sessionType != null && startTime != null && LagXpert.getInstance() != null &&
                ConfigManager.isDebugEnabled()) {
            long duration = System.currentTimeMillis() - startTime;
            LagXpert.getInstance().getLogger().info(
                    "Ended GUI session (type: " + sessionType + ", duration: " + duration + "ms)");
        }
    }

    /**
     * Starts the session cleanup task to handle timed-out sessions.
     *
     * @param plugin The LagXpert plugin instance
     */
    private void startSessionCleanupTask(LagXpert plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long currentTime = System.currentTimeMillis();

            // Find expired sessions
            UUID[] expiredSessions = sessionStartTimes.entrySet().stream()
                    .filter(entry -> currentTime - entry.getValue() > SESSION_TIMEOUT_MS)
                    .map(Map.Entry::getKey)
                    .toArray(UUID[]::new);

            // Clean up expired sessions
            for (UUID playerId : expiredSessions) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    closeGUI(player);
                    player.sendMessage(MessageManager.getPrefixedMessage("gui.session-expired"));
                } else {
                    endSession(playerId);
                }
            }

        }, 1200L, 1200L); // Run every minute (1200 ticks)
    }

    /**
     * Checks if the GUI system is initialized.
     *
     * @return true if the system is initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
    /**
     * Forces the end of a GUI session for a player.
     * Called by ConfigGUI when a GUI is closed.
     *
     * @param player The player whose session should be forcefully ended
     */
    public void forceEndSession(Player player) {
        UUID playerId = player.getUniqueId();

        if (activeSessions.containsKey(playerId)) {
            endSession(playerId);

            if (LagXpert.getInstance() != null && ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info(
                        "Forcefully ended GUI session for " + player.getName());
            }
        }
    }
    /**
     * Reloads GUI configurations.
     * Called when plugin configurations are reloaded.
     */
    public void reload() {
        // Close all existing GUIs to force refresh
        closeAllGUIs();

        if (LagXpert.getInstance() != null) {
            LagXpert.getInstance().getLogger().info("GUI System configurations reloaded");
        }
    }
}