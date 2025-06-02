package me.koyere.lagxpert.tasks;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive entity cleanup task that removes various types of problematic entities
 * to improve server performance. Includes detection and removal of invalid entities,
 * duplicates, abandoned vehicles, and entities outside world borders.
 */
public class EntityCleanupTask extends BukkitRunnable {

    // Statistics tracking
    private static final AtomicInteger totalEntitiesRemoved = new AtomicInteger(0);
    private static final AtomicInteger invalidEntitiesRemoved = new AtomicInteger(0);
    private static final AtomicInteger duplicateEntitiesRemoved = new AtomicInteger(0);
    private static final AtomicInteger abandonedVehiclesRemoved = new AtomicInteger(0);
    private static final AtomicInteger emptyContainersRemoved = new AtomicInteger(0);
    private static final AtomicInteger outOfBoundsEntitiesRemoved = new AtomicInteger(0);

    // Configuration
    private static final double DUPLICATE_DETECTION_RADIUS = 1.0; // Radius to check for duplicate entities
    private static final long ABANDONED_VEHICLE_TIME_MS = 300000; // 5 minutes
    private static final int MAX_ENTITIES_PER_CHUNK = 200; // Maximum entities per chunk before cleanup

    @Override
    public void run() {
        if (!ConfigManager.isEntityCleanupEnabled()) {
            return;
        }

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info("[EntityCleanupTask] Starting entity cleanup cycle...");
        }

        long startTime = System.currentTimeMillis();
        int totalCleaned = 0;

        for (World world : Bukkit.getWorlds()) {
            if (!isWorldEnabled(world)) {
                continue;
            }

            totalCleaned += cleanupWorldEntities(world);
        }

        long duration = System.currentTimeMillis() - startTime;
        totalEntitiesRemoved.addAndGet(totalCleaned);

