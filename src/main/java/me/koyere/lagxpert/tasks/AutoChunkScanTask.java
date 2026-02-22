package me.koyere.lagxpert.tasks;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.api.events.ChunkOverloadEvent;
import me.koyere.lagxpert.cache.ChunkDataCache;
import me.koyere.lagxpert.system.AlertCooldownManager;
import me.koyere.lagxpert.utils.ChunkUtils;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Scheduled task that periodically scans loaded chunks near players for elements
 * exceeding configured limits (e.g., mobs, hoppers, chests).
 * It can fire ChunkOverloadEvents and send warnings (with cooldowns)
 * to players in affected chunks based on detailed alert configurations.
 * Now uses cache system for improved performance.
 */
public class AutoChunkScanTask extends BukkitRunnable {

    // Replaced record ScannableElement with a final class for Java 11 compatibility
    private static final class ScannableElement {
        private final String translationKey;
        private final String displayName;
        private final Material material;
        private final ToIntFunction<Chunk> limitFunction;
        private final String overloadCauseSuffix;
        private final CounterType counterType;
        private final Supplier<Boolean> nearLimitWarningToggle;

        public ScannableElement(String translationKey, String displayName, Material material, ToIntFunction<Chunk> limitFunction, String overloadCauseSuffix, CounterType counterType, Supplier<Boolean> nearLimitToggle) {
            this.translationKey = translationKey;
            this.displayName = displayName;
            this.material = material;
            this.limitFunction = limitFunction;
            this.overloadCauseSuffix = overloadCauseSuffix;
            this.counterType = counterType;
            this.nearLimitWarningToggle = nearLimitToggle;
        }

        public ScannableElement(String translationKey, String displayName, ToIntFunction<Chunk> limitFunction, String overloadCauseSuffix, Supplier<Boolean> nearLimitToggle) {
            this(translationKey, displayName, null, limitFunction, overloadCauseSuffix, CounterType.LIVING_ENTITY, nearLimitToggle);
        }

        /** Returns the translated display name from messages.yml, falling back to the hardcoded default. */
        public String getDisplayName() { return MessageManager.getTranslation(translationKey, displayName); }
        public String getTranslationKey() { return translationKey; }
        public Material getMaterial() { return material; }
        public int getLimit(Chunk chunk) { return limitFunction.applyAsInt(chunk); }
        public String getOverloadCauseSuffix() { return overloadCauseSuffix; }
        public CounterType getCounterType() { return counterType; }
        public Supplier<Boolean> getNearLimitWarningToggle() { return nearLimitWarningToggle; }
    }

    private enum CounterType {
        LIVING_ENTITY,
        TILE_ENTITY,
        BLOCK_ITERATION,
        CUSTOM_COUNT
    }

    private static final List<ScannableElement> elementsToScan = new ArrayList<>();

