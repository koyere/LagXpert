package me.koyere.lagxpert.tasks;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.api.events.ChunkOverloadEvent;
import me.koyere.lagxpert.system.AlertCooldownManager; // IMPORT ADDED for Alert Cooldown
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag; // For Material Tags like SHULKER_BOXES
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

/**
 * Scheduled task that periodically scans loaded chunks near players for elements
 * exceeding configured limits (e.g., mobs, hoppers, chests).
 * It can fire ChunkOverloadEvents and send warnings (with cooldowns)
 * to players in affected chunks based on detailed alert configurations.
 */
public class AutoChunkScanTask extends BukkitRunnable {

    // Replaced record ScannableElement with a final class for Java 11 compatibility
    private static final class ScannableElement {
        private final String displayName;
        private final Material material;
        private final Supplier<Integer> limitSupplier;
        private final String overloadCauseSuffix;
        private final CounterType counterType;
        private final Supplier<Boolean> nearLimitWarningToggle;

        public ScannableElement(String displayName, Material material, Supplier<Integer> limitSupplier, String overloadCauseSuffix, CounterType counterType, Supplier<Boolean> nearLimitToggle) {
            this.displayName = displayName;
            this.material = material;
            this.limitSupplier = limitSupplier;
            this.overloadCauseSuffix = overloadCauseSuffix;
            this.counterType = counterType;
            this.nearLimitWarningToggle = nearLimitToggle;
        }

        public ScannableElement(String displayName, Supplier<Integer> limitSupplier, String overloadCauseSuffix, Supplier<Boolean> nearLimitToggle) {
            this(displayName, null, limitSupplier, overloadCauseSuffix, CounterType.LIVING_ENTITY, nearLimitToggle);
        }

        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
        public Supplier<Integer> getLimitSupplier() { return limitSupplier; }
        public String getOverloadCauseSuffix() { return overloadCauseSuffix; }
        public CounterType getCounterType() { return counterType; }
        public Supplier<Boolean> getNearLimitWarningToggle() { return nearLimitWarningToggle; }
    }

    private enum CounterType {
        LIVING_ENTITY,
        TILE_ENTITY,
        BLOCK_ITERATION
    }

    private static final List<ScannableElement> elementsToScan = new ArrayList<>();

    static {
        elementsToScan.add(new ScannableElement("Mobs", ConfigManager::getMaxMobsPerChunk, "mobs", ConfigManager::shouldWarnOnMobsNearLimit));
        elementsToScan.add(new ScannableElement("Hoppers", Material.HOPPER, ConfigManager::getMaxHoppersPerChunk, "hoppers", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnHoppersNearLimit));
        elementsToScan.add(new ScannableElement("Chests", Material.CHEST, ConfigManager::getMaxChestsPerChunk, "chests", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnChestsNearLimit));
        elementsToScan.add(new ScannableElement("Trapped Chests", Material.TRAPPED_CHEST, ConfigManager::getMaxChestsPerChunk, "chests", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnChestsNearLimit));
        elementsToScan.add(new ScannableElement("Furnaces", Material.FURNACE, ConfigManager::getMaxFurnacesPerChunk, "furnaces", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnFurnacesNearLimit));
        elementsToScan.add(new ScannableElement("Blast Furnaces", Material.BLAST_FURNACE, ConfigManager::getMaxBlastFurnacesPerChunk, "blast_furnaces", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnBlastFurnacesNearLimit));
        elementsToScan.add(new ScannableElement("Smokers", Material.SMOKER, ConfigManager::getMaxSmokersPerChunk, "smokers", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnSmokersNearLimit));
        elementsToScan.add(new ScannableElement("Barrels", Material.BARREL, ConfigManager::getMaxBarrelsPerChunk, "barrels", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnBarrelsNearLimit));
        elementsToScan.add(new ScannableElement("Droppers", Material.DROPPER, ConfigManager::getMaxDroppersPerChunk, "droppers", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnDroppersNearLimit));
        elementsToScan.add(new ScannableElement("Dispensers", Material.DISPENSER, ConfigManager::getMaxDispensersPerChunk, "dispensers", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnDispensersNearLimit));
        elementsToScan.add(new ScannableElement("Shulker Boxes", Material.SHULKER_BOX, ConfigManager::getMaxShulkerBoxesPerChunk, "shulker_boxes", CounterType.TILE_ENTITY, ConfigManager::shouldWarnOnShulkerBoxesNearLimit));
        elementsToScan.add(new ScannableElement("TNT", Material.TNT, ConfigManager::getMaxTntPerChunk, "tnt", CounterType.BLOCK_ITERATION, ConfigManager::shouldWarnOnTntNearLimit));
        elementsToScan.add(new ScannableElement("Pistons", Material.PISTON, ConfigManager::getMaxPistonsPerChunk, "pistons", CounterType.BLOCK_ITERATION, ConfigManager::shouldWarnOnPistonsNearLimit));
        elementsToScan.add(new ScannableElement("Sticky Pistons", Material.STICKY_PISTON, ConfigManager::getMaxPistonsPerChunk, "pistons", CounterType.BLOCK_ITERATION, ConfigManager::shouldWarnOnPistonsNearLimit));
        elementsToScan.add(new ScannableElement("Observers", Material.OBSERVER, ConfigManager::getMaxObserversPerChunk, "observers", CounterType.BLOCK_ITERATION, ConfigManager::shouldWarnOnObserversNearLimit));
    }

    @Override
    public void run() {
        if (!ConfigManager.isAutoChunkScanModuleEnabled() && !ConfigManager.isDebugEnabled()) {
            return;
        }

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info("[LagXpert] AutoChunkScanTask: Starting scan cycle...");
        }

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

                List<Player> playersInThisChunk = getPlayersInChunk(currentChunk);
                boolean isChunkCurrentlyPopulatedByPlayers = !playersInThisChunk.isEmpty();

                StringBuilder overloadedElementsSummary = new StringBuilder();
                boolean chunkIsActuallyOverloaded = false;

                for (ScannableElement element : elementsToScan) {
                    int count;
                    switch (element.getCounterType()) {
                        case LIVING_ENTITY:
                            count = countLivingEntities(currentChunk);
                            break;
                        case TILE_ENTITY:
                            count = countTileEntitiesInChunk(currentChunk, element.getMaterial());
                            break;
                        case BLOCK_ITERATION:
                            count = countAllBlocksOfTypeInChunk(currentChunk, element.getMaterial());
                            break;
                        default:
                            count = 0;
                            if (LagXpert.getInstance() != null) {
                                LagXpert.getInstance().getLogger().warning("[LagXpert] AutoChunkScanTask: Unknown counter type: " + element.getCounterType());
                            }
                            break;
                    }

                    int limit = element.getLimitSupplier().get();
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
                            sendNearLimitWarning(playersInThisChunk, element, count, limit, currentChunk); // Pass currentChunk
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
        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info("[LagXpert] AutoChunkScanTask: Scan cycle finished.");
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