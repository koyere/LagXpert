package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.utils.ChunkUtils;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles the /lagxpert inspect <x,z> command for admins.
 * Provides technical info about any chunk coordinates.
 */
public class InspectCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Permission check
        if (!sender.hasPermission("lagxpert.admin")) {
            sender.sendMessage(MessageManager.get("inspect.no-permission"));
            return true;
        }

        // Validate arguments
        if (args.length < 2 || !isNumeric(args[0]) || !isNumeric(args[1])) {
            sender.sendMessage(MessageManager.get("inspect.usage"));
            return true;
        }

        int chunkX = Integer.parseInt(args[0]);
        int chunkZ = Integer.parseInt(args[1]);

        // Try to find the chunk in any loaded world
        Chunk target = null;
        for (var world : Bukkit.getWorlds()) {
            Chunk chunk = world.getChunkAt(chunkX, chunkZ);
            if (chunk.isLoaded()) {
                target = chunk;
                break;
            }
        }

        if (target == null) {
            sender.sendMessage(MessageManager.getPrefix() + "&cChunk [" + chunkX + "," + chunkZ + "] is not loaded.");
            return true;
        }

        // Collect chunk data
        int entityCount = ChunkUtils.countEntitiesInChunk(target);
        int hopperCount = ChunkUtils.countBlocksInChunk(target, "HOPPER");
        int chestCount = ChunkUtils.countBlocksInChunk(target, "CHEST");
        int furnaceCount = ChunkUtils.countBlocksInChunk(target, "FURNACE");

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("entities", String.valueOf(entityCount));
        placeholders.put("hoppers", String.valueOf(hopperCount));
        placeholders.put("chests", String.valueOf(chestCount));
        placeholders.put("furnaces", String.valueOf(furnaceCount));
        placeholders.put("chunk", chunkX + "," + chunkZ);

        // Build response
        sender.sendMessage(MessageManager.getPrefix() + "&6Inspecting chunk &e[" + chunkX + "," + chunkZ + "]");
        sender.sendMessage(MessageManager.getPrefix() + "&7Entities: &e" + entityCount);
        sender.sendMessage(MessageManager.getPrefix() + "&7Hoppers: &e" + hopperCount);
        sender.sendMessage(MessageManager.getPrefix() + "&7Chests: &e" + chestCount);
        sender.sendMessage(MessageManager.getPrefix() + "&7Furnaces: &e" + furnaceCount);

        return true;
    }

    private boolean isNumeric(String input) {
        try {
            Integer.parseInt(input);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