    static {
        elementsToScan.add(new ScannableElement("mobs", "Mobs", chunk -> ConfigManager.getMaxMobsPerChunk(chunk.getWorld()), "mobs", ConfigManager::shouldWarnOnMobsNearLimit));
        elementsToScan.add(new ScannableElement("hoppers", "Hoppers", Material.HOPPER, chunk -> ConfigManager.getMaxHoppersPerChunk(chunk.getWorld()), "hoppers", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnHoppersNearLimit));
        // Chests and Trapped Chests unified into a single entry to avoid duplicate statistics
        elementsToScan.add(new ScannableElement("chests", "Chests", Material.CHEST, chunk -> ConfigManager.getMaxChestsPerChunk(chunk.getWorld()), "chests", CounterType.CUSTOM_COUNT, ConfigManager::shouldWarnOnChestsNearLimit));
        elementsToScan.add(new ScannableElement("furnaces", "Furnaces", Material.FURNACE, chunk -> ConfigManager.getMaxFurnacesPerChunk(chunk.getWorld()), "furnaces", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnFurnacesNearLimit));
        elementsToScan.add(new ScannableElement("blast_furnaces", "Blast Furnaces", Material.BLAST_FURNACE, chunk -> ConfigManager.getMaxBlastFurnacesPerChunk(chunk.getWorld()), "blast_furnaces", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnBlastFurnacesNearLimit));
        elementsToScan.add(new ScannableElement("smokers", "Smokers", Material.SMOKER, chunk -> ConfigManager.getMaxSmokersPerChunk(chunk.getWorld()), "smokers", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnSmokersNearLimit));
        elementsToScan.add(new ScannableElement("barrels", "Barrels", Material.BARREL, chunk -> ConfigManager.getMaxBarrelsPerChunk(chunk.getWorld()), "barrels", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnBarrelsNearLimit));
        elementsToScan.add(new ScannableElement("droppers", "Droppers", Material.DROPPER, chunk -> ConfigManager.getMaxDroppersPerChunk(chunk.getWorld()), "droppers", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnDroppersNearLimit));
        elementsToScan.add(new ScannableElement("dispensers", "Dispensers", Material.DISPENSER, chunk -> ConfigManager.getMaxDispensersPerChunk(chunk.getWorld()), "dispensers", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnDispensersNearLimit));
        elementsToScan.add(new ScannableElement("shulker_boxes", "Shulker Boxes", Material.SHULKER_BOX, chunk -> ConfigManager.getMaxShulkerBoxesPerChunk(chunk.getWorld()), "shulker_boxes", CounterType.CUSTOM_COUNT, ConfigManager::shouldWarnOnShulkerBoxesNearLimit));
        elementsToScan.add(new ScannableElement("tnt", "TNT", Material.TNT, chunk -> ConfigManager.getMaxTntPerChunk(), "tnt", CounterType.BLOCK_ITERATION, ConfigManager::shouldWarnOnTntNearLimit));
        // Pistons and Sticky Pistons unified into a single entry to avoid duplicate statistics
        elementsToScan.add(new ScannableElement("pistons", "Pistons", Material.PISTON, chunk -> ConfigManager.getMaxPistonsPerChunk(), "pistons", CounterType.CUSTOM_COUNT, ConfigManager::shouldWarnOnPistonsNearLimit));
        elementsToScan.add(new ScannableElement("observers", "Observers", Material.OBSERVER, chunk -> ConfigManager.getMaxObserversPerChunk(), "observers", CounterType.BLOCK_ITERATION, ConfigManager::shouldWarnOnObserversNearLimit));
    }

    @Override
    public void run() {
        if (!ConfigManager.isAutoChunkScanModuleEnabled() && !ConfigManager.isDebugEnabled()) {
            return;
        }

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info("[LagXpert] AutoChunkScanTask: Starting scan cycle...");
        }

        long scanStartTime = System.currentTimeMillis();
        int chunksScanned = 0;
        int cacheHits = 0;

