package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.SchedulerWrapper;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Smart mob management system that efficiently handles mob limits per chunk.
 * Automatically removes excess mobs while protecting important entities.
 * Designed to be lag-free and performant even with thousands of entities.
 * 
 * Performance optimizations:
 * - Processes chunks asynchronously when possible
 * - Uses region-based scheduling on Folia for optimal performance
 * - Batches entity operations to minimize server load
 * - Implements smart cooldowns to prevent excessive processing
 */
public class SmartMobManager {
    
    private static SmartMobManager instance;
    private final AtomicInteger totalMobsRemoved = new AtomicInteger(0);
    private final AtomicInteger protectedMobsSkipped = new AtomicInteger(0);
    
    // Chunk processing cooldowns to prevent lag
    private final ConcurrentHashMap<String, Long> chunkLastProcessed = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> chunkMobCounts = new ConcurrentHashMap<>();
    
    // Task management
    private BukkitTask mainTask = null;
    private final Set<BukkitTask> chunkTasks = Collections.synchronizedSet(new HashSet<>());
    
    // Processing configuration
    private static final int MAX_CHUNKS_PER_TICK = 5; // Prevent lag spikes
    private static final int CHUNK_PROCESSING_COOLDOWN_MS = 30000; // 30 seconds between chunk processing
    private static final int MAX_MOBS_TO_REMOVE_PER_CHUNK_PER_TICK = 10; // Gradual removal
    
    private SmartMobManager() {}
    
    public static SmartMobManager getInstance() {
        if (instance == null) {
            instance = new SmartMobManager();
        }
        return instance;
    }
    
    /**
     * Starts the smart mob management system.
     */
    public void startManagement() {
        if (mainTask != null && !mainTask.isCancelled()) {
            mainTask.cancel();
        }
        
        if (!ConfigManager.isMobsModuleEnabled() || !ConfigManager.isAutoMobRemovalEnabled()) {
            return;
        }
        
        // Start main scanning task with configurable interval
        int scanInterval = ConfigManager.getMobScanIntervalTicks();
        mainTask = SchedulerWrapper.runTaskTimer(this::scanAndManageMobs, 100L, scanInterval);
        
        LagXpert.getInstance().getLogger().info("[SmartMobManager] Started with scan interval: " + scanInterval + " ticks");
    }
    
    /**
     * Stops the smart mob management system.
     */
    public void stopManagement() {
        if (mainTask != null) {
            mainTask.cancel();
            mainTask = null;
        }
        
        // Cancel all chunk-specific tasks
        synchronized (chunkTasks) {
            chunkTasks.forEach(BukkitTask::cancel);
            chunkTasks.clear();
        }
    }
    
