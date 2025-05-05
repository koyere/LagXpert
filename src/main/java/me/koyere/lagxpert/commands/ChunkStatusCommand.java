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

/**
 * Handles the /chunkstatus command for players to inspect their current chunk.
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
        int hoppers = countBlocks(chunk, Material.HOPPER);
        int chests = countBlocks(chunk, Material.CHEST);
        int furnaces = countBlocks(chunk, Material.FURNACE);

        // Send message to player
        player.sendMessage(MessageManager.getPrefix() +
                MessageManager.get("chunkload.info")
                        .replace("{entities}", String.valueOf(mobs))
                        .replace("{hoppers}", String.valueOf(hoppers))
                        .replace("{chests}", String.valueOf(chests))
                        .replace("{furnaces}", String.valueOf(furnaces))
        );

        return true;
    }

    /**
     * Counts living entities in the chunk.
     */
    private int countMobs(Chunk chunk) {
        int count = 0;
        for (Entity e : chunk.getEntities()) {
            if (e instanceof LivingEntity) count++;
        }
        return count;
    }

    /**
     * Counts blocks of a specific type in the chunk.
     */
    private int countBlocks(Chunk chunk, Material material) {
        int count = 0;
        for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() == material) count++;
                }
            }
        }
        return count;
    }
}