        for (World world : Bukkit.getWorlds()) {
            List<Player> playersInWorld = world.getPlayers();
            if (playersInWorld.isEmpty() && !ConfigManager.isDebugEnabled()) {
                continue;
            }

            for (Chunk chunk : world.getLoadedChunks()) {
                final Chunk currentChunk = chunk;
                boolean playerIsNear = ConfigManager.isDebugEnabled() || playersInWorld.stream().anyMatch(p ->
                        p.getWorld().equals(currentChunk.getWorld()) &&
                                p.getLocation().distanceSquared(currentChunk.getBlock(7, p.getLocation().getBlockY(), 7).getLocation()) <= (48 * 48)
                );

                if (!playerIsNear) {
                    continue;
                }

                chunksScanned++;
                List<Player> playersInThisChunk = getPlayersInChunk(currentChunk);
                boolean isChunkCurrentlyPopulatedByPlayers = !playersInThisChunk.isEmpty();

                StringBuilder overloadedElementsSummary = new StringBuilder();
                boolean chunkIsActuallyOverloaded = false;

                // Use complete chunk analysis with cache
                ChunkDataCache.ChunkData chunkData = ChunkUtils.performCompleteChunkAnalysis(currentChunk);
                if (chunkData != null && chunkData.isComplete()) {
                    cacheHits++;
                }

                for (ScannableElement element : elementsToScan) {
                    int count = getElementCount(currentChunk, element, chunkData);
                    int limit = element.getLimit(currentChunk);

                    if (limit <= 0) continue;

                    if (count > limit) {
                        fireChunkOverloadEvent(currentChunk, element.getOverloadCauseSuffix() + "_scan_overload");
                        chunkIsActuallyOverloaded = true;
                        if (overloadedElementsSummary.length() > 0) {
                            overloadedElementsSummary.append(", ");
                        }
                        overloadedElementsSummary.append(count).append(" ").append(element.getDisplayName());
                    } else if (count >= (int) (limit * 0.8) && isChunkCurrentlyPopulatedByPlayers && limit > 0) {
                        if (ConfigManager.isAlertsModuleEnabled() && ConfigManager.shouldAutoScanTriggerIndividualNearLimitWarnings()) {
                            sendNearLimitWarning(playersInThisChunk, element, count, limit, currentChunk);
                        }
                    }
                }

                if (chunkIsActuallyOverloaded && isChunkCurrentlyPopulatedByPlayers &&
                        ConfigManager.isAlertsModuleEnabled() && ConfigManager.shouldAutoScanSendOverloadSummary()) {

                    Map<String, Object> placeholders = new HashMap<>();
                    placeholders.put("chunk_x", String.valueOf(currentChunk.getX()));
                    placeholders.put("chunk_z", String.valueOf(currentChunk.getZ()));
                    placeholders.put("world", currentChunk.getWorld().getName());
                    placeholders.put("details", overloadedElementsSummary.toString());

                    String alertMessage = MessageManager.getPrefixedFormattedMessage("alerts.chunk-scan.overloaded-summary", placeholders);

                    // Apply cooldown for the summary message per player
                    String summaryAlertKey = AlertCooldownManager.generateAlertKey("scan_overload_summary", currentChunk);
                    for (Player player : playersInThisChunk) {
                        if (AlertCooldownManager.canSendAlert(player, summaryAlertKey)) {
                            player.sendMessage(alertMessage);
                        }
                    }
                }
            }
        }

        long scanDuration = System.currentTimeMillis() - scanStartTime;

