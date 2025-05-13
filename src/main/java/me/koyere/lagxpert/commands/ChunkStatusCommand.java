package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles the /chunkstatus command for players to inspect their current chunk.
 * Shows usage of mobs and storage-related blocks in a visually clean format.
 */
public class ChunkStatusCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageManager.getPrefix() + "&cOnly players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        Chunk chunk = player.getLocation().getChunk();

        int mobs = countMobs(chunk);

        Map<Material, Integer> blockCounts = new LinkedHashMap<>();
        blockCounts.put(Material.HOPPER, countBlocks(chunk, Material.HOPPER));
        blockCounts.put(Material.CHEST, countBlocks(chunk, Material.CHEST));
        blockCounts.put(Material.FURNACE, countBlocks(chunk, Material.FURNACE));
        blockCounts.put(Material.BLAST_FURNACE, countBlocks(chunk, Material.BLAST_FURNACE));
        blockCounts.put(Material.SHULKER_BOX, countBlocks(chunk, Material.SHULKER_BOX));
        blockCounts.put(Material.DROPPER, countBlocks(chunk, Material.DROPPER));
        blockCounts.put(Material.DISPENSER, countBlocks(chunk, Material.DISPENSER));
        blockCounts.put(Material.OBSERVER, countBlocks(chunk, Material.OBSERVER));
        blockCounts.put(Material.BARREL, countBlocks(chunk, Material.BARREL));
        blockCounts.put(Material.PISTON, countBlocks(chunk, Material.PISTON));
        blockCounts.put(Material.TNT, countBlocks(chunk, Material.TNT));

        // Build a clean, formatted message
        StringBuilder msg = new StringBuilder();
        msg.append("\n&8&m--------------------------------------------------\n");
        msg.append("&b&lChunk Status &8| &7You are standing in chunk &e[").append(chunk.getX()).append(", ").append(chunk.getZ()).append("]\n");
        msg.append("&f• &aMobs: &e").append(mobs).append("\n");

        for (Map.Entry<Material, Integer> entry : blockCounts.entrySet()) {
            int count = entry.getValue();
            if (count > 0) {
                msg.append("&f• &a").append(getFriendlyName(entry.getKey())).append(": &e").append(count).append("\n");
            }
        }

        msg.append("&8&m--------------------------------------------------");

        // Send message to player
        player.sendMessage(MessageManager.color(msg.toString()));
        return true;
    }

    private int countMobs(Chunk chunk) {
        int count = 0;
        for (Entity e : chunk.getEntities()) {
            if (e instanceof LivingEntity) count++;
        }
        return count;
    }

    private int countBlocks(Chunk chunk, Material type) {
        int count = 0;
        for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() == type) count++;
                }
            }
        }
        return count;
    }

    private String getFriendlyName(Material material) {
        return switch (material) {
            case HOPPER -> "Hoppers";
            case CHEST -> "Chests";
            case FURNACE -> "Furnaces";
            case BLAST_FURNACE -> "Blast Furnaces";
            case SHULKER_BOX -> "Shulker Boxes";
            case DROPPER -> "Droppers";
            case DISPENSER -> "Dispensers";
            case OBSERVER -> "Observers";
            case BARREL -> "Barrels";
            case PISTON -> "Pistons";
            case TNT -> "TNT";
            default -> material.name();
        };
    }
}
