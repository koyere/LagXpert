package me.koyere.lagxpert.tasks;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.system.AbyssManager;
import me.koyere.lagxpert.system.AbyssTracker; // For bStats
import me.koyere.lagxpert.system.ActionLogger;
import me.koyere.lagxpert.system.EmergencyController;
import me.koyere.lagxpert.system.RecentlyBrokenBlocksTracker;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Periodically removes dropped items from the ground to reduce lag.
 * Supports exclusions and per-world filtering by fetching configuration from ConfigManager.
 * Integrates with Abyss recovery system and handles its own warning cycle.
 * Fixed exclusion logic to properly handle excluded items.
 * 
 * Enhanced with recently broken blocks tracking to provide grace periods
 * for items that players just broke but haven't collected yet.
 */
public class ItemCleanerTask extends BukkitRunnable {

    @Override
    public void run() {
        if (!ConfigManager.isItemCleanerModuleEnabled()) {
            return;
        }

        List<String> enabledWorlds = ConfigManager.getItemCleanerEnabledWorlds();
        boolean hasPlayersInEnabledWorlds = false;

        // Check if any enabled world has players before broadcasting
        for (Player player : Bukkit.getOnlinePlayers()) {
            World playerWorld = player.getWorld();
            boolean worldEnabled = enabledWorlds.stream().anyMatch(
                    w -> w.equalsIgnoreCase("all") || w.equalsIgnoreCase(playerWorld.getName()));
            if (worldEnabled) {
                hasPlayersInEnabledWorlds = true;
                break;
            }
        }

        if (ConfigManager.isItemCleanerWarningEnabled()) {
            int warningSeconds = ConfigManager.getItemCleanerWarningTimeSeconds();
            String warningMessageTemplate = ConfigManager.getItemCleanerWarningMessage();
            String messageContent = warningMessageTemplate.replace("{seconds}", String.valueOf(warningSeconds));
            String coloredMessage = MessageManager.color(messageContent);

            if (hasPlayersInEnabledWorlds) {
                // Only warn players in worlds where cleanup will actually happen
                for (Player player : Bukkit.getOnlinePlayers()) {
                    World playerWorld = player.getWorld();
                    boolean worldEnabled = enabledWorlds.stream().anyMatch(
                            w -> w.equalsIgnoreCase("all") || w.equalsIgnoreCase(playerWorld.getName()));
                    if (worldEnabled) {
                        player.sendMessage(coloredMessage);
                    }
                }
            } else if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info(
                        "[ItemCleanerTask] Skipping warning broadcast — no players in enabled worlds.");
            }

            // Schedule the actual cleanup to run after the warning period.
            me.koyere.lagxpert.utils.SchedulerWrapper.runTaskLater(
                    () -> performCleanupForAllWorlds(null, false, null),
                    warningSeconds * 20L);

        } else {
            // No warning configured, perform cleanup immediately.
            performCleanupForAllWorlds(null, false, null);
        }
    }

    /**
     * Performs the item cleanup process for all configured worlds.
     * This method is static and can be called by the scheduled task or manual commands.
     *
     * @param actor The player who initiated the cleanup, or null if automatic.
     * @return The total number of items removed.
     */
    private static void performCleanupForAllWorlds(Player actor,
                                                   boolean suppressReporting,
                                                   java.util.function.IntConsumer onComplete) {
        List<String> enabledWorlds = ConfigManager.getItemCleanerEnabledWorlds();

        if (ConfigManager.isDebugEnabled() && actor == null) {
            LagXpert.getInstance().getLogger().info("[LagXpert] ItemCleanerTask: Starting automatic cleanup cycle.");
        }

        List<World> targets = new java.util.ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            boolean isWorldEnabled = enabledWorlds.stream().anyMatch(
                    w -> w.equalsIgnoreCase("all") || w.equalsIgnoreCase(world.getName()));
            if (isWorldEnabled) {
                targets.add(world);
            }
        }

        // Swept per chunk rather than per world. A whole-world entity query cannot
        // be performed safely on Folia, where each region owns its own chunks.
        me.koyere.lagxpert.utils.RegionizedSweeper.sweep(
                targets, "item-cleanup",
                chunk -> clearItemsFromChunk(chunk, actor),
                result -> reportCleanupResult(result.getTotal(), actor, suppressReporting, onComplete));
    }

    /**
     * Broadcasts, records and reports the outcome of a completed sweep.
     *
     * Split out because the sweep is asynchronous: this runs on the main thread
     * once every chunk has been visited.
     */
    private static void reportCleanupResult(int totalItemsRemoved, Player actor,
                                            boolean suppressReporting,
                                            java.util.function.IntConsumer onComplete) {
        List<String> enabledWorlds = ConfigManager.getItemCleanerEnabledWorlds();

        if (totalItemsRemoved > 0 && actor == null && !suppressReporting) {
            String cleanedMessageTemplate = ConfigManager.getItemCleanerCleanedMessage();
            String messageContent = cleanedMessageTemplate.replace("{count}", String.valueOf(totalItemsRemoved));
            String coloredMessage = MessageManager.color(messageContent);

            // Only broadcast to players in enabled worlds
            for (Player p : Bukkit.getOnlinePlayers()) {
                boolean worldEnabled = enabledWorlds.stream().anyMatch(
                        w -> w.equalsIgnoreCase("all") || w.equalsIgnoreCase(p.getWorld().getName()));
                if (worldEnabled) {
                    p.sendMessage(coloredMessage);
                }
            }
        }

        if (totalItemsRemoved > 0) {
            AbyssTracker.itemAddedToAbyss(totalItemsRemoved);

            // Log corrective action to audit trail. Skipped during a forced run,
            // where the caller records a more detailed entry itself.
            if (!suppressReporting) {
                String triggeredBy = EmergencyController.getInstance().getCurrentState() !=
                        EmergencyController.ServerState.NORMAL ? "emergency" : "auto";
                ActionLogger.getInstance().log(
                        ActionLogger.ActionType.ITEM_CLEARED_BULK,
                        null, null,
                        "Automatic cleanup cycle",
                        totalItemsRemoved, triggeredBy, true, 0);
            }
        }

        if (ConfigManager.isDebugEnabled() && actor == null && totalItemsRemoved > 0) {
            LagXpert.getInstance().getLogger().info("[LagXpert] ItemCleanerTask: Automatic cleanup finished. Removed " + totalItemsRemoved + " items.");
        }

        if (onComplete != null) {
            onComplete.accept(totalItemsRemoved);
        }
    }

    /**
     * Clears dropped items from a single chunk.
     *
     * Chunk-scoped rather than world-scoped so the work can be dispatched to the
     * thread that owns the chunk, which is the only form that is correct on Folia.
     * Called from {@link me.koyere.lagxpert.utils.RegionizedSweeper}.
     *
     * @param chunk The chunk to clear items from.
     * @param actor The player who initiated the cleanup (for Abyss context), or null if automatic.
     * @return The number of items removed from this chunk.
     */
    private static int clearItemsFromChunk(org.bukkit.Chunk chunk, Player actor) {
        int itemsRemovedInWorld = 0;

        // Get excluded items and convert to uppercase for case-insensitive comparison
        Set<String> excludedItemsUpper = ConfigManager.getItemCleanerExcludedItems().stream()
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        for (org.bukkit.entity.Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Item)) {
                continue;
            }
            Item itemEntity = (Item) entity;
            try {
                ItemStack itemStack = itemEntity.getItemStack();

                // Skip invalid items
                if (itemStack == null || itemStack.getType().isAir()) {
                    continue;
                }

                // Check if item is excluded - FIXED: Compare material name properly
                String materialName = itemStack.getType().name().toUpperCase();
                if (excludedItemsUpper.contains(materialName)) {
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().info("[ItemCleanerTask] Skipping excluded item: " + materialName);
                    }
                    continue;
                }

                // Skip items with custom names (often important)
                if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().info("[ItemCleanerTask] Skipping named item: " + itemStack.getItemMeta().getDisplayName());
                    }
                    continue;
                }

                // Skip items with custom lore (often important)
                if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasLore()) {
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().info("[ItemCleanerTask] Skipping item with lore: " + materialName);
                    }
                    continue;
                }

                // Skip enchanted items (often valuable)
                if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasEnchants()) {
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().info("[ItemCleanerTask] Skipping enchanted item: " + materialName);
                    }
                    continue;
                }
                
                // ENHANCED: Check if this item is from a recently broken block (grace period)
                if (RecentlyBrokenBlocksTracker.hasRecentlyBrokenBlock(itemEntity.getLocation())) {
                    if (ConfigManager.isDebugEnabled()) {
                        RecentlyBrokenBlocksTracker.BrokenBlockInfo blockInfo = 
                            RecentlyBrokenBlocksTracker.getBrokenBlockInfo(itemEntity.getLocation());
                        if (blockInfo != null) {
                            long remainingMs = blockInfo.getRemainingGracePeriodMs();
                            LagXpert.getInstance().getLogger().info(
                                "[ItemCleanerTask] Skipping recently broken item: " + materialName + 
                                " (grace period: " + (remainingMs / 1000) + "s remaining)"
                            );
                        }
                    }
                    continue;
                }

                // Check if Abyss is active before attempting to add items.
                if (ConfigManager.isAbyssEnabled()) {
                    if (actor != null) {
                        AbyssManager.add(actor, itemStack);
                    } else {
                        // For automatic cleanup, try to find the item thrower
                        try {
                            if (itemEntity.getThrower() != null) {
                                Player thrower = Bukkit.getPlayer(itemEntity.getThrower());
                                if (thrower != null) {
                                    AbyssManager.add(thrower, itemStack);
                                } else {
                                    // If thrower is offline, add to abyss with offline player info
                                    AbyssManager.add(itemEntity);
                                }
                            } else {
                                // No thrower info available, add to general abyss
                                AbyssManager.add(itemEntity);
                            }
                        } catch (Exception e) {
                            // If there's any issue with abyss, just continue with removal
                            if (ConfigManager.isDebugEnabled()) {
                                LagXpert.getInstance().getLogger().warning("[ItemCleanerTask] Failed to add item to abyss: " + e.getMessage());
                            }
                        }
                    }
                }

                // Remove the item
                itemEntity.remove();
                itemsRemovedInWorld++;

                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().info("[ItemCleanerTask] Removed item: " + materialName + " (x" + itemStack.getAmount() + ")");
                }

            } catch (Exception e) {
                // Log the error but continue with other items
                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().warning("[ItemCleanerTask] Error processing item entity: " + e.getMessage());
                }
            }
        }

        return itemsRemovedInWorld;
    }

    // --- Static utility methods for manual cleanup commands ---

    /**
     * Manually triggers item cleanup for all configured worlds.
     * Intended for use by commands (e.g., /clearitems all).
     *
     * The sweep is spread across chunks and ticks, so the total is delivered
     * through the callback rather than returned.
     *
     * @param actor      The player executing the command, or null for console.
     * @param onComplete Receives the total removed, on the main thread. May be null.
     */
    public static void runManualCleanupAllWorlds(Player actor,
                                                 java.util.function.IntConsumer onComplete) {
        String actorName = (actor == null) ? "CONSOLE" : actor.getName();
        LagXpert.getInstance().getLogger().info("[LagXpert] Manual cleanup of all worlds initiated by " + actorName);

        // Manual runs report to the actor, so the automatic broadcast and the
        // routine audit entry are suppressed to avoid duplicate messaging.
        performCleanupForAllWorlds(actor, actor != null, onComplete);
    }

    /**
     * Runs an immediate cleanup outside the normal schedule, skipping the
     * countdown warning.
     *
     * Used by the EmergencyResponseCoordinator when the server state requests a
     * forced cleanup. The countdown is intentionally skipped: announcing a
     * ten-second warning while the server is already critical defeats the point.
     *
     * Callers are responsible for cooldown and re-entrancy control; this method
     * additionally suppresses the "cleaned {count}" broadcast so an emergency
     * sweep does not spam chat on top of the state-change alert.
     *
     * @param reason     Free-form reason recorded in the log line.
     * @param onComplete Receives the total removed, on the main thread. May be null.
     */
    public static void runForcedCleanup(String reason,
                                        java.util.function.IntConsumer onComplete) {
        LagXpert.getInstance().getLogger().info(
                "[ItemCleanerTask] Forced cleanup triggered (" + reason + ").");

        performCleanupForAllWorlds(null, true, onComplete);
    }

    /**
     * Manually triggers item cleanup for a specific world.
     * Intended for use by commands (e.g., /clearitems &lt;world_name&gt;).
     *
     * @param actor      The player executing the command, or null for console.
     * @param world      The specific world to clean items from.
     * @param onComplete Receives the total removed, on the main thread. May be null.
     */
    public static void runManualCleanupForWorld(Player actor, World world,
                                                java.util.function.IntConsumer onComplete) {
        if (world == null) {
            if (onComplete != null) {
                onComplete.accept(0);
            }
            return;
        }
        String actorName = (actor == null) ? "CONSOLE" : actor.getName();
        LagXpert.getInstance().getLogger().info("[LagXpert] Manual cleanup of world '" + world.getName() + "' initiated by " + actorName);

        me.koyere.lagxpert.utils.RegionizedSweeper.sweep(
                java.util.Collections.singletonList(world), "item-cleanup:" + world.getName(),
                chunk -> clearItemsFromChunk(chunk, actor),
                result -> {
                    int itemsRemoved = result.getTotal();
                    if (itemsRemoved > 0) {
                        AbyssTracker.itemAddedToAbyss(itemsRemoved);
                    }
                    if (onComplete != null) {
                        onComplete.accept(itemsRemoved);
                    }
                });
    }
}