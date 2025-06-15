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
 * Fixed protection logic for named entities, tamed animals, and other important entities.
 */
public class EntityCleanupTask extends BukkitRunnable {

    // Statistics tracking
    private static final AtomicInteger totalEntitiesRemoved = new AtomicInteger(0);
    private static final AtomicInteger invalidEntitiesRemoved = new AtomicInteger(0);
    private static final AtomicInteger duplicateEntitiesRemoved = new AtomicInteger(0);
    private static final AtomicInteger abandonedVehiclesRemoved = new AtomicInteger(0);
    private static final AtomicInteger emptyContainersRemoved = new AtomicInteger(0);
    private static final AtomicInteger outOfBoundsEntitiesRemoved = new AtomicInteger(0);

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

        // Broadcast completion message if enabled and threshold is met
        if (ConfigManager.shouldBroadcastEntityCleanupCompletion() &&
                totalCleaned >= ConfigManager.getEntityCleanupBroadcastThreshold()) {
            String message = ConfigManager.getEntityCleanupCompleteMessage()
                    .replace("{count}", String.valueOf(totalCleaned));
            Bukkit.broadcastMessage(message);
        }

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
                // FIXED: Skip entities that should be protected - check this FIRST
                if (shouldSkipEntity(entity)) {
                    continue;
                }