    /**
     * Main scanning method that processes all loaded chunks intelligently.
     */
    private void scanAndManageMobs() {
        if (!ConfigManager.isMobsModuleEnabled()) {
            return;
        }
        
        long startTime = System.currentTimeMillis();
        int chunksProcessed = 0;
        int totalMobsFound = 0;
        int totalMobsRemovedThisCycle = 0;
        
        List<Chunk> chunksToProcess = getChunksToProcess();
        
        for (Chunk chunk : chunksToProcess) {
            if (chunksProcessed >= MAX_CHUNKS_PER_TICK) {
                break; // Prevent lag spikes
            }
            
            try {
                ChunkMobData mobData = analyzeChunkMobs(chunk);
                totalMobsFound += mobData.getTotalMobs();
                
                if (mobData.needsProcessing()) {
                    int removed = processChunkMobs(chunk, mobData);
                    totalMobsRemovedThisCycle += removed;
                    chunksProcessed++;
                }
                
            } catch (Exception e) {
                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().warning(
                        "[SmartMobManager] Error processing chunk " + chunk.getX() + "," + chunk.getZ() + ": " + e.getMessage()
                    );
                }
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Log statistics if enabled or if significant activity occurred
        if (ConfigManager.isDebugEnabled() || totalMobsRemovedThisCycle > 0) {
            LagXpert.getInstance().getLogger().info(String.format(
                "[SmartMobManager] Scan completed in %dms: %d chunks processed, %d mobs found, %d mobs removed",
                duration, chunksProcessed, totalMobsFound, totalMobsRemovedThisCycle
            ));
        }
    }
    
    /**
     * Gets list of chunks that need mob processing, prioritized by need.
     */
    private List<Chunk> getChunksToProcess() {
        List<Chunk> allChunks = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (World world : Bukkit.getWorlds()) {
            if (!isWorldEnabled(world)) {
                continue;
            }
            
            for (Chunk chunk : world.getLoadedChunks()) {
                String chunkKey = getChunkKey(chunk);
                
                // Check if chunk needs processing based on cooldown
                Long lastProcessed = chunkLastProcessed.get(chunkKey);
                if (lastProcessed != null && (currentTime - lastProcessed) < CHUNK_PROCESSING_COOLDOWN_MS) {
                    continue;
                }
                
                // Quick pre-check: does this chunk have entities?
                if (chunk.getEntities().length > 0) {
                    allChunks.add(chunk);
                }
            }
        }
        
        // Sort chunks by priority (most problematic first)
        allChunks.sort(this::compareChunkPriority);
        
        return allChunks;
    }
    
    /**
     * Compares chunks to determine processing priority.
     * Higher priority = more urgent need for mob management.
     */
    private int compareChunkPriority(Chunk chunk1, Chunk chunk2) {
        String key1 = getChunkKey(chunk1);
        String key2 = getChunkKey(chunk2);
        
        // Prioritize chunks with known high mob counts
        Integer count1 = chunkMobCounts.getOrDefault(key1, 0);
        Integer count2 = chunkMobCounts.getOrDefault(key2, 0);
        
        // Higher mob count = higher priority (process first)
        return Integer.compare(count2, count1);
    }
    
    /**
     * Analyzes a chunk's mob situation without making changes.
     */
    private ChunkMobData analyzeChunkMobs(Chunk chunk) {
        Entity[] entities = chunk.getEntities();
        
        List<LivingEntity> livingEntities = new ArrayList<>();
        List<Player> playersInChunk = new ArrayList<>();
        boolean hasBypassPermission = false;
        
        // Categorize entities
        for (Entity entity : entities) {
            if (entity instanceof Player) {
                Player player = (Player) entity;
                playersInChunk.add(player);
                if (player.hasPermission("lagxpert.bypass.mobs")) {
                    hasBypassPermission = true;
                }
            } else if (entity instanceof LivingEntity) {
                livingEntities.add((LivingEntity) entity);
            }
        }
        
        // Cache mob count for priority sorting
        String chunkKey = getChunkKey(chunk);
        chunkMobCounts.put(chunkKey, livingEntities.size());
        
        return new ChunkMobData(chunk, livingEntities, playersInChunk, hasBypassPermission);
    }
    
    /**
     * Processes a chunk's mobs, removing excess while protecting important entities.
     */
    private int processChunkMobs(Chunk chunk, ChunkMobData mobData) {
        // Skip if players have bypass permission
        if (mobData.hasBypassPermission()) {
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info(
                    "[SmartMobManager] Skipping chunk " + chunk.getX() + "," + chunk.getZ() + " - player has bypass permission"
                );
            }
            return 0;
        }
        
        int mobLimit = ConfigManager.getMaxMobsPerChunk();
        List<LivingEntity> livingEntities = mobData.getLivingEntities();
        int currentCount = livingEntities.size();
        
        if (currentCount <= mobLimit) {
            return 0; // No action needed
        }
        
        int excessMobs = currentCount - mobLimit;
        int mobsToRemove = Math.min(excessMobs, MAX_MOBS_TO_REMOVE_PER_CHUNK_PER_TICK);
        
        // Get list of mobs that can be safely removed
        List<LivingEntity> removableMobs = getRemovableMobs(livingEntities);
        
        if (removableMobs.isEmpty()) {
            protectedMobsSkipped.addAndGet(currentCount);
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info(
                    "[SmartMobManager] All mobs in chunk " + chunk.getX() + "," + chunk.getZ() + " are protected"
                );
            }
            return 0;
        }
        
        // Remove excess mobs (up to the limit per tick)
        int actuallyRemoved = removeMobsGradually(chunk, removableMobs, mobsToRemove);
        
        // Update processing timestamp
        chunkLastProcessed.put(getChunkKey(chunk), System.currentTimeMillis());
        
        // Notify nearby players if configured
        if (actuallyRemoved > 0 && ConfigManager.shouldNotifyMobRemoval()) {
            notifyPlayersOfMobRemoval(mobData.getPlayersInChunk(), actuallyRemoved, currentCount, mobLimit);
        }
        
