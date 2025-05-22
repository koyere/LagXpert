package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.tasks.ItemCleanerTask; // Ensure this matches your refactored ItemCleanerTask
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter; // Import TabCompleter
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the /clearitems command and its subcommands for manual item clearing.
 * Subcommands:
 * - /clearitems         -> Clears items globally (all configured worlds).
 * - /clearitems all     -> Same as /clearitems.
 * - /clearitems <world> -> Clears items only in the specified world.
 */
public class ClearItemsCommand implements CommandExecutor, TabCompleter { // Implement TabCompleter

    private static final List<String> SUBCOMMAND_ARGS = Arrays.asList("all");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // General permission required for any subcommand usage
        if (!sender.hasPermission("lagxpert.clearitems")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
            return true;
        }

        int totalRemoved;
        Player actor = (sender instanceof Player) ? (Player) sender : null;

        // Subcommand: /clearitems <world>
        if (args.length == 1 && !args[0].equalsIgnoreCase("all")) {
            if (!sender.hasPermission("lagxpert.clearitems.world")) {
                sender.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
                return true;
            }

            World world = Bukkit.getWorld(args[0]);
            if (world == null) {
                sender.sendMessage(MessageManager.getPrefixedMessage("clearitems.world-not-found").replace("{world}", args[0]));
                return true;
            }

            // Use the refactored static method from ItemCleanerTask, passing the actor
            totalRemoved = ItemCleanerTask.runManualCleanupForWorld(actor, world);

            // Send feedback to the command sender
            sender.sendMessage(MessageManager.getPrefixedMessage("clearitems.removed-sender")
                    .replace("{count}", String.valueOf(totalRemoved))
                    .replace("{world_or_all}", world.getName()));

            // Broadcast message (if items were removed)
            if (totalRemoved > 0) {
                Bukkit.broadcastMessage(MessageManager.getPrefixedMessage("clearitems.broadcast")
                        .replace("{count}", String.valueOf(totalRemoved))
                        .replace("{world_or_all}", world.getName())
                        .replace("{player}", sender.getName())); // Add player who initiated
            }
            return true;
        }

        // Subcommand: /clearitems or /clearitems all (Global cleanup)
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("all"))) {
            if (!sender.hasPermission("lagxpert.clearitems.all")) {
                sender.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
                return true;
            }

            // Use the refactored static method from ItemCleanerTask
            totalRemoved = ItemCleanerTask.runManualCleanupAllWorlds(actor);

            // Send feedback to the command sender
            sender.sendMessage(MessageManager.getPrefixedMessage("clearitems.removed-sender")
                    .replace("{count}", String.valueOf(totalRemoved))
                    .replace("{world_or_all}", "all loaded/configured worlds"));

            // Broadcast message (if items were removed)
            if (totalRemoved > 0) {
                Bukkit.broadcastMessage(MessageManager.getPrefixedMessage("clearitems.broadcast")
                        .replace("{count}", String.valueOf(totalRemoved))
                        .replace("{world_or_all}", "all") // Using "all" for broadcast clarity
                        .replace("{player}", sender.getName())); // Add player who initiated
            }
            return true;
        }

        // If arguments are provided but don't match known subcommands
        sender.sendMessage(MessageManager.getPrefixedMessage("clearitems.usage")); // Suggest a usage message key
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("lagxpert.clearitems")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(SUBCOMMAND_ARGS);
            // Add world names if user has permission for world-specific clear
            if (sender.hasPermission("lagxpert.clearitems.world")) {
                Bukkit.getWorlds().forEach(world -> completions.add(world.getName()));
            }
            // Filter based on what the user has typed
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList(); // No further arguments to complete
    }
}