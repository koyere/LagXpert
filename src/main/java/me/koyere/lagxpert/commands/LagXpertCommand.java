package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.system.AbyssManager;
import me.koyere.lagxpert.system.ActionLogger;
import me.koyere.lagxpert.system.AdaptiveThresholdEngine;
import me.koyere.lagxpert.system.EmergencyController;
import me.koyere.lagxpert.system.EmergencyResponseCoordinator;
import me.koyere.lagxpert.system.LagDiagnosticsEngine;
import me.koyere.lagxpert.system.PerformanceHistory;
import me.koyere.lagxpert.system.ProfileManager;
import me.koyere.lagxpert.monitoring.TPSMonitor;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
// Unused Bukkit imports for specific types (Chunk, Material, etc.) are removed for this command's current logic.
// They would be needed if specific subcommands here performed direct world manipulation.
import org.bukkit.Bukkit; // Needed for Bukkit.getWorlds() in TabCompleter
import org.bukkit.World;  // Needed for Bukkit.getWorlds() in TabCompleter
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
// Player import is not directly used in this class if subcommands are handled by other classes or checks are internal to them
// import org.bukkit.entity.Player;


import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList; // Needed for TabCompleter world list
import java.util.Arrays;
import java.util.Collections;
// HashMap and Map imports are not strictly needed in this specific version of LagXpertCommand
// if InspectCommand handles its own placeholder maps.
// import java.util.HashMap;
// import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Handles the main /lagxpert command and its subcommands.
 * Provides functionalities like help, reloading configuration, inspecting chunks,
 * and informing users about other relevant commands like /chunkstatus.
 */
public class LagXpertCommand implements CommandExecutor, TabCompleter {

    // A list of root subcommands for easy management and tab-completion.
    private static final List<String> ROOT_SUBCOMMANDS = Arrays.asList(
            "help", "reload", "inspect", "chunkload",
            "optimize", "status", "emergency", "profile", "diagnose");

    /** Subcommands that require the general admin permission. */
    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList(
            "reload", "inspect", "optimize", "status", "emergency", "profile", "diagnose");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // If no arguments are provided, or "help" is explicitly requested, show the help message.
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(); // Process subcommand in lowercase for case-insensitivity.