        if (ConfigManager.isDebugEnabled() || totalCleaned > 0) {
            LagXpert.getInstance().getLogger().info(
                    "[EntityCleanupTask] Cleanup completed in " + duration + "ms. " +
                            "Removed: " + totalCleaned + " entities"
            );
        }
    }

    /**
     * Performs comprehensive entity cleanup for a specific world.
     */
    private int cleanupWorldEntities(World world) {
        int removedCount = 0;
        List<Entity> allEntities = world.getEntities();

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info(
                    "[EntityCleanupTask] Scanning " + allEntities.size() + " entities in world: " + world.getName()
            );
        }

        // Group entities by location for duplicate detection
        Map<String, List<Entity>> entitiesByLocation = new HashMap<>();
        List<Entity> entitiesToRemove = new ArrayList<>();

        for (Entity entity : allEntities) {
            try {
                // Skip players and important entities
                if (shouldSkipEntity(entity)) {
                    continue;
                }

                // Check for invalid/corrupted entities
                if (isInvalidEntity(entity)) {
                    entitiesToRemove.add(entity);
                    invalidEntitiesRemoved.incrementAndGet();
                    continue;
                }

                // Check if entity is outside world border
                if (isOutsideWorldBorder(entity)) {
                    entitiesToRemove.add(entity);
                    outOfBoundsEntitiesRemoved.incrementAndGet();
                    continue;
                }

                // Check for abandoned vehicles
                if (isAbandonedVehicle(entity)) {
                    entitiesToRemove.add(entity);
                    abandonedVehiclesRemoved.incrementAndGet();
                    continue;
                }

                // Check for empty item frames and armor stands
                if (isEmptyContainer(entity)) {
                    entitiesToRemove.add(entity);
                    emptyContainersRemoved.incrementAndGet();
                    continue;
                }

                // Group for duplicate detection
                String locationKey = getLocationKey(entity.getLocation());
                entitiesByLocation.computeIfAbsent(locationKey, k -> new ArrayList<>()).add(entity);

            } catch (Exception e) {
                // Entity might be corrupted, remove it
                entitiesToRemove.add(entity);
                invalidEntitiesRemoved.incrementAndGet();

                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().warning(
                            "[EntityCleanupTask] Exception while processing entity: " + e.getMessage()
                    );
                }
            }
        }

        // Detect and mark duplicates
        for (List<Entity> locationGroup : entitiesByLocation.values()) {
            if (locationGroup.size() > 1) {
                removedCount += removeDuplicateEntities(locationGroup, entitiesToRemove);
            }
        }

        // Remove marked entities
        for (Entity entity : entitiesToRemove) {
            try {
                if (entity.isValid()) {
                    entity.remove();
                    removedCount++;
                }
            } catch (Exception e) {
                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().warning(
                            "[EntityCleanupTask] Failed to remove entity: " + e.getMessage()
                    );
                }
            }
        }

        return removedCount;
    }

    /**
     * Determines if an entity should be skipped during cleanup.
     */
    private boolean shouldSkipEntity(Entity entity) {
        // Never remove players
        if (entity instanceof Player) {
            return true;
        }

        // Skip entities with custom names (likely important)
        if (entity.getCustomName() != null && !entity.getCustomName().isEmpty()) {
            return true;
        }

        // Skip tamed animals
        if (entity instanceof Tameable && ((Tameable) entity).isTamed()) {
            return true;
        }

        // Skip leashed entities
        if (entity instanceof LivingEntity && ((LivingEntity) entity).isLeashed()) {
            return true;
        }

        // Skip entities in vehicles or with passengers
        if (entity.getVehicle() != null || !entity.getPassengers().isEmpty()) {
            return true;
        }

        // Skip persistent entities
        if (entity instanceof LivingEntity && !((LivingEntity) entity).getRemoveWhenFarAway()) {
            return true;
        }

        return false;
    }

    /**
     * Checks if an entity is invalid or corrupted.
     */
    private boolean isInvalidEntity(Entity entity) {
        try {
            // Basic validity checks
            if (!entity.isValid()) {
                return true;
            }

            // Check if entity has a valid location
            Location loc = entity.getLocation();
            if (loc == null || loc.getWorld() == null) {
                return true;
            }

            // Check for NaN or infinite coordinates
            if (Double.isNaN(loc.getX()) || Double.isNaN(loc.getY()) || Double.isNaN(loc.getZ()) ||
                    Double.isInfinite(loc.getX()) || Double.isInfinite(loc.getY()) || Double.isInfinite(loc.getZ())) {
                return true;
            }

            // Check if entity type is valid
            if (entity.getType() == null) {
                return true;
            }

            // Check for entities at invalid Y coordinates
            if (loc.getY() < -100 || loc.getY() > 1000) {
                return true;
            }

        } catch (Exception e) {
            // Any exception during checking means the entity is likely corrupted
            return true;
        }

        return false;
    }

    /**
     * Checks if an entity is outside the world border.
     */
    private boolean isOutsideWorldBorder(Entity entity) {
        try {
            World world = entity.getWorld();
            if (world == null) {
                return true;
            }

            org.bukkit.WorldBorder border = world.getWorldBorder();
            Location center = border.getCenter();
            double size = border.getSize() / 2.0;

            Location entityLoc = entity.getLocation();
            double distanceX = Math.abs(entityLoc.getX() - center.getX());
            double distanceZ = Math.abs(entityLoc.getZ() - center.getZ());

            return distanceX > size || distanceZ > size;

        } catch (Exception e) {
            return false; // If we can't check, assume it's fine
        }
    }

    /**
     * Checks if a vehicle has been abandoned for too long.
     */
    private boolean isAbandonedVehicle(Entity entity) {
        if (!ConfigManager.shouldCleanupAbandonedVehicles()) {
            return false;
        }

        if (!(entity instanceof Vehicle)) {
            return false;
        }

        // Don't remove vehicles with passengers
        if (!entity.getPassengers().isEmpty()) {
            return false;
        }

        // Check how long the vehicle has been without passengers
        // This is a simplified check - in a real implementation you might want to track this data
        if (entity instanceof Boat || entity instanceof Minecart) {
            // Check if there are players nearby who might use it
            List<Entity> nearbyEntities = entity.getNearbyEntities(50, 50, 50);
            boolean hasNearbyPlayers = nearbyEntities.stream().anyMatch(e -> e instanceof Player);

            if (!hasNearbyPlayers) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if an item frame or armor stand is empty and should be removed.
     */
    private boolean isEmptyContainer(Entity entity) {
        if (entity instanceof ItemFrame) {
            ItemFrame frame = (ItemFrame) entity;
            if (!ConfigManager.shouldCleanupEmptyItemFrames()) {
                return false;
            }
            ItemStack item = frame.getItem();
            return item == null || item.getType().isAir();
        }

        if (entity instanceof ArmorStand) {
            ArmorStand stand = (ArmorStand) entity;
            if (!ConfigManager.shouldCleanupEmptyArmorStands()) {
                return false;
            }

            // Check if armor stand has any equipment
            boolean hasEquipment = false;
            hasEquipment |= stand.getItemInHand() != null && !stand.getItemInHand().getType().isAir();
            hasEquipment |= stand.getHelmet() != null && !stand.getHelmet().getType().isAir();
            hasEquipment |= stand.getChestplate() != null && !stand.getChestplate().getType().isAir();
            hasEquipment |= stand.getLeggings() != null && !stand.getLeggings().getType().isAir();
            hasEquipment |= stand.getBoots() != null && !stand.getBoots().getType().isAir();

            return !hasEquipment;
        }

        return false;
    }

    /**
     * Removes duplicate entities from a group, keeping the most appropriate one.
     */
    private int removeDuplicateEntities(List<Entity> entities, List<Entity> entitiesToRemove) {
        if (entities.size() <= 1) {
            return 0;
        }

        // Group by entity type
        Map<EntityType, List<Entity>> byType = new HashMap<>();
        for (Entity entity : entities) {
            byType.computeIfAbsent(entity.getType(), k -> new ArrayList<>()).add(entity);
        }

        int markedForRemoval = 0;

        for (Map.Entry<EntityType, List<Entity>> entry : byType.entrySet()) {
            List<Entity> typeGroup = entry.getValue();
            if (typeGroup.size() > 1) {
                // Keep the first entity, remove the rest
                for (int i = 1; i < typeGroup.size(); i++) {
                    entitiesToRemove.add(typeGroup.get(i));
                    markedForRemoval++;
                    duplicateEntitiesRemoved.incrementAndGet();
                }
            }
        }

        return markedForRemoval;
    }

    /**
     * Generates a location key for grouping nearby entities.
     */
    private String getLocationKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return "invalid";
        }

        // Round to nearest block for grouping
        int x = (int) Math.round(location.getX());
        int y = (int) Math.round(location.getY());
        int z = (int) Math.round(location.getZ());

        return location.getWorld().getName() + "_" + x + "_" + y + "_" + z;
    }

    /**
     * Checks if cleanup is enabled for a specific world.
     */
    private boolean isWorldEnabled(World world) {
        List<String> enabledWorlds = ConfigManager.getEntityCleanupEnabledWorlds();
        return enabledWorlds.stream().anyMatch(w ->
                w.equalsIgnoreCase("all") || w.equalsIgnoreCase(world.getName())
        );
    }

    /**
     * Gets comprehensive cleanup statistics.
     */
    public static Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_entities_removed", totalEntitiesRemoved.get());
        stats.put("invalid_entities_removed", invalidEntitiesRemoved.get());
        stats.put("duplicate_entities_removed", duplicateEntitiesRemoved.get());
        stats.put("abandoned_vehicles_removed", abandonedVehiclesRemoved.get());
        stats.put("empty_containers_removed", emptyContainersRemoved.get());
        stats.put("out_of_bounds_entities_removed", outOfBoundsEntitiesRemoved.get());
        return stats;
    }

    /**
     * Resets all statistics counters.
     */
    public static void resetStatistics() {
        totalEntitiesRemoved.set(0);
        invalidEntitiesRemoved.set(0);
        duplicateEntitiesRemoved.set(0);
        abandonedVehiclesRemoved.set(0);
        emptyContainersRemoved.set(0);
        outOfBoundsEntitiesRemoved.set(0);
    }
}