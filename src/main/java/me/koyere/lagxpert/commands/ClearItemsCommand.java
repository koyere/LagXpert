package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.tasks.ItemCleanerTask;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the /clearitems command and its subcommands.
 * Subcommands:
 * - /clearitems → limpia globalmente
 * - /clearitems all → lo mismo que global
 * - /clearitems <world> → limpia solo ese mundo si existe
 */
public class ClearItemsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // 🔐 Permiso general requerido para cualquier subcomando
        if (!sender.hasPermission("lagxpert.clearitems")) {
            sender.sendMessage(MessageManager.getPrefix() + MessageManager.get("general.no-permission"));
            return true;
        }

        int totalRemoved;

        // 🌍 Subcomando por mundo específico
        if (args.length == 1 && Bukkit.getWorld(args[0]) != null) {
            if (!sender.hasPermission("lagxpert.clearitems.world")) {
                sender.sendMessage(MessageManager.getPrefix() + MessageManager.get("general.no-permission"));
                return true;
            }

            World world = Bukkit.getWorld(args[0]);
            totalRemoved = ItemCleanerTask.runWorldCleanup(world);

            sender.sendMessage(MessageManager.getPrefix() + MessageManager.get("clearitems.removed").replace("{count}", String.valueOf(totalRemoved)));
            Bukkit.broadcastMessage(MessageManager.getPrefix() + MessageManager.get("clearitems.broadcast")
                    .replace("{count}", String.valueOf(totalRemoved))
                    .replace("{world}", world.getName()));

            return true;
        }

        // 🌍 Subcomando global (/clearitems o /clearitems all)
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("all"))) {
            if (!sender.hasPermission("lagxpert.clearitems.all")) {
                sender.sendMessage(MessageManager.getPrefix() + MessageManager.get("general.no-permission"));
                return true;
            }

            Player actor = (sender instanceof Player) ? (Player) sender : null;
            totalRemoved = ItemCleanerTask.runManualCleanup(actor);

            sender.sendMessage(MessageManager.getPrefix() + MessageManager.get("clearitems.removed").replace("{count}", String.valueOf(totalRemoved)));
            Bukkit.broadcastMessage(MessageManager.getPrefix() + MessageManager.get("clearitems.broadcast")
                    .replace("{count}", String.valueOf(totalRemoved))
                    .replace("{world}", "all"));

            return true;
        }

        // ❌ Si el mundo no existe o subcomando inválido
        sender.sendMessage(MessageManager.getPrefix() + MessageManager.get("clearitems.invalid"));
        return true;
    }
}
