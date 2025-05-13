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
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Scheduled task that scans loaded chunks for over-limit elements.
 * Also sends near-limit warnings (80%) to nearby players.
 */
public class AutoChunkScanTask extends BukkitRunnable {

    @Override
    public void run() {
        if (!ConfigManager.areAlertsEnabled()) return;

        for (World world : Bukkit.getWorlds()) {
            List<Player> players = world.getPlayers();
            if (players.isEmpty()) continue;

            for (Chunk chunk : world.getLoadedChunks()) {
                boolean playerNear = players.stream().anyMatch(p ->
                        p.getLocation().distanceSquared(chunk.getBlock(8, 64, 8).getLocation()) <= (48 * 48));
                if (!playerNear) continue;

                int mobs = countLivingEntities(chunk);
                int hoppers = countBlocks(chunk, Material.HOPPER);
                int chests = countBlocks(chunk, Material.CHEST);
                int furnaces = countBlocks(chunk, Material.FURNACE);
                int blast = countBlocks(chunk, Material.BLAST_FURNACE);
                int shulkers = countBlocks(chunk, Material.SHULKER_BOX);
                int droppers = countBlocks(chunk, Material.DROPPER);
                int dispensers = countBlocks(chunk, Material.DISPENSER);
                int observers = countBlocks(chunk, Material.OBSERVER);
                int barrels = countBlocks(chunk, Material.BARREL);
                int pistons = countBlocks(chunk, Material.PISTON);
                int tnt = countBlocks(chunk, Material.TNT);

                // Fire overload events
                if (mobs > ConfigManager.getMaxMobsPerChunk()) fireChunkOverloadEvent(chunk, "mobs");
                if (hoppers > ConfigManager.getMaxHoppersPerChunk()) fireChunkOverloadEvent(chunk, "hoppers");
                if (chests > ConfigManager.getMaxChestsPerChunk()) fireChunkOverloadEvent(chunk, "chests");
                if (furnaces > ConfigManager.getMaxFurnacesPerChunk()) fireChunkOverloadEvent(chunk, "furnaces");
                if (blast > ConfigManager.getMaxBlastFurnacesPerChunk()) fireChunkOverloadEvent(chunk, "blast_furnaces");
                if (shulkers > ConfigManager.getMaxShulkerBoxesPerChunk()) fireChunkOverloadEvent(chunk, "shulker_boxes");
                if (droppers > ConfigManager.getMaxDroppersPerChunk()) fireChunkOverloadEvent(chunk, "droppers");
                if (dispensers > ConfigManager.getMaxDispensersPerChunk()) fireChunkOverloadEvent(chunk, "dispensers");
                if (observers > ConfigManager.getMaxObserversPerChunk()) fireChunkOverloadEvent(chunk, "observers");
                if (barrels > ConfigManager.getMaxBarrelsPerChunk()) fireChunkOverloadEvent(chunk, "barrels");
                if (pistons > ConfigManager.getMaxPistonsPerChunk()) fireChunkOverloadEvent(chunk, "pistons");
                if (tnt > ConfigManager.getMaxTntPerChunk()) fireChunkOverloadEvent(chunk, "tnt");

                // 🔔 Near-limit warnings (80%)
                for (Player player : players) {
                    if (!player.getLocation().getChunk().equals(chunk)) continue;

                    checkNearLimit(player, "mobs", mobs, ConfigManager.getMaxMobsPerChunk());
                    checkNearLimit(player, "hoppers", hoppers, ConfigManager.getMaxHoppersPerChunk());
                    checkNearLimit(player, "chests", chests, ConfigManager.getMaxChestsPerChunk());
                    checkNearLimit(player, "furnaces", furnaces, ConfigManager.getMaxFurnacesPerChunk());
                    checkNearLimit(player, "blast furnaces", blast, ConfigManager.getMaxBlastFurnacesPerChunk());
                    checkNearLimit(player, "shulker boxes", shulkers, ConfigManager.getMaxShulkerBoxesPerChunk());
                    checkNearLimit(player, "droppers", droppers, ConfigManager.getMaxDroppersPerChunk());
                    checkNearLimit(player, "dispensers", dispensers, ConfigManager.getMaxDispensersPerChunk());
                    checkNearLimit(player, "observers", observers, ConfigManager.getMaxObserversPerChunk());
                    checkNearLimit(player, "barrels", barrels, ConfigManager.getMaxBarrelsPerChunk());
                    checkNearLimit(player, "pistons", pistons, ConfigManager.getMaxPistonsPerChunk());
                    checkNearLimit(player, "TNT", tnt, ConfigManager.getMaxTntPerChunk());
                }

                boolean alertNeeded = mobs > ConfigManager.getMaxMobsPerChunk()
                        || hoppers > ConfigManager.getMaxHoppersPerChunk()
                        || chests > ConfigManager.getMaxChestsPerChunk()
                        || furnaces > ConfigManager.getMaxFurnacesPerChunk()
                        || blast > ConfigManager.getMaxBlastFurnacesPerChunk()
                        || shulkers > ConfigManager.getMaxShulkerBoxesPerChunk()
                        || droppers > ConfigManager.getMaxDroppersPerChunk()
                        || dispensers > ConfigManager.getMaxDispensersPerChunk()
                        || observers > ConfigManager.getMaxObserversPerChunk()
                        || barrels > ConfigManager.getMaxBarrelsPerChunk()
                        || pistons > ConfigManager.getMaxPistonsPerChunk()
                        || tnt > ConfigManager.getMaxTntPerChunk();

                if (alertNeeded) {
                    String msg = MessageManager.getPrefix() +
                            "&cLag warning in chunk [" + chunk.getX() + "," + chunk.getZ() + "]&7: " +
                            "&e" + mobs + " mobs&7, " +
                            "&e" + hoppers + " hoppers&7, " +
                            "&e" + chests + " chests&7, " +
                            "&e" + furnaces + " furnaces&7, " +
                            "&e" + blast + " blast furnaces&7, " +
                            "&e" + shulkers + " shulker boxes&7, " +
                            "&e" + droppers + " droppers&7, " +
                            "&e" + dispensers + " dispensers&7, " +
                            "&e" + observers + " observers&7, " +
                            "&e" + barrels + " barrels&7, " +
                            "&e" + pistons + " pistons&7, " +
                            "&e" + tnt + " TNT&7.";
                    broadcastToNearbyPlayers(chunk, msg);
                }
            }
        }
    }

    private void checkNearLimit(Player player, String type, int value, int max) {
        if (value >= (max * 0.8) && value < max) {
            String warn = MessageManager.get("limits.near-limit")
                    .replace("{used}", String.valueOf(value))
                    .replace("{max}", String.valueOf(max));
            player.sendMessage(MessageManager.getPrefix() + warn);
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
    }
}
