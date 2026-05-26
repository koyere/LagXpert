package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.system.AbyssManager;
import me.koyere.lagxpert.system.ActionLogger;
import me.koyere.lagxpert.system.AdaptiveThresholdEngine;
import me.koyere.lagxpert.system.EmergencyController;
import me.koyere.lagxpert.system.PerformanceHistory;
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
            "optimize", "status", "emergency");

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
        if (!sender.hasPermission("lagxpert.admin")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
            return true;
        }

        String sep = MessageManager.color("&8&m------------------------------------------");
        sender.sendMessage(sep);
        sender.sendMessage(MessageManager.color("&b&lLagXpert Status Dashboard"));
        sender.sendMessage(sep);

        // Server State
        EmergencyController.ServerState state = EmergencyController.getInstance().getCurrentState();
        String stateColor = state == EmergencyController.ServerState.NORMAL ? "&a" :
                state == EmergencyController.ServerState.WARNING ? "&e" :
                        state == EmergencyController.ServerState.CRITICAL ? "&c" : "&4";
        sender.sendMessage(MessageManager.color(
                "&7Server State: " + stateColor + state.name()));

        // TPS
        double tps = TPSMonitor.getCurrentTPS();
        String tpsColor = tps >= 19 ? "&a" : tps >= 16 ? "&e" : "&c";
        sender.sendMessage(MessageManager.color(
                "&7TPS: " + tpsColor + String.format("%.1f", tps) + " &7/ 20.0"));

        // Memory
        long maxMem = Runtime.getRuntime().maxMemory();
        long usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        double memPct = maxMem > 0 ? (double) usedMem / maxMem * 100.0 : 0;
        String memColor = memPct < 70 ? "&a" : memPct < 85 ? "&e" : "&c";
        sender.sendMessage(MessageManager.color(
                "&7Memory: " + memColor + String.format("%.1f%%", memPct) +
                        " &7(" + usedMem / 1024 / 1024 + "MB)"));
        sender.sendMessage("");

        // Adaptive Thresholds
        AdaptiveThresholdEngine ate = AdaptiveThresholdEngine.getInstance();
        sender.sendMessage(MessageManager.color("&eAdaptive Multipliers:"));
        sender.sendMessage(MessageManager.color(
                "  &7Health Factor: &f" + String.format("%.2f", ate.getHealthFactor())));
        sender.sendMessage(MessageManager.color(
                "  &7Mob Limit: &f" + String.format("%.0f%%", ate.getMobMultiplier() * 100)));
        sender.sendMessage(MessageManager.color(
                "  &7Storage Limit: &f" + String.format("%.0f%%", ate.getStorageMultiplier() * 100)));
        sender.sendMessage("");

        // Recent Actions
        java.util.List<ActionLogger.ActionRecord> recent = ActionLogger.getInstance().getRecent(5);
        sender.sendMessage(MessageManager.color("&eRecent Actions:"));
        if (recent.isEmpty()) {
            sender.sendMessage(MessageManager.color("  &7No actions recorded yet."));
        } else {
            for (ActionLogger.ActionRecord record : recent) {
                String age = formatAge(System.currentTimeMillis() - record.getTimestamp());
                sender.sendMessage(MessageManager.color(
                        "  &7• &f" + record.getType().name() +
                                " &7(" + record.getCount() + ") &8" + age + " ago"));
            }
        }
        sender.sendMessage("");

        // Performance Trends
        PerformanceHistory ph = PerformanceHistory.getInstance();
        sender.sendMessage(MessageManager.color("&ePerformance Trends:"));
        sender.sendMessage(MessageManager.color(
                "  &7Snapshots: &f" + ph.getSnapshotCount()));
        sender.sendMessage(MessageManager.color(
                "  &7Peak Players: &f" + ph.getPeakPlayerCount()));
        sender.sendMessage(MessageManager.color(
                "  &7Peak Lag Hour: &f" + ph.getPeakLagHour() + ":00" +
                        " &7(TPS: " + String.format("%.1f", ph.getAverageTpsForHour(ph.getPeakLagHour())) + ")"));

        PerformanceHistory.TrendAnalysis trend = ph.getEntityTrend(6);
        sender.sendMessage(MessageManager.color(
                "  &7Entity Trend: &f" + trend.getDirection() +
                        " &7(" + String.format("%.0f", trend.getHourlyChange()) + "/hr)"));

        sender.sendMessage(sep);
        return true;
    }

    /**
     * Handles the /lagxpert emergency subcommand.
     * /lagxpert emergency status  — shows current emergency state
     * /lagxpert emergency force-normal — forces return to NORMAL state
     */
    private boolean handleEmergency(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lagxpert.admin.emergency")) {
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
        if (sender.hasPermission("lagxpert.admin")) { // Admin-specific commands
            sender.sendMessage(MessageManager.getPrefixedMessage("help.reload"));
            sender.sendMessage(MessageManager.getPrefixedMessage("help.optimize"));
            sender.sendMessage(MessageManager.getPrefixedMessage("help.status"));
            sender.sendMessage(MessageManager.getPrefixedMessage("help.emergency"));
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

        // Reload all plugin configurations.
        ConfigManager.loadAll();    // This reloads all YAMLs and re-initializes MessageManager.
        AbyssManager.loadConfig();  // AbyssManager fetches its reloaded config values from ConfigManager.

        sender.sendMessage(MessageManager.getPrefixedMessage("general.config-reloaded")); // Confirmation message.
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
                if (sub.toLowerCase().startsWith(currentArg)) {
                    // Permission-based tab completion
                    if (sub.equalsIgnoreCase("reload") || sub.equalsIgnoreCase("inspect")
                            || sub.equalsIgnoreCase("optimize") || sub.equalsIgnoreCase("status")
                            || sub.equalsIgnoreCase("emergency")) {
                        if (sender.hasPermission("lagxpert.admin")) {
                            completions.add(sub);
                        }
                    } else { // For help, chunkload (which is just a message)
                        completions.add(sub);
                    }
                }
            }
            return completions;
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