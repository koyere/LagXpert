package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.system.AbyssManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command that allows players to recover their recently cleared items
 * from the Abyss system, if enabled and within retention period.
 * Usage: /abyss
 * Permission: lagxpert.abyss
 */
public class AbyssCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // ✅ Ensure command is used by a player
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[LagXpert] This command can only be used by players.");
            return true;
        }

        // 🔐 Permission check
        if (!player.hasPermission("lagxpert.abyss")) {
            player.sendMessage(MessageManager.getPrefix() + MessageManager.get("general.no-permission"));
            return true;
        }

        // 🔄 Recover items from player's abyss file
        AbyssManager.tryRecover(player);
        return true;
    }
}
