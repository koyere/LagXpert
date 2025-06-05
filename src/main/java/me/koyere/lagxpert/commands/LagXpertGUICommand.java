package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.gui.GUIManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Command executor for the /lagxpertgui command.
 * Provides GUI-based configuration management for LagXpert.
 * Supports subcommands for various GUI operations.
 */
public class LagXpertGUICommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "open", "close", "reload", "sessions", "help"
    );

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Only players can use GUI commands
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageManager.getPrefixedMessage("general.players-only"));
            return true;
        }

        Player player = (Player) sender;

        // Check if GUI system is initialized
        if (!GUIManager.isInitialized()) {
            player.sendMessage(MessageManager.getPrefixedMessage("gui.system-not-initialized"));
            return true;
        }

        // Handle subcommands
        if (args.length == 0) {
            // Default behavior: open main GUI
            return handleOpenCommand(player);
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "open":
                return handleOpenCommand(player);
            case "close":
                return handleCloseCommand(player);
            case "reload":
                return handleReloadCommand(player);
            case "sessions":
                return handleSessionsCommand(player);
            case "help":
                return handleHelpCommand(player);
            default:
                player.sendMessage(MessageManager.getPrefixedFormattedMessage("general.unknown-subcommand",
                        Map.of("subcommand", subcommand)));
                return true;
        }
    }

    /**
     * Handles the 'open' subcommand to open the main configuration GUI.
     */
    private boolean handleOpenCommand(Player player) {
        GUIManager guiManager = GUIManager.getInstance();

        if (guiManager.openConfigGUI(player)) {
            return true;
        } else {
            // Error message is handled by GUIManager
            return true;
        }
    }

    /**
     * Handles the 'close' subcommand to close any open GUI.
     */
    private boolean handleCloseCommand(Player player) {
        GUIManager guiManager = GUIManager.getInstance();

        if (guiManager.hasActiveSession(player)) {
            guiManager.closeGUI(player);
        } else {
            player.sendMessage(MessageManager.getPrefixedMessage("gui.no-gui-open"));
        }

        return true;
    }

    /**
     * Handles the 'reload' subcommand to reload GUI configurations.
     * Requires admin permission.
     */
    private boolean handleReloadCommand(Player player) {
        if (!player.hasPermission("lagxpert.admin")) {
            player.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
            return true;
        }

        try {
            GUIManager guiManager = GUIManager.getInstance();
            guiManager.reload();
            player.sendMessage(MessageManager.getPrefixedMessage("gui.reload-success"));
        } catch (Exception e) {
            player.sendMessage(MessageManager.getPrefixedMessage("gui.reload-failed"));
        }

        return true;
    }

    /**
     * Handles the 'sessions' subcommand to display GUI session information.
     * Requires admin permission.
     */
    private boolean handleSessionsCommand(Player player) {
        if (!player.hasPermission("lagxpert.admin")) {
            player.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
            return true;
        }

        GUIManager guiManager = GUIManager.getInstance();
        Map<String, Object> stats = guiManager.getSessionStatistics();

        player.sendMessage(MessageManager.color("&6=== GUI Session Statistics ==="));
        player.sendMessage(MessageManager.color("&7Active Sessions: &e" + stats.get("active_sessions")));
        player.sendMessage(MessageManager.color("&7Max Concurrent: &e" + stats.get("max_concurrent_sessions")));
        player.sendMessage(MessageManager.color("&7Session Timeout: &e" + stats.get("session_timeout_minutes") + " minutes"));

        @SuppressWarnings("unchecked")
        Map<String, Integer> sessionTypes = (Map<String, Integer>) stats.get("sessions_by_type");
        if (sessionTypes != null && !sessionTypes.isEmpty()) {
            player.sendMessage(MessageManager.color("&7Sessions by Type:"));
            for (Map.Entry<String, Integer> entry : sessionTypes.entrySet()) {
                player.sendMessage(MessageManager.color("&8  - &e" + entry.getKey() + "&7: &e" + entry.getValue()));
            }
        }

        return true;
    }

    /**
     * Handles the 'help' subcommand to display command usage information.
     */
    private boolean handleHelpCommand(Player player) {
        player.sendMessage(MessageManager.color("&6=== LagXpert GUI Commands ==="));
        player.sendMessage(MessageManager.color("&e/lagxpertgui &7- Open the configuration GUI"));
        player.sendMessage(MessageManager.color("&e/lagxpertgui open &7- Open the configuration GUI"));
        player.sendMessage(MessageManager.color("&e/lagxpertgui close &7- Close any open GUI"));
        player.sendMessage(MessageManager.color("&e/lagxpertgui help &7- Show this help message"));

        if (player.hasPermission("lagxpert.admin")) {
            player.sendMessage(MessageManager.color("&6=== Admin Commands ==="));
            player.sendMessage(MessageManager.color("&e/lagxpertgui reload &7- Reload GUI configurations"));
            player.sendMessage(MessageManager.color("&e/lagxpertgui sessions &7- View session statistics"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            // First argument: subcommands
            String input = args[0].toLowerCase();

            for (String subcommand : SUBCOMMANDS) {
                if (subcommand.startsWith(input)) {
                    // Check permissions for admin-only commands
                    if ((subcommand.equals("reload") || subcommand.equals("sessions"))
                            && !player.hasPermission("lagxpert.admin")) {
                        continue;
                    }
                    completions.add(subcommand);
                }
            }
        }

        return completions;
    }
}