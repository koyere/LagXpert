package me.koyere.lagxpert.listeners;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.api.events.ChunkOverloadEvent;
import me.koyere.lagxpert.system.AlertCooldownManager; // IMPORT ADDED for Alert Cooldown
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.RedstoneWire; // Specific import for RedstoneWire BlockData
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Controls redstone activity by monitoring redstone wire signals that remain active for too long.
 * If a redstone wire signal persists beyond a configured duration and no player with bypass
 * permission is nearby, the wire is cut. Alerts are sent based on configuration and cooldowns.
 */
public class RedstoneListener implements Listener {

    private static final int BYPASS_RADIUS = 16; // Radius in blocks to check for bypass players

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstoneChange(BlockRedstoneEvent event) {
        if (!ConfigManager.isRedstoneControlModuleEnabled()) {
            return;
        }

        Block block = event.getBlock();

        // We are interested when a redstone wire specifically transitions from off to on.
        if (block.getType() == Material.REDSTONE_WIRE && event.getNewCurrent() > 0 && event.getOldCurrent() == 0) {
            scheduleRedstoneCheck(block);
        }
    }

    private void scheduleRedstoneCheck(Block block) {
        long delayTicks = ConfigManager.getRedstoneActiveTicks();
        if (delayTicks <= 0) {
            return;
        }

        Location blockLocation = block.getLocation().clone(); // Clone for safety in scheduled task

        Bukkit.getScheduler().runTaskLater(LagXpert.getInstance(), () -> {
            World world = blockLocation.getWorld();
            if (world == null || !world.isChunkLoaded(blockLocation.getBlockX() >> 4, blockLocation.getBlockZ() >> 4)) {
                return;
            }

            Block currentBlockState = blockLocation.getBlock();

            if (currentBlockState.getType() != Material.REDSTONE_WIRE) {
                return;
            }

            org.bukkit.block.data.BlockData blockData = currentBlockState.getBlockData();
            if (!(blockData instanceof RedstoneWire)) {
                return;
            }
            RedstoneWire wireData = (RedstoneWire) blockData;

            if (wireData.getPower() == 0) {
                return;
            }

            boolean isBypassed = false;
            Collection<Entity> nearbyEntities = currentBlockState.getWorld().getNearbyEntities(blockLocation, BYPASS_RADIUS, BYPASS_RADIUS, BYPASS_RADIUS);
            for (Entity entity : nearbyEntities) {
                if (entity instanceof Player) {
                    Player player = (Player) entity;
                    if (player.hasPermission("lagxpert.bypass.redstone")) {
                        isBypassed = true;
                        break;
                    }
                }
            }

            if (isBypassed) {
                return;
            }

            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info("[LagXpert] Cutting redstone wire at: " +
                        blockLocationToString(blockLocation) + " due to persistent signal.");
            }

            currentBlockState.setType(Material.AIR, true);
            currentBlockState.getWorld().playSound(blockLocation, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);

            fireChunkOverloadEvent(currentBlockState.getChunk(), "redstone_timeout");

            // Alert Players in the Chunk (Conditional and with Cooldown)
            if (ConfigManager.isAlertsModuleEnabled() && ConfigManager.shouldAlertOnRedstoneActivity()) {
                String message = MessageManager.getPrefixedMessage("limits.redstone");
                List<Player> playersInChunk = new ArrayList<>();
                for (Entity entityInChunk : currentBlockState.getChunk().getEntities()) {
                    if (entityInChunk instanceof Player) {
                        playersInChunk.add((Player) entityInChunk);
                    }
                }
                if (!playersInChunk.isEmpty()) {
                    // Use the block's location as part of the unique context for the alert cooldown
                    String alertContext = blockLocationToString(blockLocation);
                    String alertKeyBase = "redstone_cut"; // Base key for this type of alert

                    for (Player playerInChunk : playersInChunk) {
                        // Generate a player-specific alert key for this event instance using the context
                        String uniquePlayerAlertKey = AlertCooldownManager.generateAlertKey(playerInChunk, alertKeyBase, alertContext);
                        if (AlertCooldownManager.canSendAlert(playerInChunk, uniquePlayerAlertKey)) {
                            playerInChunk.sendMessage(message);
                        }
                    }
                }
            }
        }, delayTicks);
    }

    private void fireChunkOverloadEvent(Chunk chunk, String cause) {
        ChunkOverloadEvent event = new ChunkOverloadEvent(chunk, cause);
        Bukkit.getPluginManager().callEvent(event);
    }

    private String blockLocationToString(Location loc) {
        if (loc == null) return "null_location";
        // Using block coordinates for simpler log messages and unique alert keys.
        return loc.getWorld().getName() + "_X" + loc.getBlockX() + "_Y" + loc.getBlockY() + "_Z" + loc.getBlockZ();
    }
}