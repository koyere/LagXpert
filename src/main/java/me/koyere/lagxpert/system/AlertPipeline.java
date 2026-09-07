package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import me.koyere.lagxpert.utils.MessageType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AlertPipeline — unified entry point for all LagXpert alerts.
 *
 * Consolidates what was previously three separate cooldown/alert systems:
 *   1. AlertCooldownManager (player-specific cooldowns)
 *   2. alerts.yml cooldown config (per-type cooldowns)
 *   3. monitoring.yml cooldown config (TPS/memory/lag-spike cooldowns)
 *
 * Features:
 *   - Single method for sending any alert
 *   - Global burst protection (max alerts/second)
 *   - Per-player rate limiting
 *   - Per-type + per-context deduplication
 *   - Automatic cooldown enforcement
 *   - Console delivery alongside player delivery
 *   - Emergency mode: bypasses cooldowns for critical alerts
 *
 * Thread-safe. Works with existing MessageManager for i18n.
 */
public class AlertPipeline {

    private static AlertPipeline instance;

    // Alert severity levels
    public enum AlertLevel {
        DEBUG,
        INFO,
        WARNING,
        CRITICAL,
        EMERGENCY
    }

    // Delivery targets
    public enum AlertTarget {
        CONSOLE,
        PLAYERS_WITH_PERMISSION,
        AFFECTED_PLAYERS,
        ALL_PLAYERS
    }

    // Per-player cooldown tracking
    // Structure: Player UUID → (Alert Key → Timestamp)
    private final Map<UUID, Map<String, Long>> playerCooldowns = new ConcurrentHashMap<>();

    // Global rate limiting
    private final AtomicInteger alertsThisSecond = new AtomicInteger(0);
    private final AtomicLong secondWindowStart = new AtomicLong(System.currentTimeMillis());
    private int maxAlertsPerSecond = 10;

    // Per-type cooldowns (from config)
    private final Map<String, Integer> typeCooldowns = new ConcurrentHashMap<>();

    // Statistics
    private final AtomicLong totalAlertsSent = new AtomicLong(0);
    private final AtomicLong totalAlertsSuppressed = new AtomicLong(0);

    private AlertPipeline() {
        loadCooldownConfig();
    }

    public static AlertPipeline getInstance() {
        if (instance == null) {
            instance = new AlertPipeline();
        }
        return instance;
    }

    /**
     * Reloads cooldown configuration from alerts.yml.
     */
    public void loadCooldownConfig() {
        typeCooldowns.clear();

        int defaultCooldown = ConfigManager.getAlertCooldownDefaultSeconds();

        // Load per-type cooldowns and global rate limit from alerts.yml
        try {
            java.io.File alertsFile = new java.io.File(
                    LagXpert.getInstance().getDataFolder(), "alerts.yml");
            if (alertsFile.exists()) {
                org.bukkit.configuration.file.FileConfiguration config =
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(alertsFile);

                // Load global rate limit
                this.maxAlertsPerSecond = config.getInt(
                        "global-rate-limit.max-alerts-per-second", 10);

                // Load per-type cooldowns
                if (config.isConfigurationSection("cooldowns.per-type")) {
                    for (String key : config.getConfigurationSection("cooldowns.per-type")
                            .getKeys(false)) {
                        int seconds = config.getInt("cooldowns.per-type." + key, defaultCooldown);
                        typeCooldowns.put(key, seconds);
                    }
                }
            }
        } catch (Exception e) {
            LagXpert.getInstance().getLogger().warning(
                    "[AlertPipeline] Failed to load per-type cooldowns: " + e.getMessage());
        }

        // Store default
        typeCooldowns.putIfAbsent("default", defaultCooldown);
    }

    /**
     * Context for a single alert. Immutable builder-style.
     */
    public static class AlertContext {
        private final AlertLevel level;
        private final String alertType;
        private final String contextKey;
        private final String messagePath;
        private final Map<String, Object> placeholders;
        private final String rawMessage;
        private final AlertTarget target;
        private final String permission;
        private final UUID targetPlayerUuid;
        private final boolean logToConsole;
        private final boolean logToActionLogger;

        private AlertContext(Builder builder) {
            this.level = builder.level;
            this.alertType = builder.alertType;
            this.contextKey = builder.contextKey;
            this.messagePath = builder.messagePath;
            this.placeholders = builder.placeholders;
            this.rawMessage = builder.rawMessage;
            this.target = builder.target;
            this.permission = builder.permission;
            this.targetPlayerUuid = builder.targetPlayerUuid;
            this.logToConsole = builder.logToConsole;
            this.logToActionLogger = builder.logToActionLogger;
        }

