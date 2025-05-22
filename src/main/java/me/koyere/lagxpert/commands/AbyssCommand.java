package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.system.AbyssManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command that allows players to recover their recently cleared items
 * from the Abyss system, if the system is enabled and items are within the retention period.
 * Usage: /abyss
 * Permission: lagxpert.abyss
 */
public class AbyssCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Check if the command sender is a player
        // Reverted from pattern matching for Java 11 compatibility
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageManager.getPrefixedMessage("general.players-only"));
            return true;
        }
        Player player = (Player) sender; // Explicit cast after instanceof check

        // Check if the player has the required permission
        if (!player.hasPermission("lagxpert.abyss")) {
            player.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
            return true;
        }

        // No arguments are expected for this command.
        // Further argument handling can be added here if needed in the future.

        // Attempt to recover items from the player's abyss file
        // All user feedback messages (success, empty, errors) are handled by AbyssManager.tryRecover()
        AbyssManager.tryRecover(player);
        return true;
    }
}