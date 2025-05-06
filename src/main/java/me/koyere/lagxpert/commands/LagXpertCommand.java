package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Handles the main /lagxpert command with subcommands like help, reload, inspect, and chunkload.
 */
public class LagXpertCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Show help if no arguments or explicitly requested
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        // Handle reload
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("lagxpert.admin")) {
                sender.sendMessage(MessageManager.getPrefix() + MessageManager.get("general.no-permission"));
                return true;
            }
            ConfigManager.loadAll();
            MessageManager.loadMessages();
            sender.sendMessage(MessageManager.getPrefix() + MessageManager.get("reload.success"));
            return true;
        }

        // Show usage for inspect
        if (args[0].equalsIgnoreCase("inspect")) {
            sender.sendMessage(MessageManager.getPrefix() + MessageManager.get("inspect.usage"));
            return true;
        }

        // Redirect for legacy chunkload (now a standalone command)
        if (args[0].equalsIgnoreCase("chunkload")) {
            sender.sendMessage(MessageManager.getPrefix() + "/chunkstatus is now a standalone command.");
            return true;
        }

        // Unknown subcommand
        sender.sendMessage(MessageManager.getPrefix() + MessageManager.get("general.invalid-command"));
        return true;
    }

    /**
     * Displays a formatted help message with available subcommands, based on permissions.
     *
     * @param sender The command sender (player or console).
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageManager.get("help.header"));

        if (sender.hasPermission("lagxpert.use")) {
            sender.sendMessage(MessageManager.get("help.inspect"));
            sender.sendMessage(MessageManager.get("help.chunkstatus"));
        }

        if (sender.hasPermission("lagxpert.abyss")) {
            sender.sendMessage(MessageManager.get("help.abyss"));
        }

        if (sender.hasPermission("lagxpert.clearitems")) {
            sender.sendMessage(MessageManager.get("help.clearitems"));
        }

        if (sender.hasPermission("lagxpert.admin")) {
            sender.sendMessage(MessageManager.get("help.reload"));
        }

        sender.sendMessage(MessageManager.get("help.footer"));
    }

    /**
     * Provides tab completion for subcommands under /lagxpert.
     *
     * @return list of matching subcommands
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>(Arrays.asList("help", "reload", "inspect", "chunkload"));
            return subcommands.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }
}
