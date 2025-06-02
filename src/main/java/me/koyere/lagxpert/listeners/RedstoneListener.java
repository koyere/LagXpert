package me.koyere.lagxpert.listeners;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.api.events.ChunkOverloadEvent;
import me.koyere.lagxpert.system.AlertCooldownManager;
import me.koyere.lagxpert.system.RedstoneCircuitTracker;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.RedstoneWire;
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
 * Enhanced redstone listener that controls redstone activity by monitoring signals
 * and integrating with the advanced RedstoneCircuitTracker system.
 * Provides intelligent circuit detection, frequency analysis, and graduated shutdown procedures.
 */
public class RedstoneListener implements Listener {

    private static final int BYPASS_RADIUS = 16; // Radius in blocks to check for bypass players

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstoneChange(BlockRedstoneEvent event) {
        if (!ConfigManager.isRedstoneControlModuleEnabled()) {
            return;
        }

        Block block = event.getBlock();
        Material material = block.getType();
        Location location = block.getLocation();

        // Record all redstone activity for circuit tracking
        if (isRedstoneComponent(material)) {
            RedstoneCircuitTracker.recordRedstoneActivity(location, material);
        }

        // Handle redstone wire specifically for the legacy timeout system
        if (material == Material.REDSTONE_WIRE && event.getNewCurrent() > 0 && event.getOldCurrent() == 0) {
            scheduleRedstoneCheck(block);
        }
    }

    /**
     * Checks if a material is a redstone component that should be tracked.
     */
    private boolean isRedstoneComponent(Material material) {
        switch (material) {
            case REDSTONE_WIRE:
            case REPEATER:
            case COMPARATOR:
            case REDSTONE_TORCH:
            case REDSTONE_WALL_TORCH:
            case REDSTONE_BLOCK:
            case OBSERVER:
            case PISTON:
            case STICKY_PISTON:
                return true;
            default:
                return false;
        }
    }