        public AlertLevel getLevel() { return level; }
        public String getAlertType() { return alertType; }
        public String getContextKey() { return contextKey; }
        public String getMessagePath() { return messagePath; }
        public Map<String, Object> getPlaceholders() { return placeholders; }
        public String getRawMessage() { return rawMessage; }
        public AlertTarget getTarget() { return target; }
        public String getPermission() { return permission; }
        public UUID getTargetPlayerUuid() { return targetPlayerUuid; }
        public boolean isLogToConsole() { return logToConsole; }
        public boolean isLogToActionLogger() { return logToActionLogger; }

        /**
         * Generates a deduplication key from alertType + contextKey.
         */
        public String getDedupeKey() {
            if (contextKey != null && !contextKey.isEmpty()) {
                return alertType + "_" + contextKey;
            }
            return alertType;
        }

        public static Builder builder(AlertLevel level, String alertType) {
            return new Builder(level, alertType);
        }

        public static class Builder {
            private final AlertLevel level;
            private final String alertType;
            private String contextKey = null;
            private String messagePath = null;
            private Map<String, Object> placeholders = null;
            private String rawMessage = null;
            private AlertTarget target = AlertTarget.CONSOLE;
            private String permission = null;
            private UUID targetPlayerUuid = null;
            private boolean logToConsole = true;
            private boolean logToActionLogger = true;

            public Builder(AlertLevel level, String alertType) {
                this.level = level;
                this.alertType = alertType;
            }

            public Builder contextKey(String contextKey) {
                this.contextKey = contextKey;
                return this;
            }

            public Builder messagePath(String messagePath) {
                this.messagePath = messagePath;
                return this;
            }

            public Builder placeholders(Map<String, Object> placeholders) {
                this.placeholders = placeholders;
                return this;
            }

            public Builder rawMessage(String rawMessage) {
                this.rawMessage = rawMessage;
                return this;
            }

            public Builder target(AlertTarget target) {
                this.target = target;
                return this;
            }

            public Builder permission(String permission) {
                this.permission = permission;
                return this;
            }

            public Builder targetPlayerUuid(UUID targetPlayerUuid) {
                this.targetPlayerUuid = targetPlayerUuid;
                return this;
            }

            public Builder logToConsole(boolean logToConsole) {
                this.logToConsole = logToConsole;
                return this;
            }

            public Builder logToActionLogger(boolean logToActionLogger) {
                this.logToActionLogger = logToActionLogger;
                return this;
            }

            public AlertContext build() {
                return new AlertContext(this);
            }
        }
    }

