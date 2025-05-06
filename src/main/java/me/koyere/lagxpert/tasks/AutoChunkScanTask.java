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
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduled task that scans loaded chunks for over-limit elements.
 * Now optimized to skip chunks without nearby players to improve performance.
 */
public class AutoChunkScanTask extends BukkitRunnable {

    @Override
    public void run() {
        if (!ConfigManager.areAlertsEnabled()) return;

        for (World world : Bukkit.getWorlds()) {
            List<Player> players = world.getPlayers();
            if (players.isEmpty()) continue; // Skip world if no players online

            for (Chunk chunk : world.getLoadedChunks()) {
                // Skip chunks far from players
                boolean playerNear = players.stream()
                        .anyMatch(p -> p.getLocation().distanceSquared(chunk.getBlock(8, 64, 8).getLocation()) <= (48 * 48));
                if (!playerNear) continue;

                int mobCount = countLivingEntities(chunk);

                // Count blocks of interest
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

                int hopperMinecartCount = countHopperMinecarts(chunk);

                // Fire overload events
                if (mobCount > ConfigManager.getMaxMobsPerChunk()) {
                    fireChunkOverloadEvent(chunk, "mobs");
                }

                if (blockCounts.get(Material.HOPPER) > ConfigManager.getMaxHoppersPerChunk()) {
                    fireChunkOverloadEvent(chunk, "hoppers");
                }
                if (blockCounts.get(Material.CHEST) > ConfigManager.getMaxChestsPerChunk()) {
                    fireChunkOverloadEvent(chunk, "chests");
                }
                if (blockCounts.get(Material.FURNACE) > ConfigManager.getMaxFurnacesPerChunk()) {
                    fireChunkOverloadEvent(chunk, "furnaces");
                }

                // Prepare message for overloaded chunk
                boolean alertNeeded = mobCount > ConfigManager.getMaxMobsPerChunk()
                        || blockCounts.get(Material.HOPPER) > ConfigManager.getMaxHoppersPerChunk()
                        || blockCounts.get(Material.CHEST) > ConfigManager.getMaxChestsPerChunk()
                        || blockCounts.get(Material.FURNACE) > ConfigManager.getMaxFurnacesPerChunk();

                if (alertNeeded) {
                    StringBuilder msg = new StringBuilder(MessageManager.getPrefix())
                            .append("&cLag warning in chunk [&f")
                            .append(chunk.getX()).append("&7, &f").append(chunk.getZ()).append("&c]&7: ")
                            .append("&e").append(mobCount).append(" mobs&7");

                    blockCounts.forEach((mat, count) -> {
                        if (count > 0) {
                            msg.append(", &e").append(count).append(" ").append(getFriendlyName(mat)).append("&7");
                        }
                    });

                    if (hopperMinecartCount > 0) {
                        msg.append(", &e").append(hopperMinecartCount).append(" Hopper Minecarts&7");
                    }

                    broadcastToNearbyPlayers(chunk, msg.toString());
                }
            }
        }
    }

    private int countLivingEntities(Chunk chunk) {
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity) count++;
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

    private int countHopperMinecarts(Chunk chunk) {
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof org.bukkit.entity.minecart.HopperMinecart) {
                count++;
            }
        }
        return count;
    }

    private void broadcastToNearbyPlayers(Chunk chunk, String msg) {
        chunk.getWorld().getPlayers().forEach(player -> {
            if (player.getLocation().getChunk().equals(chunk)) {
                player.sendMessage(msg);
            }
        });
    }

    private void fireChunkOverloadEvent(Chunk chunk, String cause) {
        ChunkOverloadEvent event = new ChunkOverloadEvent(chunk, cause);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
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
            case TNT -> "TNT Blocks";
            default -> material.name();
        };
    }
}