        switch (subCommand) {
            case "reload":
                return handleReload(sender);
            case "inspect":
                // Check permission for the inspect subcommand
                if (!sender.hasPermission("lagxpert.admin")) { // Or a more specific "lagxpert.inspect" permission
                    sender.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
                    return true;
                }
                // Prepare arguments for InspectCommand.execute by removing "inspect" itself.
                // args[0] is "inspect", so we pass args starting from index 1.
                String[] inspectArgs = new String[0]; // Default to empty if only "/lagxpert inspect" is typed
                if (args.length > 1) {
                    inspectArgs = new String[args.length - 1];
                    System.arraycopy(args, 1, inspectArgs, 0, args.length - 1);
                }
                // Call the static execute method from InspectCommand class
                return InspectCommand.execute(sender, inspectArgs);
            case "chunkload":
                sender.sendMessage(MessageManager.getPrefixedMessage("chunkload.use-chunkstatus-command"));
                return true;
            case "optimize":
                return OptimizeAction.execute(sender);
            case "status":
                return handleStatus(sender);
            case "emergency":
                return handleEmergency(sender, args);
            case "profile":
                return handleProfile(sender, args);
            case "diagnose":
                return handleDiagnose(sender, args);
            default:
                // Handle any unknown subcommands.
                sender.sendMessage(MessageManager.getPrefixedMessage("general.invalid-command"));
                return true;
        }
    }

    /**
     * Handles the /lagxpert status subcommand.
     * Shows a text dashboard with server health, recent actions, and trends.
     */
    private boolean handleStatus(CommandSender sender) {
        if (!canRunAdminSubcommand(sender, "status")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
            return true;
        }

        // Every line is driven by messages.yml so the dashboard can be translated,
        // matching the convention the rest of the plugin already follows.
        String sep = MessageManager.get("status.separator");
        sender.sendMessage(sep);
        sender.sendMessage(MessageManager.get("status.header"));
        sender.sendMessage(sep);

        // Server state
        EmergencyController controller = EmergencyController.getInstance();
        EmergencyController.ServerState state = controller.getCurrentState();
        sender.sendMessage(MessageManager.getFormatted("status.server-state", mapOf(
                "state", state.name(),
                "state_color", stateColorCode(state))));

        // TPS
        double tps = TPSMonitor.getCurrentTPS();
        sender.sendMessage(MessageManager.getFormatted("status.tps", mapOf(
                "tps", String.format("%.2f", tps),
                "tps_color", tps >= 19 ? "&a" : tps >= 16 ? "&e" : "&c")));

        // Memory
        long maxMem = Runtime.getRuntime().maxMemory();
        long usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        double memPct = maxMem > 0 ? (double) usedMem / maxMem * 100.0 : 0;
        sender.sendMessage(MessageManager.getFormatted("status.memory", mapOf(
                "percent", String.format("%.1f", memPct),
                "used", usedMem / 1024 / 1024,
                "max", maxMem / 1024 / 1024,
                "mem_color", memPct < 70 ? "&a" : memPct < 85 ? "&e" : "&c")));

        // Active profile, since it explains why the limits are what they are
        String activeProfile = ProfileManager.getInstance().getActiveProfile();
        sender.sendMessage(MessageManager.getFormatted("status.profile", mapOf(
                "profile", activeProfile == null ? "none" : activeProfile)));
        sender.sendMessage("");

        // Limits actually being enforced right now
        AdaptiveThresholdEngine adaptive = AdaptiveThresholdEngine.getInstance();
        sender.sendMessage(MessageManager.get("status.limits-header"));
        sender.sendMessage(MessageManager.getFormatted("status.limits-health", mapOf(
                "health", String.format("%.2f", adaptive.getHealthFactor()))));
        sender.sendMessage(MessageManager.getFormatted("status.limits-values", mapOf(
                "mobs", String.format("%.0f%%", adaptive.getMobMultiplier() * 100),
                "storage", String.format("%.0f%%", adaptive.getStorageMultiplier() * 100),
                "entities", String.format("%.0f%%", adaptive.getEntityMultiplier() * 100),
                "redstone", String.format("%.0f%%", adaptive.getRedstoneMultiplier() * 100))));
        sender.sendMessage(adaptive.isCurrentlyThrottling()
                ? MessageManager.get("status.limits-throttling")
                : MessageManager.get("status.limits-normal"));
        sender.sendMessage("");

        // Which emergency responses are currently in force
        sender.sendMessage(MessageManager.get("status.emergency-header"));
        sender.sendMessage(MessageManager.getFormatted("status.emergency-values", mapOf(
                "spawns", yesNo(controller.shouldBlockNaturalSpawns()),
                "ai", yesNo(EmergencyResponseCoordinator.getInstance().isAiCurrentlyFrozen()),
                "preloader", controller.shouldPauseChunkPreloader() ? "&cpaused" : "&arunning",
                "redstone", controller.shouldDisableRedstoneClocks() ? "&cdisabled" : "&aallowed")));
        sender.sendMessage("");

        // Recent corrective actions
        java.util.List<ActionLogger.ActionRecord> recent = ActionLogger.getInstance().getRecent(5);
        sender.sendMessage(MessageManager.get("status.actions-header"));
        if (recent.isEmpty()) {
            sender.sendMessage(MessageManager.get("status.actions-none"));
        } else {
            for (ActionLogger.ActionRecord record : recent) {
                sender.sendMessage(MessageManager.getFormatted("status.actions-entry", mapOf(
                        "type", record.getType().name(),
                        "count", record.getCount(),
                        "age", formatAge(System.currentTimeMillis() - record.getTimestamp()))));
            }
        }
        sender.sendMessage("");

        // Historical trends
        PerformanceHistory history = PerformanceHistory.getInstance();
        sender.sendMessage(MessageManager.get("status.trends-header"));
        sender.sendMessage(MessageManager.getFormatted("status.trends-summary", mapOf(
                "snapshots", history.getSnapshotCount(),
                "peak_players", history.getPeakPlayerCount())));

        if (history.getSnapshotCount() > 0) {
            int peakHour = history.getPeakLagHour();
            sender.sendMessage(MessageManager.getFormatted("status.trends-peak-hour", mapOf(
                    "hour", peakHour,
                    "tps", String.format("%.2f", history.getAverageTpsForHour(peakHour)))));

            PerformanceHistory.TrendAnalysis trend = history.getEntityTrend(6);
            sender.sendMessage(MessageManager.getFormatted("status.trends-entities", mapOf(
                    "direction", trend.getDirection(),
                    "change", String.format("%+.0f", trend.getHourlyChange()))));
        }

        sender.sendMessage("");
        sender.sendMessage(MessageManager.get("status.hint"));
        sender.sendMessage(sep);
        return true;
    }

    /**
     * Handles the /lagxpert emergency subcommand.
     * /lagxpert emergency status  — shows current emergency state
     * /lagxpert emergency force-normal — forces return to NORMAL state
     */
    private boolean handleEmergency(CommandSender sender, String[] args) {
        if (!canRunAdminSubcommand(sender, "emergency")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
            return true;
        }

        if (args.length < 2) {
            // Show current emergency status
            EmergencyController ec = EmergencyController.getInstance();
            sender.sendMessage(MessageManager.color("&8&m------------------------------------------"));
            sender.sendMessage(MessageManager.color(
                    "&eEmergency Controller: &f" + ec.getCurrentState().name()));
            if (ec.getCurrentState() != EmergencyController.ServerState.NORMAL) {
                sender.sendMessage(MessageManager.color(
                        "  &7Time in state: &f" + (ec.getTimeInCurrentStateMs() / 1000) + "s"));
                sender.sendMessage(MessageManager.color(
                        "  &7Mob Cap: &f" + String.format("%.0f%%", ec.getMobCapMultiplier() * 100)));
                sender.sendMessage(MessageManager.color(
                        "  &7Block Spawns: &f" + (ec.shouldBlockNaturalSpawns() ? "&cYes" : "&aNo")));
                sender.sendMessage(MessageManager.color(
                        "  &7AI Distance: &f" + ec.getAIDistanceThreshold() + " blocks"));
                sender.sendMessage(MessageManager.color(
                        "  &7Preloader: &f" + (ec.shouldPauseChunkPreloader() ? "&cPaused" : "&aRunning")));
                sender.sendMessage(MessageManager.color(
                        "  &7Redstone Clocks: &f" + (ec.shouldDisableRedstoneClocks() ? "&cDisabled" : "&aAllowed")));
            }
            sender.sendMessage(MessageManager.color(
                    "&7Usage: /lagxpert emergency [status|force-normal]"));
            return true;
        }

        if (args[1].equalsIgnoreCase("force-normal")) {
            boolean result = EmergencyController.getInstance().forceNormal();
            if (result) {
                sender.sendMessage(MessageManager.getPrefixedMessage(
                        "alerts.messages.emergency-controller.force-normal"));
            } else {
                sender.sendMessage(MessageManager.color("&cForce-normal is not allowed in config."));
            }
            return true;
        }

        if (args[1].equalsIgnoreCase("status")) {
            EmergencyController ec = EmergencyController.getInstance();
            java.util.Map<String, Object> status = ec.getStatus();
            sender.sendMessage(MessageManager.color("&eEmergency Controller Status:"));
            for (java.util.Map.Entry<String, Object> entry : status.entrySet()) {
                sender.sendMessage(MessageManager.color(
                        "  &7" + entry.getKey() + ": &f" + entry.getValue()));
            }
            return true;
        }

        sender.sendMessage(MessageManager.color("&cUnknown emergency subcommand. Use: status or force-normal"));
        return true;
    }

    /**
     * Handles the /lagxpert diagnose subcommand.
     *
     * /lagxpert diagnose            — opens the GUI for players, prints for console
     * /lagxpert diagnose chat       — force the text report even in-game
     * /lagxpert diagnose refresh    — force a fresh scan
     */
    private boolean handleDiagnose(CommandSender sender, String[] args) {
        if (!canRunAdminSubcommand(sender, "diagnose")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
            return true;
        }

        boolean forceRescan = args.length > 1 && args[1].equalsIgnoreCase("refresh");
        boolean forceChat = args.length > 1 && args[1].equalsIgnoreCase("chat");

        boolean useGui = (sender instanceof org.bukkit.entity.Player) && !forceChat;

        if (useGui) {
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
            me.koyere.lagxpert.gui.GUIManager.getInstance().openDiagnosticsGUI(player, forceRescan);
            return true;
        }

        // Text path: console, or a player who explicitly asked for chat output.
        LagDiagnosticsEngine engine = LagDiagnosticsEngine.getInstance();

        LagDiagnosticsEngine.DiagnosticsReport cached =
                forceRescan ? null : engine.getCachedReport();
        if (cached != null) {
            me.koyere.lagxpert.gui.DiagnosticsGUI.sendChatReport(sender, cached, 10);
            return true;
        }

        sender.sendMessage(MessageManager.getPrefixedMessage("diagnostics.scanning"));

        engine.requestReport(forceRescan, report -> {
            if (report == null) {
                sender.sendMessage(MessageManager.getPrefixedMessage("diagnostics.scan-in-progress"));
                return;
            }
            me.koyere.lagxpert.gui.DiagnosticsGUI.sendChatReport(sender, report, 10);
        });
        return true;
    }

    /**
     * Single source of truth for whether a sender may use an admin subcommand.
     *
     * Help output, tab completion and the handlers all consult this method. When
     * they each had their own check, the help menu advertised commands that the
     * execution path then refused.
     *
     * A holder of the broad {@code lagxpert.admin} node can use everything; the
     * finer-grained nodes exist so a limited admin can be granted just one action.
     */
    private static boolean canRunAdminSubcommand(CommandSender sender, String subCommand) {
        if (sender.hasPermission("lagxpert.admin")) {
            return true;
        }
        switch (subCommand.toLowerCase()) {
            case "optimize":
                return sender.hasPermission("lagxpert.admin.optimize");
            case "emergency":
                return sender.hasPermission("lagxpert.admin.emergency");
            case "profile":
                return sender.hasPermission(ProfileManager.getInstance().getRequiredPermission());
            case "diagnose":
                return sender.hasPermission("lagxpert.admin.diagnostics");
            case "status":
                return sender.hasPermission("lagxpert.admin.status");
            default:
                return false;
        }
    }

    /**
     * Handles the /lagxpert profile subcommand.
     *
     * /lagxpert profile                — shows the active profile and the list
     * /lagxpert profile list           — lists available profiles
     * /lagxpert profile &lt;name&gt;   — applies a profile
     * /lagxpert profile revert         — restores the pre-profile configuration
     */
    private boolean handleProfile(CommandSender sender, String[] args) {
        ProfileManager manager = ProfileManager.getInstance();

        // The required permission is operator-configurable in profiles.yml.
        String permission = manager.getRequiredPermission();
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
            return true;
        }

        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            sendProfileList(sender, manager);
            return true;
        }

        String action = args[1].toLowerCase();

        if (action.equals("revert")) {
            ProfileManager.ApplyResult result = manager.revert(sender.getName());
            sender.sendMessage(MessageManager.getPrefix() + MessageManager.color(
                    (result.isSuccess() ? "&a" : "&c") + result.getMessage()));
            return true;
        }

        if (!manager.hasProfile(action)) {
            java.util.Map<String, Object> placeholders = new java.util.HashMap<>();
            placeholders.put("profile", args[1]);
            placeholders.put("available", String.join(", ", manager.getProfileNames()));
            sender.sendMessage(MessageManager.getPrefixedFormattedMessage(
                    "profile.unknown", placeholders));
            return true;
        }

        ProfileManager.ApplyResult result = manager.apply(action, sender.getName());
        sender.sendMessage(MessageManager.getPrefix() + MessageManager.color(
                (result.isSuccess() ? "&a" : "&c") + result.getMessage()));

        if (result.isSuccess()) {
            if (manager.getAutoRevertMinutes() > 0) {
                java.util.Map<String, Object> placeholders = new java.util.HashMap<>();
                placeholders.put("minutes", manager.getAutoRevertMinutes());
                sender.sendMessage(MessageManager.getPrefixedFormattedMessage(
                        "profile.auto-revert-notice", placeholders));
            }
            if (!result.getUnknownKeys().isEmpty()) {
                java.util.Map<String, Object> placeholders = new java.util.HashMap<>();
                placeholders.put("keys", String.join(", ", result.getUnknownKeys()));
                sender.sendMessage(MessageManager.getPrefixedFormattedMessage(
                        "profile.unknown-keys", placeholders));
            }
        }
        return true;
    }

    /**
     * Prints the available profiles and which one is currently active.
     */
    private void sendProfileList(CommandSender sender, ProfileManager manager) {
        sender.sendMessage(MessageManager.color("&8&m------------------------------------------"));
        sender.sendMessage(MessageManager.get("profile.header"));

        String active = manager.getActiveProfile();
        if (active == null) {
            sender.sendMessage(MessageManager.get("profile.none-active"));
        } else {
            java.util.Map<String, Object> placeholders = new java.util.HashMap<>();
            placeholders.put("profile", active);
            placeholders.put("by", manager.getAppliedBy() == null ? "unknown" : manager.getAppliedBy());
            placeholders.put("age", formatAge(System.currentTimeMillis() - manager.getActiveSince()));
            sender.sendMessage(MessageManager.getFormatted("profile.active", placeholders));
        }

        sender.sendMessage("");
        for (String name : manager.getProfileNames()) {
            java.util.Map<String, Object> placeholders = new java.util.HashMap<>();
            placeholders.put("profile", name);
            placeholders.put("description", manager.getDescription(name));
            placeholders.put("marker", name.equalsIgnoreCase(active) ? "&a\u25b6 " : "&7\u2022 ");
            sender.sendMessage(MessageManager.getFormatted("profile.list-entry", placeholders));
        }

        sender.sendMessage("");
        sender.sendMessage(MessageManager.get("profile.usage"));
        sender.sendMessage(MessageManager.color("&8&m------------------------------------------"));
    }

    /**
     * Builds a placeholder map from alternating key/value arguments.
     *
     * Keeps the status dashboard readable; Java 11 has no map literal.
     */
    private static java.util.Map<String, Object> mapOf(Object... keyValuePairs) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            map.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return map;
    }

    private static String stateColorCode(EmergencyController.ServerState state) {
        switch (state) {
            case NORMAL: return "&a";
            case WARNING: return "&e";
            case CRITICAL: return "&c";
            case EMERGENCY: return "&4";
            default: return "&7";
        }
    }

    private static String yesNo(boolean value) {
        return value ? "&cyes" : "&ano";
    }

    private String formatAge(long ms) {
        if (ms < 60000) return (ms / 1000) + "s";
        if (ms < 3600000) return (ms / 60000) + "m";
        return (ms / 3600000) + "h";
    }
    /**
     * Sends a formatted help message to the CommandSender.
     * The message includes available commands based on the sender's permissions
     * and the current server time.
     *
     * @param sender The CommandSender to receive the help message.
     */
    private void sendHelp(CommandSender sender) {
        String headerFooter = MessageManager.color("&8&m------------------------------------------");
        String serverTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        sender.sendMessage(headerFooter);
        // Dynamically display plugin name and version from plugin.yml.
        if (LagXpert.getInstance() != null && LagXpert.getInstance().getDescription() != null) {
            sender.sendMessage(MessageManager.color("&b&lLagXpert &7v" + LagXpert.getInstance().getDescription().getVersion() + " &8- &fHelp Menu"));
        } else {
            sender.sendMessage(MessageManager.color("&b&lLagXpert &8- &fHelp Menu")); // Fallback if instance or description is null
        }
        sender.sendMessage(MessageManager.color("&7Server Time: &e" + serverTime));
        sender.sendMessage(""); // Empty line for better readability.

        // Display command help based on sender's permissions.
        // Assumes corresponding "help.command_name" keys exist in messages.yml.
        if (sender.hasPermission("lagxpert.use")) { // General permission for basic commands
            // help.inspect is now conditional on lagxpert.admin below
            sender.sendMessage(MessageManager.getPrefixedMessage("help.chunkstatus")); // Reminds about /chunkstatus
        }
        if (sender.hasPermission("lagxpert.admin")) { // Assuming inspect is an admin command
            sender.sendMessage(MessageManager.getPrefixedMessage("help.inspect"));
        }
        if (sender.hasPermission("lagxpert.abyss")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("help.abyss"));
        }
        if (sender.hasPermission("lagxpert.clearitems")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("help.clearitems"));
        }
        // Each admin line is gated by the same check the handler uses, so nothing
        // is advertised that the sender cannot actually run.
        if (sender.hasPermission("lagxpert.admin")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("help.reload"));
        }
        if (canRunAdminSubcommand(sender, "optimize")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("help.optimize"));
        }
        if (canRunAdminSubcommand(sender, "status")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("help.status"));
        }
        if (canRunAdminSubcommand(sender, "emergency")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("help.emergency"));
        }
        if (canRunAdminSubcommand(sender, "profile")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("help.profile"));
        }
        if (canRunAdminSubcommand(sender, "diagnose")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("help.diagnose"));
        }
        sender.sendMessage(headerFooter);
    }

    /**
     * Handles the /lagxpert reload subcommand.
     * Reloads all plugin configurations if the sender has the appropriate permission.
     *
     * @param sender The CommandSender who issued the command.
     * @return true if the command was handled.
     */
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("lagxpert.admin")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
            return true;
        }

        // Reload every config file AND re-apply it to every subsystem. Reloading
        // ConfigManager alone is not enough: several subsystems read their own YAML
        // directly and would otherwise keep serving startup values.
        java.util.List<String> failures = LagXpert.getInstance().reloadAllConfigurations();

        if (failures.isEmpty()) {
            sender.sendMessage(MessageManager.getPrefixedMessage("general.config-reloaded"));
        } else {
            java.util.Map<String, Object> placeholders = new java.util.HashMap<>();
            placeholders.put("count", failures.size());
            placeholders.put("subsystems", String.join(", ", failures));
            sender.sendMessage(MessageManager.getPrefixedFormattedMessage(
                    "general.config-reloaded-partial", placeholders));
        }

        ActionLogger.getInstance().log(
                ActionLogger.ActionType.MANUAL_RELOAD,
                null, null,
                failures.isEmpty() ? "Full reload" : "Reload with failures: " + failures,
                1, "player:" + sender.getName(), failures.isEmpty(), 0);

        if (LagXpert.getInstance() != null) {
            LagXpert.getInstance().getLogger().info("LagXpert configurations reloaded by " + sender.getName() + ".");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Provide tab completion for the root subcommands, filtered by what the user is typing.
            String currentArg = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (String sub : ROOT_SUBCOMMANDS) {
                if (!sub.toLowerCase().startsWith(currentArg)) {
                    continue;
                }
                // Only suggest what the sender can actually run. The check must match
                // the one performed at execution time, otherwise the command is
                // advertised and then refused.
                if (ADMIN_SUBCOMMANDS.contains(sub)) {
                    if (canRunAdminSubcommand(sender, sub)) {
                        completions.add(sub);
                    }
                } else { // help, chunkload
                    completions.add(sub);
                }
            }
            return completions;
        }

        // Tab completion for /lagxpert profile <name|list|revert>
        if (args[0].equalsIgnoreCase("profile") && args.length == 2) {
            ProfileManager manager = ProfileManager.getInstance();
            if (!sender.hasPermission(manager.getRequiredPermission())) {
                return Collections.emptyList();
            }
            List<String> options = new ArrayList<>(manager.getProfileNames());
            options.add("list");
            options.add("revert");
            String current = args[1].toLowerCase();
            return options.stream()
                    .filter(o -> o.toLowerCase().startsWith(current))
                    .collect(Collectors.toList());
        }

        // Tab completion for /lagxpert diagnose <chat|refresh>
        if (args[0].equalsIgnoreCase("diagnose") && args.length == 2
                && canRunAdminSubcommand(sender, "diagnose")) {
            String current = args[1].toLowerCase();
            return Arrays.asList("chat", "refresh").stream()
                    .filter(o -> o.startsWith(current))
                    .collect(Collectors.toList());
        }

        // Tab completion for /lagxpert emergency <status|force-normal>
        if (args[0].equalsIgnoreCase("emergency") && args.length == 2
                && sender.hasPermission("lagxpert.admin.emergency")) {
            String current = args[1].toLowerCase();
            return Arrays.asList("status", "force-normal").stream()
                    .filter(o -> o.startsWith(current))
                    .collect(Collectors.toList());
        }

        // Tab completion for /lagxpert inspect <x> <z> [world]
        if (args[0].equalsIgnoreCase("inspect") && sender.hasPermission("lagxpert.admin")) {
            if (args.length == 2) { // Suggesting <x> (placeholder text)
                return Collections.singletonList("<x>");
            } else if (args.length == 3) { // Suggesting <z> (placeholder text)
                return Collections.singletonList("<z>");
            } else if (args.length == 4) { // Suggesting actual [world_name]
                List<String> worldNames = new ArrayList<>();
                for (World world : Bukkit.getWorlds()) {
                    worldNames.add(world.getName());
                }
                String currentWorldArg = args[3].toLowerCase();
                return worldNames.stream()
                        .filter(name -> name.toLowerCase().startsWith(currentWorldArg))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList(); // No further tab completions by default.
    }
}