    /**
     * Legacy redstone wire timeout system - still used as a fallback.
     * Now enhanced with player notifications and integration with circuit tracker.
     */
    private void scheduleRedstoneCheck(Block block) {
        long delayTicks = ConfigManager.getRedstoneActiveTicks();
        if (delayTicks <= 0) {
            return;
        }

        Location blockLocation = block.getLocation().clone();

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

            // Check for bypass permissions
            List<Player> nearbyPlayers = getNearbyPlayers(blockLocation, BYPASS_RADIUS);
            boolean isBypassed = false;
            for (Player player : nearbyPlayers) {
                if (player.hasPermission("lagxpert.bypass.redstone")) {
                    isBypassed = true;
                    break;
                }
            }

            if (isBypassed) {
                return;
            }

            // Enhanced warning system - notify players before cutting
            if (!nearbyPlayers.isEmpty()) {
                notifyPlayersBeforeShutdown(nearbyPlayers, blockLocation, "timeout");

                // Give players a few seconds to react before cutting
                Bukkit.getScheduler().runTaskLater(LagXpert.getInstance(), () -> {
                    performRedstoneShutdown(blockLocation, "redstone_timeout");
                }, 60L); // 3 second warning
            } else {
                // No players nearby, cut immediately
                performRedstoneShutdown(blockLocation, "redstone_timeout");
            }

        }, delayTicks);
    }

    /**
     * Gets nearby players within a specified radius.
     */
    private List<Player> getNearbyPlayers(Location location, int radius) {
        List<Player> nearbyPlayers = new ArrayList<>();
        Collection<Entity> nearbyEntities = location.getWorld().getNearbyEntities(location, radius, radius, radius);

        for (Entity entity : nearbyEntities) {
            if (entity instanceof Player) {
                nearbyPlayers.add((Player) entity);
            }
        }
        return nearbyPlayers;
    }

    /**
     * Notifies players about an impending redstone shutdown.
     */
    private void notifyPlayersBeforeShutdown(List<Player> players, Location location, String reason) {
        if (!ConfigManager.isAlertsModuleEnabled() || !ConfigManager.shouldAlertOnRedstoneActivity()) {
            return;
        }

        String locationStr = locationToString(location);
        String alertContext = locationStr + "_" + reason;
        String baseAlertKey = "redstone_warning";

        for (Player player : players) {
            String uniquePlayerAlertKey = AlertCooldownManager.generateAlertKey(player, baseAlertKey, alertContext);
            if (AlertCooldownManager.canSendAlert(player, uniquePlayerAlertKey)) {
                // Send warning message
                player.sendMessage(MessageManager.getPrefixedMessage("limits.redstone"));

                // Play warning sound
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.8f);

                if (ConfigManager.isDebugEnabled()) {
                    player.sendMessage(MessageManager.color("&e[Debug] Redstone will be cut in 3 seconds due to: " + reason));
                }
            }
        }
    }

    /**
     * Performs the actual redstone shutdown with enhanced logging and effects.
     */
    private void performRedstoneShutdown(Location location, String cause) {
        World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }

        Block currentBlockState = location.getBlock();
        if (currentBlockState.getType() != Material.REDSTONE_WIRE) {
            return; // Already been removed or changed
        }

        // Verify the wire is still powered
        org.bukkit.block.data.BlockData blockData = currentBlockState.getBlockData();
        if (blockData instanceof RedstoneWire) {
            RedstoneWire wireData = (RedstoneWire) blockData;
            if (wireData.getPower() == 0) {
                return; // Wire is no longer powered
            }
        }

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info("[LagXpert] Cutting redstone wire at: " +
                    locationToString(location) + " due to: " + cause);
        }

        // Cut the redstone wire
        currentBlockState.setType(Material.AIR, true);

        // Enhanced effects
        world.playSound(location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);

        // Optional: Create particle effect if players are nearby
        List<Player> nearbyPlayers = getNearbyPlayers(location, BYPASS_RADIUS);
        if (!nearbyPlayers.isEmpty()) {
            // Simple redstone particle effect using existing Bukkit methods
            world.playSound(location, Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.8f, 1.0f);
        }

        // Fire chunk overload event
        fireChunkOverloadEvent(currentBlockState.getChunk(), cause);

        // Alert players in the chunk (with cooldown)
        alertPlayersInChunk(currentBlockState.getChunk(), location, cause);
    }

    /**
     * Alerts players in the chunk about the redstone shutdown.
     */
    private void alertPlayersInChunk(Chunk chunk, Location shutdownLocation, String cause) {
        if (!ConfigManager.isAlertsModuleEnabled() || !ConfigManager.shouldAlertOnRedstoneActivity()) {
            return;
        }

        String message = MessageManager.getPrefixedMessage("limits.redstone");
        List<Player> playersInChunk = new ArrayList<>();

        for (Entity entityInChunk : chunk.getEntities()) {
            if (entityInChunk instanceof Player) {
                playersInChunk.add((Player) entityInChunk);
            }
        }

        if (!playersInChunk.isEmpty()) {
            String alertContext = locationToString(shutdownLocation);
            String alertKeyBase = "redstone_cut";

            for (Player playerInChunk : playersInChunk) {
                String uniquePlayerAlertKey = AlertCooldownManager.generateAlertKey(playerInChunk, alertKeyBase, alertContext);
                if (AlertCooldownManager.canSendAlert(playerInChunk, uniquePlayerAlertKey)) {
                    playerInChunk.sendMessage(message);
                }
            }
        }
    }

    private void fireChunkOverloadEvent(Chunk chunk, String cause) {
        ChunkOverloadEvent event = new ChunkOverloadEvent(chunk, cause);
        Bukkit.getPluginManager().callEvent(event);
    }

    private String locationToString(Location loc) {
        if (loc == null) return "null_location";
        String worldName = (loc.getWorld() != null) ? loc.getWorld().getName() : "unknown_world";
        return worldName + "_X" + loc.getBlockX() + "_Y" + loc.getBlockY() + "_Z" + loc.getBlockZ();
    }
}