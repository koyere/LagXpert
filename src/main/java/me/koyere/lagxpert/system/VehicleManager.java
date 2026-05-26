package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.SchedulerWrapper;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages vehicle limits and optimizations.
 * Handles minecarts and boats to prevent excessive server load.
 *
 * Fixed in Phase 2: runCleanupTask() now actually removes abandoned vehicles.
 * Added vehicle interaction tracking to distinguish abandoned vs in-use vehicles.
 * Logs all removals and blocked spawns to ActionLogger.
 */
public class VehicleManager implements Listener {

    private boolean enabled;
    private int maxMinecartsPerChunk;
    private int maxBoatsPerChunk;
    private boolean removeAbandonedVehicles;
    private long abandonedTimeoutMs;
    private int maxRemovalsPerCycle;
    private long cleanupInitialDelayTicks;
    private long cleanupIntervalTicks;
    private double playerNearbyRadius;
    private Set<String> disabledWorlds;

    // Track last interaction time for each vehicle
    private final Map<UUID, Long> vehicleLastInteraction = new ConcurrentHashMap<>();

    public VehicleManager() {
        reloadConfig();
        startTasks();
    }

    public void reloadConfig() {
        File file = new File(LagXpert.getInstance().getDataFolder(), "vehicles.yml");
        if (!file.exists()) {
            this.enabled = false;
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        this.enabled = config.getBoolean("enabled", true);
        this.maxMinecartsPerChunk = config.getInt("limits.minecarts.per-chunk", 8);
        this.maxBoatsPerChunk = config.getInt("limits.boats.per-chunk", 5);
        this.removeAbandonedVehicles = config.getBoolean("cleanup.remove-abandoned-vehicles", true);
        this.abandonedTimeoutMs = config.getLong("cleanup.abandoned-timeout-seconds", 300) * 1000L;
        this.maxRemovalsPerCycle = config.getInt("cleanup.max-removals-per-cycle", 50);
        this.cleanupInitialDelayTicks = config.getLong("cleanup.initial-delay-ticks", 600);
        this.cleanupIntervalTicks = config.getLong("cleanup.interval-ticks", 6000);
        this.playerNearbyRadius = config.getDouble("cleanup.player-nearby-radius", 16.0);

        this.disabledWorlds = new HashSet<>();
        List<String> worlds = config.getStringList("disabled-worlds");
        for (String w : worlds) {
            this.disabledWorlds.add(w.toLowerCase());
        }
    }

    private void startTasks() {
        // Periodic cleanup task for abandoned vehicles
        SchedulerWrapper.runTaskTimer(this::runCleanupTask,
                cleanupInitialDelayTicks, cleanupIntervalTicks);
    }

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
                vehicleLastInteraction.put(vehicle.getUniqueId(), System.currentTimeMillis());

                ActionLogger.getInstance().log(
                        ActionLogger.ActionType.VEHICLE_BLOCKED,
                        chunk.getWorld().getName(),
                        chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ(),
                        "Minecart limit reached (" + count + "/" + maxMinecartsPerChunk + ")",
                        1, "auto", true, 0);
            }
        } else if (vehicle instanceof Boat) {
            long count = Arrays.stream(chunk.getEntities())
                    .filter(e -> e instanceof Boat)
                    .count();
            if (count >= maxBoatsPerChunk) {
                event.setCancelled(true);
                vehicleLastInteraction.put(vehicle.getUniqueId(), System.currentTimeMillis());

                ActionLogger.getInstance().log(
                        ActionLogger.ActionType.VEHICLE_BLOCKED,
                        chunk.getWorld().getName(),
                        chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ(),
                        "Boat limit reached (" + count + "/" + maxBoatsPerChunk + ")",
                        1, "auto", true, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!enabled) return;
        vehicleLastInteraction.put(event.getVehicle().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!enabled) return;
        // Update timestamp when player exits — starts the abandonment timer
        vehicleLastInteraction.put(event.getVehicle().getUniqueId(), System.currentTimeMillis());
    }

    private void runCleanupTask() {
        if (!enabled || !removeAbandonedVehicles) return;

        long now = System.currentTimeMillis();
        int removed = 0;

        for (World world : Bukkit.getWorlds()) {
            if (isDisabledWorld(world)) continue;

            for (Chunk chunk : world.getLoadedChunks()) {
                if (removed >= maxRemovalsPerCycle) break;

                for (Entity entity : chunk.getEntities()) {
                    if (removed >= maxRemovalsPerCycle) break;
                    if (!(entity instanceof Vehicle)) continue;

                    Vehicle vehicle = (Vehicle) entity;

                    // Skip vehicles with passengers (actively being used)
                    if (!vehicle.getPassengers().isEmpty()) continue;

                    // Check abandonment: no interaction for timeout period
                    Long lastInteraction = vehicleLastInteraction.get(vehicle.getUniqueId());
                    long inactiveTime;
                    if (lastInteraction != null) {
                        inactiveTime = now - lastInteraction;
                    } else {
                        // Never interacted with since tracking started — use entity ticks lived
                        inactiveTime = vehicle.getTicksLived() * 50L; // ticks to ms
                    }

                    if (inactiveTime < abandonedTimeoutMs) continue;

                    // Safety: don't remove storage minecarts with items near players
                    if (vehicle instanceof org.bukkit.entity.minecart.StorageMinecart) {
                        org.bukkit.entity.minecart.StorageMinecart storageCart =
                                (org.bukkit.entity.minecart.StorageMinecart) vehicle;
                        if (!storageCart.getInventory().isEmpty()) {
                            double radiusSq = playerNearbyRadius * playerNearbyRadius;
                            boolean playerNearby = false;
                            for (Player p : world.getPlayers()) {
                                if (p.getLocation().distanceSquared(vehicle.getLocation()) < radiusSq) {
                                    playerNearby = true;
                                    break;
                                }
                            }
                            if (playerNearby) continue;
                        }
                    }

                    // Actually remove the vehicle
                    try {
                        String detail = vehicle.getType().name() +
                                " at " + vehicle.getLocation().getBlockX() + "," +
                                vehicle.getLocation().getBlockZ();
                        vehicle.remove();
                        removed++;

                        ActionLogger.getInstance().log(
                                ActionLogger.ActionType.VEHICLE_REMOVED,
                                world.getName(),
                                world.getName() + "_" + chunk.getX() + "_" + chunk.getZ(),
                                detail,
                                1, "auto", true, 0);

                        vehicleLastInteraction.remove(vehicle.getUniqueId());
                    } catch (Exception e) {
                        if (LagXpert.getInstance() != null) {
                            LagXpert.getInstance().getLogger().warning(
                                    "[VehicleManager] Failed to remove vehicle: " + e.getMessage());
                        }
                    }
                }
            }
        }

        if (removed > 0 && LagXpert.getInstance() != null) {
            LagXpert.getInstance().getLogger().info(
                    "[VehicleManager] Cleanup complete: removed " + removed + " abandoned vehicles.");
        }
    }

    private boolean isDisabledWorld(World world) {
        return disabledWorlds.contains(world.getName().toLowerCase());
    }
}
