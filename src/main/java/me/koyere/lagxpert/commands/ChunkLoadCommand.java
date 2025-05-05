package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.utils.ChunkUtils;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Chunk;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Handles the /lagxpert chunkload command for players.
 * Displays basic information about the current chunk's load: mobs, hoppers, chests.
 */
public class ChunkLoadCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Verify sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command is only for players.");
            return true;
        }

        Player player = (Player) sender;

        // Permission check
        if (!player.hasPermission("lagxpert.use")) {
            player.sendMessage(MessageManager.get("general.no-permission"));
            return true;
        }

        // Handle subcommands
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("chunkload")) {
                return handleChunkLoad(player);
            }
        }

        // Default response for invalid subcommand
        player.sendMessage(MessageManager.get("general.invalid-command"));
        return true;
    }

    /**
     * Handles the actual chunkload data display for the player.
     *
     * @param player the player executing the command
     */
    private boolean handleChunkLoad(Player player) {
        Chunk chunk = player.getLocation().getChunk();

        int entityCount = ChunkUtils.countEntitiesInChunk(chunk);
        int hopperCount = ChunkUtils.countBlocksInChunk(chunk, "HOPPER");
        int chestCount = ChunkUtils.countBlocksInChunk(chunk, "CHEST");

        // Compose placeholder map
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("entities", String.valueOf(entityCount));
        placeholders.put("hoppers", String.valueOf(hopperCount));
        placeholders.put("chests", String.valueOf(chestCount));

        // Send info message
        player.sendMessage(MessageManager.getPrefix() + MessageManager.getFormatted("chunkload.info", placeholders));

        // Warn if chunk is overloaded (simple check)
        if (entityCount > 40 || hopperCount > 8 || chestCount > 20) {
            player.sendMessage(MessageManager.getPrefix() + MessageManager.get("chunkload.limit-warning"));
        }

        return true;
    }

    /**
     * Provides tab completion for the /lagxpert command.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("chunkload", "inspect");
        }
        return Collections.emptyList();
    }
}