        if (ConfigManager.isDebugEnabled()) {
            double cacheHitRate = chunksScanned > 0 ? (double) cacheHits / chunksScanned * 100 : 0;
            LagXpert.getInstance().getLogger().info("[LagXpert] AutoChunkScanTask: Scan cycle finished. " +
                    "Chunks scanned: " + chunksScanned + ", Cache hits: " + cacheHits +
                    " (" + String.format("%.1f", cacheHitRate) + "%), Duration: " + scanDuration + "ms");
        }
    }

    /**
     * Gets the count for a specific element using the most appropriate method.
     * Uses cached data when available for maximum performance.
     */
    private int getElementCount(Chunk chunk, ScannableElement element, ChunkDataCache.ChunkData chunkData) {
        switch (element.getCounterType()) {
            case LIVING_ENTITY:
                if (chunkData != null && chunkData.isComplete()) {
                    return chunkData.getLivingEntities();
                }
                return countLivingEntities(chunk);

            case TILE_ENTITY:
                if (chunkData != null && chunkData.isComplete()) {
                    return chunkData.getBlockCount(element.getMaterial());
                }
                return countTileEntitiesInChunk(chunk, element.getMaterial());

            case BLOCK_ITERATION:
                if (chunkData != null && chunkData.isComplete()) {
                    return chunkData.getBlockCount(element.getMaterial());
                }
                return countAllBlocksOfTypeInChunk(chunk, element.getMaterial());

            case CUSTOM_COUNT:
                if (chunkData != null && chunkData.isComplete()) {
                    return getCustomCount(chunkData, element);
                }
                return getCustomCountDirect(chunk, element);

            default:
                if (LagXpert.getInstance() != null) {
                    LagXpert.getInstance().getLogger().warning("[LagXpert] AutoChunkScanTask: Unknown counter type: " + element.getCounterType());
                }
                return 0;
        }
    }

    /**
     * Gets custom counts from cached data based on element type.
     */
    private int getCustomCount(ChunkDataCache.ChunkData chunkData, ScannableElement element) {
        switch (element.getOverloadCauseSuffix()) {
            case "chests":
                return chunkData.getCustomCount("all_chests");
            case "shulker_boxes":
                return chunkData.getCustomCount("all_shulker_boxes");
            case "pistons":
                return chunkData.getCustomCount("all_pistons");
            default:
                return chunkData.getBlockCount(element.getMaterial());
        }
    }

    /**
     * Gets custom counts directly from chunk when cache is not available.
     */
    private int getCustomCountDirect(Chunk chunk, ScannableElement element) {
        switch (element.getOverloadCauseSuffix()) {
            case "chests":
                return ChunkUtils.countTileEntitiesInChunk(chunk, Material.CHEST) +
                        ChunkUtils.countTileEntitiesInChunk(chunk, Material.TRAPPED_CHEST);
            case "shulker_boxes":
                return ChunkUtils.countAllShulkerBoxesInChunk(chunk);
            case "pistons":
                return ChunkUtils.countAllBlocksOfTypeSlow(chunk, Material.PISTON) +
                        ChunkUtils.countAllBlocksOfTypeSlow(chunk, Material.STICKY_PISTON);
            default:
                return ChunkUtils.countTileEntitiesInChunk(chunk, element.getMaterial());
        }
    }

    private List<Player> getPlayersInChunk(Chunk chunk) {
        List<Player> players = new ArrayList<>();
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player) {
                Player player = (Player) entity;
                players.add(player);
            }
        }
        return players;
    }

    private void sendNearLimitWarning(List<Player> playersInChunk, ScannableElement element, int currentValue, int maxValue, Chunk chunkContext) {
        // This method is only called if shouldAutoScanTriggerIndividualNearLimitWarnings is true globally for this task.
        // Additionally, check if general alerts are on AND if the specific near-limit warning for THIS element type is enabled.
        if (playersInChunk.isEmpty() || !ConfigManager.isAlertsModuleEnabled() ||
                (element.getNearLimitWarningToggle() != null && !element.getNearLimitWarningToggle().get())) {
            return;
        }

        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("type", element.getDisplayName());
        placeholders.put("used", String.valueOf(currentValue));
        placeholders.put("max", String.valueOf(maxValue));
        String message = MessageManager.getPrefixedFormattedMessage("limits.near-limit", placeholders);

        // Generate a unique alert key for this specific near-limit warning type and chunk
        String alertKey = AlertCooldownManager.generateAlertKey(element.getOverloadCauseSuffix() + "_near_limit", chunkContext);

        for (Player player : playersInChunk) {
            if (AlertCooldownManager.canSendAlert(player, alertKey)) {
                player.sendMessage(message);
            }
        }
    }

    private int countLivingEntities(Chunk chunk) {
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity) {
                count++;
            }
        }
        return count;
    }

    private int countTileEntitiesInChunk(Chunk chunk, Material materialToCount) {
        int count = 0;
        if (materialToCount == null) return 0;

        if (materialToCount == Material.SHULKER_BOX) {
            for (BlockState blockState : chunk.getTileEntities()) {
                if (Tag.SHULKER_BOXES.isTagged(blockState.getType())) {
                    count++;
                }
            }
        } else {
            for (BlockState blockState : chunk.getTileEntities()) {
                if (blockState.getType() == materialToCount) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countAllBlocksOfTypeInChunk(Chunk chunk, Material material) {
        if (material == null) return 0;
        if (ConfigManager.isDebugEnabled() && LagXpert.getInstance() != null) {
            LagXpert.getInstance().getLogger().info("[LagXpert] AutoChunkScanTask: Performing slow block iteration count for " + material.name() + " in chunk " + chunk.getX() + "," + chunk.getZ());
        }
        int count = 0;
        int minHeight = chunk.getWorld().getMinHeight();
        int maxHeight = chunk.getWorld().getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minHeight; y < maxHeight; y++) {
                    if (chunk.getBlock(x, y, z).getType() == material) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private void fireChunkOverloadEvent(Chunk chunk, String causeSuffix) {
        ChunkOverloadEvent event = new ChunkOverloadEvent(chunk, causeSuffix);
        Bukkit.getPluginManager().callEvent(event);
    }
}
