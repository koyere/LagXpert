package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.SchedulerWrapper;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleCreateEvent;
// import org.bukkit.event.vehicle.VehicleMoveEvent;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages vehicle limits and optimizations.
 * Handles minecarts and boats to prevent excessive server load.
 */
public class VehicleManager implements Listener {

    private boolean enabled;
    private int maxMinecartsPerChunk;
    private int maxBoatsPerChunk;
    private boolean removeAbandonedLootCarts;
    // private boolean disablePhysicsForEmpty; // Unused for now
    private Set<String> disabledWorlds;

    public VehicleManager() {
        reloadConfig();
        startTasks();
    }

    public void reloadConfig() {
        File file = new File(LagXpert.getInstance().getDataFolder(), "vehicles.yml");
        if (!file.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        this.enabled = config.getBoolean("enabled", true);
        this.maxMinecartsPerChunk = config.getInt("limits.minecarts.per-chunk", 8);
        this.maxBoatsPerChunk = config.getInt("limits.boats.per-chunk", 5);
        this.removeAbandonedLootCarts = config.getBoolean("cleanup.remove-abandoned-loot-carts", true);
        // this.disablePhysicsForEmpty =
        // config.getBoolean("optimization.disable-physics-for-empty", true);

        this.disabledWorlds = new HashSet<>();
        List<String> worlds = config.getStringList("disabled-worlds");
        for (String w : worlds) {
            this.disabledWorlds.add(w.toLowerCase());
        }
    }

    private void startTasks() {
        // Periodic cleanup task for abandoned vehicles
        // Runs every 5 minutes (6000 ticks)
        SchedulerWrapper.runTaskTimer(this::runCleanupTask, 600L, 6000L);
    }

    // Optimization: Cancel move events for empty vehicles if configured?
    // Actually, completely cancelling move might look glitchy, but we can stop
    // collision processing logic here if API allowed.
    // For now, we enforce limits on spawn.

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleSpawn(VehicleCreateEvent event) {
        if (!enabled || isDisabledWorld(event.getVehicle().getWorld()))
            return;

        Vehicle vehicle = event.getVehicle();
        Chunk chunk = vehicle.getLocation().getChunk();

        if (vehicle instanceof Minecart) {
            long count = Arrays.stream(chunk.getEntities())
                    .filter(e -> e instanceof Minecart)
                    .count();
            if (count >= maxMinecartsPerChunk) {
                event.setCancelled(true);
                // Maybe notify admins?
            }
        } else if (vehicle instanceof Boat) {
            long count = Arrays.stream(chunk.getEntities())
                    .filter(e -> e instanceof Boat)
                    .count();
            if (count >= maxBoatsPerChunk) {
                event.setCancelled(true);
            }
        }
    }

    private void runCleanupTask() {
        if (!enabled || !removeAbandonedLootCarts)
            return;

        // This is a heavy task, so we should be careful.
        // In a real optimized system, we'd only scan active chunks or use a queue.
        // For now, iterating worlds/loaded chunks.

        for (World world : Bukkit.getWorlds()) {
            if (isDisabledWorld(world))
                continue;

            for (Chunk chunk : world.getLoadedChunks()) {
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof Minecart) {
                        Minecart cart = (Minecart) entity;
                        // Check if it's abandoned chest minecart
                        if (removeAbandonedLootCarts && cart instanceof org.bukkit.entity.minecart.StorageMinecart) {
                            if (cart.getPassengers().isEmpty() && cart.getVelocity().length() < 0.01) {
                                // Logic to distinguish "abandoned" vs "using":
                                // For now, if empty and stopped, we might want to flag/remove.
                                // Simplification for this implementation step.
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isDisabledWorld(World world) {
        return disabledWorlds.contains(world.getName().toLowerCase());
    }
}