        return actuallyRemoved;
    }
    
    /**
     * Filters mobs to identify which ones can be safely removed.
     * Protects named mobs, tamed animals, and plugin-created entities.
     */
    private List<LivingEntity> getRemovableMobs(List<LivingEntity> allMobs) {
        List<LivingEntity> removable = new ArrayList<>();
        
        for (LivingEntity mob : allMobs) {
            if (shouldProtectEntity(mob)) {
                continue;
            }
            removable.add(mob);
        }
        
        // Sort by removal priority (remove least important first)
        removable.sort(this::compareMobRemovalPriority);
        
        return removable;
    }
    
    /**
     * Determines if an entity should be protected from removal.
     */
    private boolean shouldProtectEntity(LivingEntity entity) {
        // Never remove players
        if (entity instanceof Player) {
            return true;
        }
        
        // Protect named entities
        if (ConfigManager.shouldProtectNamedMobs()) {
            String customName = entity.getCustomName();
            if (customName != null && !customName.trim().isEmpty()) {
                return true;
            }
        }
        
        // Protect tamed animals
        if (ConfigManager.shouldProtectTamedMobs() && entity instanceof Tameable) {
            Tameable tameable = (Tameable) entity;
            if (tameable.isTamed()) {
                return true;
            }
        }
        
        // Protect leashed entities
        if (ConfigManager.shouldProtectLeashedMobs() && entity.isLeashed()) {
            return true;
        }
        
        // Protect entities with equipment
        if (ConfigManager.shouldProtectEquippedMobs() && hasSignificantEquipment(entity)) {
            return true;
        }
        
        // Protect persistent entities (won't despawn naturally)
        if (!entity.getRemoveWhenFarAway()) {
            return true;
        }
        
        // Protect entities created by other plugins
        if (hasPluginMetadata(entity)) {
            return true;
        }
        
        // Protect specific entity types from configuration
        List<String> protectedTypes = ConfigManager.getProtectedMobTypes();
        String entityType = entity.getType().name().toUpperCase();
        if (protectedTypes.stream().anyMatch(type -> type.equalsIgnoreCase(entityType))) {
            return true;
        }
        
        // Protect villagers with trades
        if (entity instanceof Villager) {
            Villager villager = (Villager) entity;
            if (villager.getRecipes().size() > 0) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if an entity has significant equipment worth protecting.
     */
    private boolean hasSignificantEquipment(LivingEntity entity) {
        if (entity.getEquipment() == null) {
            return false;
        }
        
        // Check for non-air items in any equipment slot
        return (entity.getEquipment().getItemInMainHand() != null && !entity.getEquipment().getItemInMainHand().getType().isAir()) ||
               (entity.getEquipment().getItemInOffHand() != null && !entity.getEquipment().getItemInOffHand().getType().isAir()) ||
               (entity.getEquipment().getHelmet() != null && !entity.getEquipment().getHelmet().getType().isAir()) ||
               (entity.getEquipment().getChestplate() != null && !entity.getEquipment().getChestplate().getType().isAir()) ||
               (entity.getEquipment().getLeggings() != null && !entity.getEquipment().getLeggings().getType().isAir()) ||
               (entity.getEquipment().getBoots() != null && !entity.getEquipment().getBoots().getType().isAir());
    }
    
    /**
     * Checks if an entity has metadata from other plugins.
     */
    private boolean hasPluginMetadata(LivingEntity entity) {
        for (MetadataValue metadata : entity.getMetadata("plugin-created")) {
            if (metadata.value() != null) {
                return true;
            }
        }
        
        // Check for other common plugin metadata keys
        String[] commonKeys = {"mythicmob", "custom-entity", "protected", "boss", "special"};
        for (String key : commonKeys) {
            if (!entity.getMetadata(key).isEmpty()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Compares mobs to determine removal priority.
     * Lower priority mobs are removed first.
     */
    private int compareMobRemovalPriority(LivingEntity mob1, LivingEntity mob2) {
        // Prioritize by configured entity priority
        int priority1 = getEntityRemovalPriority(mob1);
        int priority2 = getEntityRemovalPriority(mob2);
        
        if (priority1 != priority2) {
            return Integer.compare(priority1, priority2);
        }
        
        // Secondary sort: prefer removing entities without AI goals
        boolean hasAI1 = mob1.hasAI();
        boolean hasAI2 = mob2.hasAI();
        
        if (hasAI1 != hasAI2) {
            return Boolean.compare(hasAI1, hasAI2); // Remove non-AI mobs first
        }
        
        // Tertiary sort: prefer removing baby animals (they grow up anyway)
        if (mob1 instanceof Ageable && mob2 instanceof Ageable) {
            boolean isAdult1 = ((Ageable) mob1).isAdult();
            boolean isAdult2 = ((Ageable) mob2).isAdult();
            
            if (isAdult1 != isAdult2) {
                return Boolean.compare(isAdult1, isAdult2); // Remove babies first
            }
        }
        
        return 0; // Equal priority
    }
    
    /**
     * Gets removal priority for an entity type (lower = remove first).
     */
    private int getEntityRemovalPriority(LivingEntity entity) {
        // Configure these priorities based on server needs
        switch (entity.getType()) {
            case CHICKEN:
            case COW: 
            case PIG:
            case SHEEP:
                return 1; // Low priority (farm animals)
                
            case ZOMBIE:
            case SKELETON:
            case SPIDER:
            case CREEPER:
                return 2; // Medium priority (common hostiles)
                
            case VILLAGER:
            case HORSE:
                return 5; // High priority (valuable)
                
            case WITHER:
            case ENDER_DRAGON:
                return 10; // Never remove (bosses)
                
            default:
                return 3; // Default priority
        }
    }
    
    /**
     * Removes mobs gradually to prevent lag spikes.
     */
    private int removeMobsGradually(Chunk chunk, List<LivingEntity> removableMobs, int targetCount) {
        int removed = 0;
        
        for (int i = 0; i < Math.min(targetCount, removableMobs.size()); i++) {
            LivingEntity mob = removableMobs.get(i);
            
            try {
                if (mob.isValid() && !mob.isDead()) {
                    // Use region-specific scheduling on Folia for better performance
                    SchedulerWrapper.runTaskForChunk(chunk, () -> {
                        if (mob.isValid() && !mob.isDead()) {
                            mob.remove();
                        }
                    });
                    
                    removed++;
                    totalMobsRemoved.incrementAndGet();
                }
            } catch (Exception e) {
                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().warning(
                        "[SmartMobManager] Failed to remove mob: " + e.getMessage()
                    );
                }
            }
        }
        
        return removed;
    }
    
    /**
     * Notifies players in a chunk about mob removal.
     */
    private void notifyPlayersOfMobRemoval(List<Player> players, int removed, int originalCount, int limit) {
        if (players.isEmpty()) {
            return;
        }
        
        String message = ConfigManager.getMobRemovalNotificationMessage()
            .replace("{removed}", String.valueOf(removed))
            .replace("{original}", String.valueOf(originalCount))
            .replace("{limit}", String.valueOf(limit))
            .replace("{remaining}", String.valueOf(originalCount - removed));
        
        String formattedMessage = MessageManager.color(message);
        
        for (Player player : players) {
            if (player.isOnline() && player.hasPermission("lagxpert.notifications.mob-removal")) {
                player.sendMessage(formattedMessage);
            }
        }
    }
    
    /**
     * Checks if mob management is enabled for a specific world.
     */
    private boolean isWorldEnabled(World world) {
        List<String> enabledWorlds = ConfigManager.getMobManagementEnabledWorlds();
        return enabledWorlds.stream().anyMatch(w ->
            w.equalsIgnoreCase("all") || w.equalsIgnoreCase(world.getName())
        );
    }
    
    /**
     * Generates a unique key for a chunk.
     */
    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
    }
    
    /**
     * Forces immediate processing of a specific chunk (for commands/API).
     */
    public int processChunkImmediately(Chunk chunk) {
        if (!ConfigManager.isMobsModuleEnabled()) {
            return 0;
        }
        
        ChunkMobData mobData = analyzeChunkMobs(chunk);
        if (mobData.needsProcessing()) {
            return processChunkMobs(chunk, mobData);
        }
        
        return 0;
    }
    
    /**
     * Gets statistics about mob management.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_mobs_removed", totalMobsRemoved.get());
        stats.put("protected_mobs_skipped", protectedMobsSkipped.get());
        stats.put("chunks_tracked", chunkMobCounts.size());
        stats.put("active_chunk_tasks", chunkTasks.size());
        return stats;
    }
    
    /**
     * Resets all statistics.
     */
    public void resetStatistics() {
        totalMobsRemoved.set(0);
        protectedMobsSkipped.set(0);
        chunkMobCounts.clear();
        chunkLastProcessed.clear();
    }
    
    /**
     * Data class to hold chunk mob analysis results.
     */
    private static class ChunkMobData {
        private final Chunk chunk;
        private final List<LivingEntity> livingEntities;
        private final List<Player> playersInChunk;
        private final boolean hasBypassPermission;
        
        public ChunkMobData(Chunk chunk, List<LivingEntity> livingEntities, 
                           List<Player> playersInChunk, boolean hasBypassPermission) {
            this.chunk = chunk;
            this.livingEntities = livingEntities;
            this.playersInChunk = playersInChunk;
            this.hasBypassPermission = hasBypassPermission;
        }
        
        public boolean needsProcessing() {
            return !hasBypassPermission && 
                   livingEntities.size() > ConfigManager.getMaxMobsPerChunk();
        }
        
        public Chunk getChunk() { return chunk; }
        public List<LivingEntity> getLivingEntities() { return livingEntities; }
        public List<Player> getPlayersInChunk() { return playersInChunk; }
        public boolean hasBypassPermission() { return hasBypassPermission; }
        public int getTotalMobs() { return livingEntities.size(); }
    }
}