                // Check for invalid/corrupted entities
                if (ConfigManager.shouldCleanupInvalidEntities() && isInvalidEntity(entity)) {
                    entitiesToRemove.add(entity);
                    invalidEntitiesRemoved.incrementAndGet();
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().info("[EntityCleanupTask] Marking invalid entity for removal: " + entity.getType());
                    }
                    continue;
                }

                // Check if entity is outside world border
                if (ConfigManager.shouldCleanupOutOfBoundsEntities() && isOutsideWorldBorder(entity)) {
                    entitiesToRemove.add(entity);
                    outOfBoundsEntitiesRemoved.incrementAndGet();
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().info("[EntityCleanupTask] Marking out-of-bounds entity for removal: " + entity.getType());
                    }
                    continue;
                }

                // Check for abandoned vehicles
                if (ConfigManager.shouldCleanupAbandonedVehicles() && isAbandonedVehicle(entity)) {
                    entitiesToRemove.add(entity);
                    abandonedVehiclesRemoved.incrementAndGet();
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().info("[EntityCleanupTask] Marking abandoned vehicle for removal: " + entity.getType());
                    }
                    continue;
                }

                // Check for empty item frames and armor stands
                if (isEmptyContainer(entity)) {
                    entitiesToRemove.add(entity);
                    emptyContainersRemoved.incrementAndGet();
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().info("[EntityCleanupTask] Marking empty container for removal: " + entity.getType());
                    }
                    continue;
                }

                // Group for duplicate detection only if enabled
                if (ConfigManager.shouldCleanupDuplicateEntities()) {
                    String locationKey = getLocationKey(entity.getLocation());
                    entitiesByLocation.computeIfAbsent(locationKey, k -> new ArrayList<>()).add(entity);
                }

            } catch (Exception e) {
                // Entity might be corrupted, remove it if invalid entity cleanup is enabled
                if (ConfigManager.shouldCleanupInvalidEntities()) {
                    entitiesToRemove.add(entity);
                    invalidEntitiesRemoved.incrementAndGet();
                }

                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().warning(
                            "[EntityCleanupTask] Exception while processing entity: " + e.getMessage()
                    );
                }
            }
        }

        // Detect and mark duplicates only if enabled
        if (ConfigManager.shouldCleanupDuplicateEntities()) {
            for (List<Entity> locationGroup : entitiesByLocation.values()) {
                if (locationGroup.size() > 1) {
                    removedCount += removeDuplicateEntities(locationGroup, entitiesToRemove);
                }
            }
        }

        // Remove marked entities
        for (Entity entity : entitiesToRemove) {
            try {
                if (entity.isValid()) {
                    entity.remove();
                    removedCount++;

                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().info("[EntityCleanupTask] Removed entity: " + entity.getType() + " at " + entity.getLocation());
                    }
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
     * FIXED: Proper protection logic with configuration support.
     */
    private boolean shouldSkipEntity(Entity entity) {
        // Never remove players
        if (entity instanceof Player) {
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info("[EntityCleanupTask] Skipping player: " + entity.getName());
            }
            return true;
        }

        // Check if entity type is in protected list
        List<String> protectedTypes = ConfigManager.getProtectedEntityTypes();
        String entityTypeName = entity.getType().name().toUpperCase();
        if (protectedTypes.stream().anyMatch(type -> type.equalsIgnoreCase(entityTypeName))) {
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info("[EntityCleanupTask] Skipping protected entity type: " + entityTypeName);
            }
            return true;
        }

        // FIXED: Skip entities with custom names if configured (and they actually have names)
        if (ConfigManager.shouldSkipNamedEntities()) {
            String customName = entity.getCustomName();
            if (customName != null && !customName.trim().isEmpty()) {
                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().info("[EntityCleanupTask] Skipping named entity: " + customName + " (" + entity.getType() + ")");
                }
                return true;
            }
        }

        // FIXED: Skip tamed animals if configured (and they are actually tamed)
        if (ConfigManager.shouldSkipTamedAnimals() && entity instanceof Tameable) {
            Tameable tameable = (Tameable) entity;
            if (tameable.isTamed()) {
                if (ConfigManager.isDebugEnabled()) {
                    String ownerName = tameable.getOwner() != null ? tameable.getOwner().getName() : "Unknown";
                    LagXpert.getInstance().getLogger().info("[EntityCleanupTask] Skipping tamed animal: " + entity.getType() + " owned by " + ownerName);
                }
                return true;
            }
        }

        // FIXED: Skip leashed entities if configured (and they are actually leashed)
        if (ConfigManager.shouldSkipLeashedEntities() && entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            if (living.isLeashed()) {
                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().info("[EntityCleanupTask] Skipping leashed entity: " + entity.getType());
                }
                return true;
            }
        }

        // Skip entities in vehicles or with passengers
        if (entity.getVehicle() != null || !entity.getPassengers().isEmpty()) {
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info("[EntityCleanupTask] Skipping entity with vehicle/passengers: " + entity.getType());
            }
            return true;
        }

        // Skip persistent entities (those that don't despawn naturally)
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            if (!living.getRemoveWhenFarAway()) {
                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().info("[EntityCleanupTask] Skipping persistent entity: " + entity.getType());
                }
                return true;
            }
        }

        // Skip entities with special AI or goals (villagers with trades, etc.)
        if (entity instanceof Villager) {
            Villager villager = (Villager) entity;
            if (villager.getRecipes().size() > 0) {
                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().info("[EntityCleanupTask] Skipping villager with trades");
                }
                return true;
            }
        }

        // Skip entities created by plugins (have custom metadata)
        if (!entity.getMetadata("plugin-created").isEmpty()) {
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info("[EntityCleanupTask] Skipping plugin-created entity: " + entity.getType());
            }
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

            // Check for entities at invalid Y coordinates (below bedrock or above build limit)
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
        if (!(entity instanceof Vehicle)) {
            return false;
        }

        // Don't remove vehicles with passengers
        if (!entity.getPassengers().isEmpty()) {
            return false;
        }

        // Check if there are players nearby who might use it
        if (entity instanceof Boat || entity instanceof Minecart) {
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
            if (!ConfigManager.shouldCleanupEmptyItemFrames()) {
                return false;
            }
            ItemFrame frame = (ItemFrame) entity;
            ItemStack item = frame.getItem();
            return item == null || item.getType().isAir();
        }

        if (entity instanceof ArmorStand) {
            if (!ConfigManager.shouldCleanupEmptyArmorStands()) {
                return false;
            }
            ArmorStand stand = (ArmorStand) entity;

            // Check if armor stand has any equipment
            boolean hasEquipment = false;

            // Check main hand
            ItemStack mainHand = stand.getItemInHand();
            hasEquipment |= mainHand != null && !mainHand.getType().isAir();

            // Check armor pieces
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
                // Sort by preference: keep named entities, tamed entities, entities with equipment, etc.
                typeGroup.sort((e1, e2) -> {
                    // Prefer named entities
                    boolean e1HasName = e1.getCustomName() != null && !e1.getCustomName().trim().isEmpty();
                    boolean e2HasName = e2.getCustomName() != null && !e2.getCustomName().trim().isEmpty();
                    if (e1HasName && !e2HasName) return -1;
                    if (!e1HasName && e2HasName) return 1;

                    // Prefer tamed entities
                    boolean e1Tamed = e1 instanceof Tameable && ((Tameable) e1).isTamed();
                    boolean e2Tamed = e2 instanceof Tameable && ((Tameable) e2).isTamed();
                    if (e1Tamed && !e2Tamed) return -1;
                    if (!e1Tamed && e2Tamed) return 1;

                    // Prefer entities with equipment
                    if (e1 instanceof LivingEntity && e2 instanceof LivingEntity) {
                        LivingEntity l1 = (LivingEntity) e1;
                        LivingEntity l2 = (LivingEntity) e2;

                        boolean e1HasEquipment = l1.getEquipment() != null &&
                                (hasValidItem(l1.getEquipment().getItemInMainHand()) ||
                                        hasValidItem(l1.getEquipment().getHelmet()) ||
                                        hasValidItem(l1.getEquipment().getChestplate()) ||
                                        hasValidItem(l1.getEquipment().getLeggings()) ||
                                        hasValidItem(l1.getEquipment().getBoots()));

                        boolean e2HasEquipment = l2.getEquipment() != null &&
                                (hasValidItem(l2.getEquipment().getItemInMainHand()) ||
                                        hasValidItem(l2.getEquipment().getHelmet()) ||
                                        hasValidItem(l2.getEquipment().getChestplate()) ||
                                        hasValidItem(l2.getEquipment().getLeggings()) ||
                                        hasValidItem(l2.getEquipment().getBoots()));

                        if (e1HasEquipment && !e2HasEquipment) return -1;
                        if (!e1HasEquipment && e2HasEquipment) return 1;
                    }

                    return 0;
                });

                // Keep the first (most preferred) entity, remove the rest
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
     * Helper method to check if an ItemStack is valid and not air.
     */
    private boolean hasValidItem(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    /**
     * Generates a location key for grouping nearby entities.
     */
    private String getLocationKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return "invalid";
        }

        // Use configured radius for duplicate detection
        double radius = ConfigManager.getDuplicateDetectionRadius();

        // Round to radius precision for grouping
        int x = (int) Math.round(location.getX() / radius) * (int) radius;
        int y = (int) Math.round(location.getY() / radius) * (int) radius;
        int z = (int) Math.round(location.getZ() / radius) * (int) radius;

        return location.getWorld().getName() + "_" + x + "_" + y + "_" + z;
    }

    /**
     * Checks if cleanup is enabled for a specific world.
     */
    private boolean isWorldEnabled(World world) {
        // Check blacklisted worlds first
        List<String> blacklistedWorlds = ConfigManager.getBlacklistedWorlds();
        if (blacklistedWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(world.getName()))) {
            return false;
        }

        // Check enabled worlds
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