    /**
     * Sends an alert through the pipeline.
     * This is the SINGLE entry point for all plugin alerts.
     *
     * @param ctx AlertContext with all delivery parameters
     * @return true if the alert was delivered, false if suppressed
     */
    public boolean send(AlertContext ctx) {
        if (ctx == null) {
            return false;
        }

        // Global burst protection
        if (!checkGlobalRateLimit()) {
            totalAlertsSuppressed.incrementAndGet();
            return false;
        }

        // Resolve the message text
        String message = resolveMessage(ctx);
        if (message == null || message.isEmpty()) {
            return false;
        }

        boolean delivered = false;

        // Deliver to console
        if (ctx.isLogToConsole()) {
            deliverToConsole(ctx.getLevel(), message);
            delivered = true;
        }

        // Deliver to players based on target
        switch (ctx.getTarget()) {
            case ALL_PLAYERS:
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (canSendToPlayer(player, ctx)) {
                        deliverToPlayer(player, message, ctx.getLevel());
                        delivered = true;
                    }
                }
                break;

            case PLAYERS_WITH_PERMISSION:
                if (ctx.getPermission() != null) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.hasPermission(ctx.getPermission())) {
                            if (canSendToPlayer(player, ctx)) {
                                deliverToPlayer(player, message, ctx.getLevel());
                                delivered = true;
                            }
                        }
                    }
                }
                break;

            case AFFECTED_PLAYERS:
                if (ctx.getTargetPlayerUuid() != null) {
                    Player player = Bukkit.getPlayer(ctx.getTargetPlayerUuid());
                    if (player != null && player.isOnline()) {
                        if (canSendToPlayer(player, ctx)) {
                            deliverToPlayer(player, message, ctx.getLevel());
                            delivered = true;
                        }
                    }
                }
                break;

            case CONSOLE:
            default:
                // Already delivered to console above
                break;
        }

        if (delivered) {
            totalAlertsSent.incrementAndGet();
        } else {
            totalAlertsSuppressed.incrementAndGet();
        }

        return delivered;
    }

    /**
     * Resolves the message string from messagePath or rawMessage.
     * Does NOT add prefix — the prefix is handled by delivery methods (console logger, player messages).
     */
    private String resolveMessage(AlertContext ctx) {
        if (ctx.getRawMessage() != null && !ctx.getRawMessage().isEmpty()) {
            return MessageManager.color(ctx.getRawMessage());
        }

        if (ctx.getMessagePath() != null && !ctx.getMessagePath().isEmpty()) {
            if (ctx.getPlaceholders() != null && !ctx.getPlaceholders().isEmpty()) {
                return MessageManager.getFormatted(ctx.getMessagePath(), ctx.getPlaceholders());
            }
            return MessageManager.get(ctx.getMessagePath());
        }

        return null;
    }

    /**
     * Checks global bursts — max N alerts per second across ALL types.
     */
    private boolean checkGlobalRateLimit() {
        long now = System.currentTimeMillis();
        long windowStart = secondWindowStart.get();

        if (now - windowStart > 1000) {
            // New second window
            if (secondWindowStart.compareAndSet(windowStart, now)) {
                alertsThisSecond.set(0);
            }
        }

        return alertsThisSecond.incrementAndGet() <= maxAlertsPerSecond;
    }

    /**
     * Checks if an alert can be sent to a specific player considering cooldowns.
     */
    private boolean canSendToPlayer(Player player, AlertContext ctx) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        // EMERGENCY and CRITICAL alerts always bypass cooldowns
        if (ctx.getLevel() == AlertLevel.EMERGENCY ||
                ctx.getLevel() == AlertLevel.CRITICAL) {
            return true;
        }

        // Check permission if specified
        if (ctx.getPermission() != null && !player.hasPermission(ctx.getPermission())) {
            return false;
        }

        // Get cooldown for this alert type
        int cooldownSeconds = typeCooldowns.getOrDefault(
                ctx.getAlertType(),
                typeCooldowns.getOrDefault("default", 15));

        if (cooldownSeconds <= 0) {
            return true; // Cooldowns disabled
        }

        String dedupeKey = ctx.getDedupeKey();
        UUID playerUuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Map<String, Long> playerMap = playerCooldowns.computeIfAbsent(
                playerUuid, k -> new ConcurrentHashMap<>());

        Long lastSent = playerMap.get(dedupeKey);
        if (lastSent != null && (now - lastSent) < (cooldownSeconds * 1000L)) {
            return false; // On cooldown
        }

        // Update cooldown
        playerMap.put(dedupeKey, now);
        return true;
    }

    /**
     * Delivers a message to console with appropriate log level.
     */
    private void deliverToConsole(AlertLevel level, String message) {
        String strippedMessage = org.bukkit.ChatColor.stripColor(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', message));

        switch (level) {
            case EMERGENCY:
            case CRITICAL:
                LagXpert.getInstance().getLogger().severe(strippedMessage);
                break;
            case WARNING:
                LagXpert.getInstance().getLogger().warning(strippedMessage);
                break;
            case INFO:
                LagXpert.getInstance().getLogger().info(strippedMessage);
                break;
            case DEBUG:
                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().info("[DEBUG] " + strippedMessage);
                }
                break;
        }
    }

    /**
     * Delivers a message to a specific player.
     */
    private void deliverToPlayer(Player player, String message, AlertLevel level) {
        if (player == null || !player.isOnline()) {
            return;
        }

        String prefixedMessage = MessageManager.getPrefix() + message;

        MessageType messageType;
        switch (level) {
            case EMERGENCY:
            case CRITICAL:
                messageType = MessageType.BOTH;
                break;
            case WARNING:
                messageType = MessageType.CHAT;
                break;
            default:
                messageType = MessageType.ACTIONBAR;
                break;
        }

        MessageManager.sendMessage(player, prefixedMessage, messageType);
    }

    /**
     * Clears all cooldowns for a player (call on PlayerQuitEvent).
     */
    public void clearCooldowns(UUID playerUuid) {
        if (playerUuid != null) {
            playerCooldowns.remove(playerUuid);
        }
    }

    /**
     * Clears all cooldown data and resets statistics.
     */
    public void reset() {
        playerCooldowns.clear();
        totalAlertsSent.set(0);
        totalAlertsSuppressed.set(0);
        alertsThisSecond.set(0);
    }

    /**
     * Returns pipeline statistics for commands/API.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("total_sent", totalAlertsSent.get());
        stats.put("total_suppressed", totalAlertsSuppressed.get());
        stats.put("active_player_cooldowns", playerCooldowns.size());
        stats.put("type_cooldowns_configured", typeCooldowns.size());
        return stats;
    }

    /**
     * Generates a context key from an alert type and chunk.
     * Compatible with existing AlertCooldownManager.generateAlertKey().
     */
    public static String generateContextKey(String alertType, org.bukkit.Chunk chunk) {
        if (alertType == null || chunk == null) {
            return "invalid";
        }
        return alertType + "_" + chunk.getWorld().getName() +
                "_c_" + chunk.getX() + "_" + chunk.getZ();
    }
}
