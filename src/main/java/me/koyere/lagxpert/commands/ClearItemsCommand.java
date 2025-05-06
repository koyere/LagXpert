package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.tasks.ItemCleanerTask;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to immediately trigger the ItemCleaner task.
 * Usage: /clearitems
 * Requires permission: lagxpert.clearitems
 */
public class ClearItemsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // 🔐 Check permission
        if (!sender.hasPermission("lagxpert.clearitems")) {
            sender.sendMessage(MessageManager.getPrefix() + MessageManager.get("general.no-permission"));
            return true;
        }

        // 🧹 Trigger manual item cleanup with player association (if possible)
        Player actor = (sender instanceof Player) ? (Player) sender : null;
        int removed = ItemCleanerTask.runManualCleanup(actor);

        // ✅ Feedback to sender
        sender.sendMessage(MessageManager.getPrefix() + MessageManager.get("clearitems.start"));
        sender.sendMessage(MessageManager.getPrefix() + MessageManager.get("clearitems.removed").replace("{count}", String.valueOf(removed)));

        // 📢 Broadcast
        String broadcast = MessageManager.get("clearitems.broadcast").replace("{count}", String.valueOf(removed));
        Bukkit.broadcastMessage(MessageManager.getPrefix() + broadcast);

        // 🖥 Log to console
        LagXpert.getInstance().getLogger().info("Manual item cleanup triggered. Removed " + removed + " items.");

        return true;
    }
}
