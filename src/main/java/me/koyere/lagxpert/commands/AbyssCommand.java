package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.system.AbyssManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command that allows players to recover their recently cleared items
 * from the Abyss if enabled and within time limits.
 */
public class AbyssCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Must be a player
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        // Permission check
        if (!player.hasPermission("lagxpert.abyss")) {
            player.sendMessage(MessageManager.getPrefix() + MessageManager.get("general.no-permission"));
            return true;
        }

        // Attempt recovery
        AbyssManager.tryRecover(player);
        return true;
    }
}
