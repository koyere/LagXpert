package me.koyere.lagxpert.tasks;

import me.koyere.lagxpert.api.events.ChunkOverloadEvent;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Scheduled task that scans all loaded chunks and alerts players
 * if any chunk exceeds mob, hopper, or storage limits.
 */
public class AutoChunkScanTask extends BukkitRunnable {

    @Override
    public void run() {
        if (!ConfigManager.areAlertsEnabled()) return;

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                int mobCount = countLivingEntities(chunk);
                int hopperCount = countBlocks(chunk, Material.HOPPER);
                int chestCount = countBlocks(chunk, Material.CHEST);
                int furnaceCount = countBlocks(chunk, Material.FURNACE);

                if (mobCount > ConfigManager.getMaxMobsPerChunk()) {
                    fireChunkOverloadEvent(chunk, "mobs");
                }
                if (hopperCount > ConfigManager.getMaxHoppersPerChunk()) {
                    fireChunkOverloadEvent(chunk, "hoppers");
                }
                if (chestCount > ConfigManager.getMaxChestsPerChunk()) {
                    fireChunkOverloadEvent(chunk, "chests");
                }
                if (furnaceCount > ConfigManager.getMaxFurnacesPerChunk()) {
                    fireChunkOverloadEvent(chunk, "furnaces");
                }

                boolean alertNeeded = mobCount > ConfigManager.getMaxMobsPerChunk()
                        || hopperCount > ConfigManager.getMaxHoppersPerChunk()
                        || chestCount > ConfigManager.getMaxChestsPerChunk()
                        || furnaceCount > ConfigManager.getMaxFurnacesPerChunk();

                if (alertNeeded) {
                    alertNearbyPlayers(chunk, mobCount, hopperCount, chestCount, furnaceCount);
                }
            }
        }
    }

    private int countLivingEntities(Chunk chunk) {
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

    private void alertNearbyPlayers(Chunk chunk, int mobs, int hoppers, int chests, int furnaces) {
        String msg = MessageManager.getPrefix() +
                "&cLag warning in chunk [" + chunk.getX() + "," + chunk.getZ() + "]&7: " +
                "&e" + mobs + " mobs&7, " +
                "&e" + hoppers + " hoppers&7, " +
                "&e" + chests + " chests&7, " +
                "&e" + furnaces + " furnaces&7.";

        chunk.getWorld().getPlayers().forEach(player -> {
            if (player.getLocation().getChunk().equals(chunk)) {
                player.sendMessage(msg);
            }
        });
    }

    /**
     * Fires the public ChunkOverloadEvent for API consumers.
     * Cancels handling if another plugin cancels it.
     */
    private void fireChunkOverloadEvent(Chunk chunk, String cause) {
        ChunkOverloadEvent event = new ChunkOverloadEvent(chunk, cause);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return; // External plugin cancelled the event
        }
        // Internal handling can be added here later
    }
}
