package me.koyere.lagxpert.gui;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Main GUI class for LagXpert configuration management.
 * Provides an interactive interface for server administrators to modify
 * plugin settings without editing YAML files directly.
 */
public class ConfigGUI implements Listener {

    private static final String MAIN_MENU_TITLE = "LagXpert Configuration";
    private static final String ENTITY_LIMITS_TITLE = "Entity Limits Configuration";
    private static final String BLOCK_LIMITS_TITLE = "Block Limits Configuration";
    private static final String REDSTONE_SETTINGS_TITLE = "Redstone Settings";
    private static final String CLEANUP_SETTINGS_TITLE = "Cleanup Settings";
    private static final String ALERT_CONFIG_TITLE = "Alert Configuration";
    private static final String PERFORMANCE_MONITOR_TITLE = "Performance Monitor";
    private static final String ADVANCED_SETTINGS_TITLE = "Advanced Settings";

    // Track which GUI each player has open for event handling
    private static final Map<UUID, String> openGUIs = new HashMap<>();
    // Track pending changes before saving
    private static final Map<UUID, Map<String, Object>> pendingChanges = new HashMap<>();

    /**
     * Opens the main configuration menu for the specified player.
     *
     * @param player The player to open the GUI for
     */
    public static void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, MAIN_MENU_TITLE);

        // Entity Limits (Slot 10)
        ItemStack entityLimits = createMenuItem(
                Material.ZOMBIE_SPAWN_EGG,
                "&6Entity Limits",
                Arrays.asList(
                        "&7Configure mob limits per chunk",
                        "&7Current: &e" + getEffectiveValue(player, "mobs-per-chunk", ConfigManager.getMaxMobsPerChunk()) + " mobs/chunk",
                        "",
                        "&eClick to configure"
                )
        );
        gui.setItem(10, entityLimits);

        // Block Limits (Slot 11)
        ItemStack blockLimits = createMenuItem(
                Material.HOPPER,
                "&6Block Limits",
                Arrays.asList(
                        "&7Configure storage block limits",
                        "&7Hoppers: &e" + getEffectiveValue(player, "hoppers-per-chunk", ConfigManager.getMaxHoppersPerChunk()) + "/chunk",
                        "&7Chests: &e" + getEffectiveValue(player, "chests-per-chunk", ConfigManager.getMaxChestsPerChunk()) + "/chunk",
                        "",
                        "&eClick to configure"
                )
        );
        gui.setItem(11, blockLimits);

        // Redstone Settings (Slot 12)
        ItemStack redstoneSettings = createMenuItem(
                Material.REDSTONE,
                "&6Redstone Settings",
                Arrays.asList(
                        "&7Configure redstone control",
                        "&7Active ticks: &e" + getEffectiveValue(player, "redstone-active-ticks", ConfigManager.getRedstoneActiveTicks()),
                        "&7Status: " + (getEffectiveBooleanValue(player, "redstone-module-enabled", ConfigManager.isRedstoneControlModuleEnabled()) ? "&aEnabled" : "&cDisabled"),
                        "",
                        "&eClick to configure"
                )
        );
        gui.setItem(12, redstoneSettings);

        // Cleanup Settings (Slot 13)
        ItemStack cleanupSettings = createMenuItem(
                Material.DIAMOND_SWORD,
                "&6Cleanup Settings",
                Arrays.asList(
                        "&7Configure item and entity cleanup",
                        "&7Item Cleaner: " + (getEffectiveBooleanValue(player, "item-cleaner-module-enabled", ConfigManager.isItemCleanerModuleEnabled()) ? "&aEnabled" : "&cDisabled"),
                        "&7Entity Cleanup: " + (getEffectiveBooleanValue(player, "entity-cleanup-module-enabled", ConfigManager.isEntityCleanupModuleEnabled()) ? "&aEnabled" : "&cDisabled"),
                        "",
                        "&eClick to configure"
                )
        );
        gui.setItem(13, cleanupSettings);

        // Alert Configuration (Slot 14)
        ItemStack alertConfig = createMenuItem(
                Material.BELL,
                "&6Alert Configuration",
                Arrays.asList(
                        "&7Configure alert settings",
                        "&7Alerts: " + (getEffectiveBooleanValue(player, "alerts-module-enabled", ConfigManager.isAlertsModuleEnabled()) ? "&aEnabled" : "&cDisabled"),
                        "&7Cooldown: &e" + getEffectiveValue(player, "alert-cooldown-seconds", ConfigManager.getAlertCooldownDefaultSeconds()) + "s",
                        "",
                        "&eClick to configure"
                )
        );
        gui.setItem(14, alertConfig);

        // Performance Monitor (Slot 15)
        ItemStack performanceMonitor = createMenuItem(
                Material.COMPASS,
                "&6Performance Monitor",
                Arrays.asList(
                        "&7Configure monitoring settings",
                        "&7TPS Monitoring: " + (getEffectiveBooleanValue(player, "tps-monitoring-enabled", ConfigManager.isTPSMonitoringEnabled()) ? "&aEnabled" : "&cDisabled"),
                        "&7Memory Monitoring: " + (getEffectiveBooleanValue(player, "memory-monitoring-enabled", ConfigManager.isMemoryMonitoringEnabled()) ? "&aEnabled" : "&cDisabled"),
                        "",
                        "&eClick to configure"
                )
        );
        gui.setItem(15, performanceMonitor);

        // Advanced Settings (Slot 16)
        ItemStack advancedSettings = createMenuItem(
                Material.COMMAND_BLOCK,
                "&6Advanced Settings",
                Arrays.asList(
                        "&7Configure advanced options",
                        "&7Debug Mode: " + (getEffectiveBooleanValue(player, "debug-enabled", ConfigManager.isDebugEnabled()) ? "&aEnabled" : "&cDisabled"),
                        "&7Chunk Management: " + (getEffectiveBooleanValue(player, "chunk-management-enabled", ConfigManager.isChunkManagementModuleEnabled()) ? "&aEnabled" : "&cDisabled"),
                        "",
                        "&eClick to configure"
                )
        );
        gui.setItem(16, advancedSettings);

        // Add decorative borders
        addBorders(gui);

        // Save & Close button (Slot 22)
        ItemStack saveClose = createMenuItem(
                Material.EMERALD_BLOCK,
                "&aSave & Close",
                Arrays.asList(
                        "&7Save all pending changes",
                        "&7and close the configuration menu",
                        "",
                        "&eClick to save and exit"
                )
        );
        gui.setItem(22, saveClose);

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), "main_menu");

        // Initialize pending changes map for this player
        pendingChanges.putIfAbsent(player.getUniqueId(), new HashMap<>());
    }

    /**
     * Opens the entity limits configuration submenu.
     *
     * @param player The player to open the GUI for
     */
    public static void openEntityLimitsMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ENTITY_LIMITS_TITLE);

        int currentLimit = getEffectiveValue(player, "mobs-per-chunk", ConfigManager.getMaxMobsPerChunk());

        // Current mob limit display
        ItemStack currentLimitItem = createMenuItem(
                Material.ZOMBIE_SPAWN_EGG,
                "&6Current Mob Limit",
                Arrays.asList(
                        "&7Mobs per chunk: &e" + currentLimit,
                        "",
                        "&7This limit controls how many",
                        "&7living entities can exist in",
                        "&7a single chunk before new",
                        "&7spawns are prevented.",
                        "",
                        hasPendingChanges(player) ? "&e⚠ &7You have unsaved changes" : "&a✓ &7No pending changes"
                )
        );
        gui.setItem(13, currentLimitItem);

        // Decrease mob limit
        ItemStack decrease = createMenuItem(
                Material.RED_WOOL,
                "&cDecrease Limit (-5)",
                Arrays.asList(
                        "&7Current: &e" + currentLimit,
                        "&7New: &e" + Math.max(1, currentLimit - 5),
                        "",
                        "&eClick to decrease by 5",
                        "&eShift+Click to decrease by 1"
                )
        );
        gui.setItem(11, decrease);

        // Increase mob limit
        ItemStack increase = createMenuItem(
                Material.GREEN_WOOL,
                "&aIncrease Limit (+5)",
                Arrays.asList(
                        "&7Current: &e" + currentLimit,
                        "&7New: &e" + Math.min(500, currentLimit + 5),
                        "",
                        "&eClick to increase by 5",
                        "&eShift+Click to increase by 1"
                )
        );
        gui.setItem(15, increase);

        // Module toggle
        boolean moduleEnabled = getEffectiveBooleanValue(player, "mobs-module-enabled", ConfigManager.isMobsModuleEnabled());
        ItemStack moduleToggle = createMenuItem(
                moduleEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "&6Module Status",
                Arrays.asList(
                        "&7Mob limiting is currently:",
                        moduleEnabled ? "&aEnabled" : "&cDisabled",
                        "",
                        "&eClick to toggle"
                )
        );
        gui.setItem(22, moduleToggle);

        // Back button
        addBackButton(gui, 18);
        addBorders(gui);

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), "entity_limits");
    }

    /**
     * Opens the block limits configuration submenu.
     *
     * @param player The player to open the GUI for
     */
    public static void openBlockLimitsMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, BLOCK_LIMITS_TITLE);

        // Row 1: Main storage blocks
        addBlockLimitItem(gui, 10, Material.HOPPER, "Hoppers",
                getEffectiveValue(player, "hoppers-per-chunk", ConfigManager.getMaxHoppersPerChunk()), "hoppers-per-chunk");

        addBlockLimitItem(gui, 11, Material.CHEST, "Chests",
                getEffectiveValue(player, "chests-per-chunk", ConfigManager.getMaxChestsPerChunk()), "chests-per-chunk");

        addBlockLimitItem(gui, 12, Material.FURNACE, "Furnaces",
                getEffectiveValue(player, "furnaces-per-chunk", ConfigManager.getMaxFurnacesPerChunk()), "furnaces-per-chunk");

        addBlockLimitItem(gui, 13, Material.BLAST_FURNACE, "Blast Furnaces",
                getEffectiveValue(player, "blast-furnaces-per-chunk", ConfigManager.getMaxBlastFurnacesPerChunk()), "blast-furnaces-per-chunk");

        addBlockLimitItem(gui, 14, Material.SMOKER, "Smokers",
                getEffectiveValue(player, "smokers-per-chunk", ConfigManager.getMaxSmokersPerChunk()), "smokers-per-chunk");

        addBlockLimitItem(gui, 15, Material.BARREL, "Barrels",
                getEffectiveValue(player, "barrels-per-chunk", ConfigManager.getMaxBarrelsPerChunk()), "barrels-per-chunk");

        addBlockLimitItem(gui, 16, Material.DROPPER, "Droppers",
                getEffectiveValue(player, "droppers-per-chunk", ConfigManager.getMaxDroppersPerChunk()), "droppers-per-chunk");

        // Row 2: Additional blocks
        addBlockLimitItem(gui, 19, Material.DISPENSER, "Dispensers",
                getEffectiveValue(player, "dispensers-per-chunk", ConfigManager.getMaxDispensersPerChunk()), "dispensers-per-chunk");

        addBlockLimitItem(gui, 20, Material.SHULKER_BOX, "Shulker Boxes",
                getEffectiveValue(player, "shulker-boxes-per-chunk", ConfigManager.getMaxShulkerBoxesPerChunk()), "shulker-boxes-per-chunk");

        addBlockLimitItem(gui, 21, Material.TNT, "TNT",
                getEffectiveValue(player, "tnt-per-chunk", ConfigManager.getMaxTntPerChunk()), "tnt-per-chunk");

        addBlockLimitItem(gui, 22, Material.PISTON, "Pistons",
                getEffectiveValue(player, "pistons-per-chunk", ConfigManager.getMaxPistonsPerChunk()), "pistons-per-chunk");

        addBlockLimitItem(gui, 23, Material.OBSERVER, "Observers",
                getEffectiveValue(player, "observers-per-chunk", ConfigManager.getMaxObserversPerChunk()), "observers-per-chunk");

        // Module toggle
        boolean moduleEnabled = getEffectiveBooleanValue(player, "storage-module-enabled", ConfigManager.isStorageModuleEnabled());
        ItemStack moduleToggle = createMenuItem(
                moduleEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "&6Storage Module Status",
                Arrays.asList(
                        "&7Storage limiting is currently:",
                        moduleEnabled ? "&aEnabled" : "&cDisabled",
                        "",
                        "&eClick to toggle"
                )
        );
        gui.setItem(31, moduleToggle);

        // Pending changes indicator
        if (hasPendingChanges(player)) {
            ItemStack changesIndicator = createMenuItem(
                    Material.YELLOW_WOOL,
                    "&eUnsaved Changes",
                    Arrays.asList(
                            "&7You have &e" + getPendingChangesCount(player) + " &7unsaved changes",
                            "&7Go back to main menu to save",
                            "",
                            "&eClick to view details"
                    )
            );
            gui.setItem(40, changesIndicator);
        }

        // Back button
        addBackButton(gui, 45);
        addBorders(gui);

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), "block_limits");
    }

    /**
     * Opens the redstone settings configuration submenu.
     *
     * @param player The player to open the GUI for
     */
    public static void openRedstoneSettingsMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, REDSTONE_SETTINGS_TITLE);

        boolean moduleEnabled = getEffectiveBooleanValue(player, "redstone-module-enabled", ConfigManager.isRedstoneControlModuleEnabled());
        int activeTicks = getEffectiveValue(player, "redstone-active-ticks", ConfigManager.getRedstoneActiveTicks());

        // Module status
        ItemStack moduleStatus = createMenuItem(
                moduleEnabled ? Material.REDSTONE : Material.BARRIER,
                "&6Redstone Control Module",
                Arrays.asList(
                        "&7Status: " + (moduleEnabled ? "&aEnabled" : "&cDisabled"),
                        "",
                        "&7Controls redstone activity to",
                        "&7prevent lag from excessive circuits",
                        "",
                        "&eClick to toggle"
                )
        );
        gui.setItem(13, moduleStatus);

        // Active ticks setting
        ItemStack activeTicksItem = createMenuItem(
                Material.CLOCK,
                "&6Active Ticks Limit",
                Arrays.asList(
                        "&7Current: &e" + activeTicks + " ticks",
                        "",
                        "&7Maximum ticks a redstone",
                        "&7component can stay active",
                        "&7before being disabled",
                        "",
                        "&eLeft click: +10",
                        "&eRight click: -10",
                        "&eShift+Left: +50",
                        "&eShift+Right: -50"
                )
        );
        gui.setItem(11, activeTicksItem);

        // Back button and borders
        addBackButton(gui, 18);
        addBorders(gui);

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), "redstone_settings");
    }

    /**
     * Opens the cleanup settings configuration submenu.
     *
     * @param player The player to open the GUI for
     */
    public static void openCleanupSettingsMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, CLEANUP_SETTINGS_TITLE);

        boolean itemCleanerEnabled = getEffectiveBooleanValue(player, "item-cleaner-module-enabled", ConfigManager.isItemCleanerModuleEnabled());
        boolean entityCleanupEnabled = getEffectiveBooleanValue(player, "entity-cleanup-module-enabled", ConfigManager.isEntityCleanupModuleEnabled());

        // Item Cleaner Module
        ItemStack itemCleaner = createMenuItem(
                itemCleanerEnabled ? Material.DIAMOND_SWORD : Material.WOODEN_SWORD,
                "&6Item Cleaner Module",
                Arrays.asList(
                        "&7Status: " + (itemCleanerEnabled ? "&aEnabled" : "&cDisabled"),
                        "&7Interval: &e" + (ConfigManager.getItemCleanerIntervalTicks() / 20) + "s",
                        "",
                        "&7Automatically cleans dropped",
                        "&7items from the ground",
                        "",
                        "&eClick to toggle"
                )
        );
        gui.setItem(11, itemCleaner);

        // Entity Cleanup Module
        ItemStack entityCleanup = createMenuItem(
                entityCleanupEnabled ? Material.IRON_SWORD : Material.STONE_SWORD,
                "&6Entity Cleanup Module",
                Arrays.asList(
                        "&7Status: " + (entityCleanupEnabled ? "&aEnabled" : "&cDisabled"),
                        "&7Interval: &e" + (ConfigManager.getEntityCleanupIntervalTicks() / 20) + "s",
                        "",
                        "&7Removes invalid, duplicate,",
                        "&7and abandoned entities",
                        "",
                        "&eClick to toggle"
                )
        );
        gui.setItem(15, entityCleanup);

        // Back button and borders
        addBackButton(gui, 18);
        addBorders(gui);

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), "cleanup_settings");
    }

    /**
     * Opens the alert configuration submenu.
     *
     * @param player The player to open the GUI for
     */
    public static void openAlertConfigMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ALERT_CONFIG_TITLE);

        boolean alertsEnabled = getEffectiveBooleanValue(player, "alerts-module-enabled", ConfigManager.isAlertsModuleEnabled());
        int cooldownSeconds = getEffectiveValue(player, "alert-cooldown-seconds", ConfigManager.getAlertCooldownDefaultSeconds());

        // Alerts Module Status
        ItemStack alertsModule = createMenuItem(
                alertsEnabled ? Material.BELL : Material.IRON_BLOCK,
                "&6Alert System",
                Arrays.asList(
                        "&7Status: " + (alertsEnabled ? "&aEnabled" : "&cDisabled"),
                        "&7Cooldown: &e" + cooldownSeconds + "s",
                        "",
                        "&7Controls all limit alerts",
                        "&7and warning messages",
                        "",
                        "&eClick to toggle"
                )
        );
        gui.setItem(13, alertsModule);

        // Cooldown adjustment
        ItemStack cooldownItem = createMenuItem(
                Material.CLOCK,
                "&6Alert Cooldown",
                Arrays.asList(
                        "&7Current: &e" + cooldownSeconds + " seconds",
                        "",
                        "&7Time between alert messages",
                        "&7for the same chunk/player",
                        "",
                        "&eLeft click: +5s",
                        "&eRight click: -5s",
                        "&eShift+Left: +30s",
                        "&eShift+Right: -30s"
                )
        );
        gui.setItem(11, cooldownItem);

        // Back button and borders
        addBackButton(gui, 18);
        addBorders(gui);

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), "alert_config");
    }

    /**
     * Opens the performance monitor configuration submenu.
     *
     * @param player The player to open the GUI for
     */
    public static void openPerformanceMonitorMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, PERFORMANCE_MONITOR_TITLE);

        boolean monitoringEnabled = getEffectiveBooleanValue(player, "monitoring-module-enabled", ConfigManager.isMonitoringModuleEnabled());
        boolean tpsEnabled = getEffectiveBooleanValue(player, "tps-monitoring-enabled", ConfigManager.isTPSMonitoringEnabled());
        boolean memoryEnabled = getEffectiveBooleanValue(player, "memory-monitoring-enabled", ConfigManager.isMemoryMonitoringEnabled());

        // Monitoring Module Status
        ItemStack monitoringModule = createMenuItem(
                monitoringEnabled ? Material.COMPASS : Material.BARRIER,
                "&6Performance Monitoring",
                Arrays.asList(
                        "&7Status: " + (monitoringEnabled ? "&aEnabled" : "&cDisabled"),
                        "",
                        "&7Master toggle for all",
                        "&7performance monitoring features",
                        "",
                        "&eClick to toggle"
                )
        );
        gui.setItem(13, monitoringModule);

        // TPS Monitoring
        ItemStack tpsMonitoring = createMenuItem(
                tpsEnabled ? Material.EMERALD : Material.REDSTONE,
                "&6TPS Monitoring",
                Arrays.asList(
                        "&7Status: " + (tpsEnabled ? "&aEnabled" : "&cDisabled"),
                        "&7Threshold: &e" + ConfigManager.getTPSWarningThreshold() + " TPS",
                        "",
                        "&7Tracks server tick rate",
                        "&7and lag spikes",
                        "",
                        "&eClick to toggle"
                )
        );
        gui.setItem(11, tpsMonitoring);

        // Memory Monitoring
        ItemStack memoryMonitoring = createMenuItem(
                memoryEnabled ? Material.GOLD_INGOT : Material.IRON_INGOT,
                "&6Memory Monitoring",
                Arrays.asList(
                        "&7Status: " + (memoryEnabled ? "&aEnabled" : "&cDisabled"),
                        "&7Threshold: &e" + ConfigManager.getMemoryWarningThreshold() + "%",
                        "",
                        "&7Tracks server memory usage",
                        "&7and garbage collection",
                        "",
                        "&eClick to toggle"
                )
        );
        gui.setItem(15, memoryMonitoring);

        // Back button and borders
        addBackButton(gui, 18);
        addBorders(gui);

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), "performance_monitor");
    }

    /**
     * Opens the advanced settings configuration submenu.
     *
     * @param player The player to open the GUI for
     */
    public static void openAdvancedSettingsMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ADVANCED_SETTINGS_TITLE);

        boolean debugEnabled = getEffectiveBooleanValue(player, "debug-enabled", ConfigManager.isDebugEnabled());
        boolean chunkManagementEnabled = getEffectiveBooleanValue(player, "chunk-management-enabled", ConfigManager.isChunkManagementModuleEnabled());

        // Debug Mode
        ItemStack debugMode = createMenuItem(
                debugEnabled ? Material.REDSTONE_TORCH : Material.TORCH,
                "&6Debug Mode",
                Arrays.asList(
                        "&7Status: " + (debugEnabled ? "&aEnabled" : "&cDisabled"),
                        "",
                        "&7Enables detailed logging",
                        "&7for troubleshooting",
                        "",
                        "&c⚠ &7Can increase log file size",
                        "",
                        "&eClick to toggle"
                )
        );
        gui.setItem(11, debugMode);

        // Chunk Management
        ItemStack chunkManagement = createMenuItem(
                chunkManagementEnabled ? Material.GRASS_BLOCK : Material.DIRT,
                "&6Chunk Management",
                Arrays.asList(
                        "&7Status: " + (chunkManagementEnabled ? "&aEnabled" : "&cDisabled"),
                        "",
                        "&7Intelligent chunk loading",
                        "&7and unloading system",
                        "",
                        "&eClick to toggle"
                )
        );
        gui.setItem(15, chunkManagement);

        // Back button and borders
        addBackButton(gui, 18);
        addBorders(gui);

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), "advanced_settings");
    }

    /**
     * Handles inventory click events for all GUI interactions.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();

        if (!openGUIs.containsKey(playerId)) return;

        event.setCancelled(true); // Prevent item pickup/movement

        String currentGUI = openGUIs.get(playerId);
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        switch (currentGUI) {
            case "main_menu":
                handleMainMenuClick(player, event.getSlot(), clickedItem);
                break;
            case "entity_limits":
                handleEntityLimitsClick(player, event.getSlot(), event.getClick());
                break;
            case "block_limits":
                handleBlockLimitsClick(player, event.getSlot(), event.getClick());
                break;
            case "redstone_settings":
                handleRedstoneSettingsClick(player, event.getSlot(), event.getClick());
                break;
            case "cleanup_settings":
                handleCleanupSettingsClick(player, event.getSlot(), event.getClick());
                break;
            case "alert_config":
                handleAlertConfigClick(player, event.getSlot(), event.getClick());
                break;
            case "performance_monitor":
                handlePerformanceMonitorClick(player, event.getSlot(), event.getClick());
                break;
            case "advanced_settings":
                handleAdvancedSettingsClick(player, event.getSlot(), event.getClick());
                break;
        }
    }

    /**
     * Handles inventory close events to clean up tracking.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if this player had a GUI open
        if (openGUIs.containsKey(playerId)) {
            // Clean up local tracking
            openGUIs.remove(playerId);

            // Notify GUIManager to clean up session tracking
            GUIManager guiManager = GUIManager.getInstance();
            if (guiManager != null) {
                // Force end the session in GUIManager
                guiManager.forceEndSession(player);
            }
        }

        // Note: Don't clear pending changes here - they should persist until saved or explicitly cleared
    }

    /**
     * Handles clicks in the main menu.
     */
    private void handleMainMenuClick(Player player, int slot, ItemStack item) {
        switch (slot) {
            case 10: // Entity Limits
                openEntityLimitsMenu(player);
                break;
            case 11: // Block Limits
                openBlockLimitsMenu(player);
                break;
            case 12: // Redstone Settings
                openRedstoneSettingsMenu(player);
                break;
            case 13: // Cleanup Settings
                openCleanupSettingsMenu(player);
                break;
            case 14: // Alert Configuration
                openAlertConfigMenu(player);
                break;
            case 15: // Performance Monitor
                openPerformanceMonitorMenu(player);
                break;
            case 16: // Advanced Settings
                openAdvancedSettingsMenu(player);
                break;
            case 22: // Save & Close
                savePendingChanges(player);
                player.closeInventory();
                break;
        }
    }

    /**
     * Handles clicks in the entity limits menu.
     */
    private void handleEntityLimitsClick(Player player, int slot, ClickType clickType) {
        switch (slot) {
            case 11: // Decrease mob limit
                int currentMobs = getEffectiveValue(player, "mobs-per-chunk", ConfigManager.getMaxMobsPerChunk());
                int decreaseAmount = clickType == ClickType.SHIFT_LEFT ? 1 : 5;
                int newMobLimit = Math.max(1, currentMobs - decreaseAmount);

                setPendingChange(player, "mobs-per-chunk", newMobLimit);
                player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.pending-change",
                        Map.of("setting", "Mob Limit", "value", newMobLimit)));

                openEntityLimitsMenu(player);
                break;

            case 15: // Increase mob limit
                int currentMobs2 = getEffectiveValue(player, "mobs-per-chunk", ConfigManager.getMaxMobsPerChunk());
                int increaseAmount = clickType == ClickType.SHIFT_LEFT ? 1 : 5;
                int newMobLimit2 = Math.min(500, currentMobs2 + increaseAmount); // Cap at 500

                setPendingChange(player, "mobs-per-chunk", newMobLimit2);
                player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.pending-change",
                        Map.of("setting", "Mob Limit", "value", newMobLimit2)));

                openEntityLimitsMenu(player);
                break;

            case 22: // Module toggle
                boolean currentEnabled = getEffectiveBooleanValue(player, "mobs-module-enabled", ConfigManager.isMobsModuleEnabled());
                setPendingChange(player, "mobs-module-enabled", !currentEnabled);
                player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.module-toggled",
                        Map.of("module", "Mobs", "status", !currentEnabled ? "Enabled" : "Disabled")));

                openEntityLimitsMenu(player);
                break;

            case 18: // Back button
                openMainMenu(player);
                break;
        }
    }

    /**
     * Handles clicks in the block limits menu.
     */
    private void handleBlockLimitsClick(Player player, int slot, ClickType clickType) {
        // Handle back button
        if (slot == 45) {
            openMainMenu(player);
            return;
        }

        // Handle module toggle
        if (slot == 31) {
            boolean currentEnabled = getEffectiveBooleanValue(player, "storage-module-enabled", ConfigManager.isStorageModuleEnabled());
            setPendingChange(player, "storage-module-enabled", !currentEnabled);
            player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.module-toggled",
                    Map.of("module", "Storage", "status", !currentEnabled ? "Enabled" : "Disabled")));
            openBlockLimitsMenu(player);
            return;
        }

        // Handle pending changes view
        if (slot == 40) {
            showPendingChangesDetails(player);
            return;
        }

        // Handle block limit modifications
        String configKey = getBlockConfigKeyForSlot(slot);
        if (configKey != null) {
            handleBlockLimitAdjustment(player, configKey, clickType);
            openBlockLimitsMenu(player);
        }
    }

    /**
     * Handles clicks in the redstone settings menu.
     */
    private void handleRedstoneSettingsClick(Player player, int slot, ClickType clickType) {
        switch (slot) {
            case 11: // Active ticks adjustment
                int currentTicks = getEffectiveValue(player, "redstone-active-ticks", ConfigManager.getRedstoneActiveTicks());
                int adjustment = 0;

                if (clickType == ClickType.LEFT) {
                    adjustment = 10;
                } else if (clickType == ClickType.RIGHT) {
                    adjustment = -10;
                } else if (clickType == ClickType.SHIFT_LEFT) {
                    adjustment = 50;
                } else if (clickType == ClickType.SHIFT_RIGHT) {
                    adjustment = -50;
                }

                if (adjustment != 0) {
                    int newTicks = Math.max(10, Math.min(1000, currentTicks + adjustment));
                    setPendingChange(player, "redstone-active-ticks", newTicks);
                    player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.pending-change",
                            Map.of("setting", "Redstone Active Ticks", "value", newTicks)));
                    openRedstoneSettingsMenu(player);
                }
                break;

            case 13: // Module toggle
                boolean currentEnabled = getEffectiveBooleanValue(player, "redstone-module-enabled", ConfigManager.isRedstoneControlModuleEnabled());
                setPendingChange(player, "redstone-module-enabled", !currentEnabled);
                player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.module-toggled",
                        Map.of("module", "Redstone", "status", !currentEnabled ? "Enabled" : "Disabled")));
                openRedstoneSettingsMenu(player);
                break;

            case 18: // Back button
                openMainMenu(player);
                break;
        }
    }

    /**
     * Handles clicks in the cleanup settings menu.
     */
    private void handleCleanupSettingsClick(Player player, int slot, ClickType clickType) {
        switch (slot) {
            case 11: // Item Cleaner toggle
                boolean itemCleanerEnabled = getEffectiveBooleanValue(player, "item-cleaner-module-enabled", ConfigManager.isItemCleanerModuleEnabled());
                setPendingChange(player, "item-cleaner-module-enabled", !itemCleanerEnabled);
                player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.module-toggled",
                        Map.of("module", "Item Cleaner", "status", !itemCleanerEnabled ? "Enabled" : "Disabled")));
                openCleanupSettingsMenu(player);
                break;

            case 15: // Entity Cleanup toggle
                boolean entityCleanupEnabled = getEffectiveBooleanValue(player, "entity-cleanup-module-enabled", ConfigManager.isEntityCleanupModuleEnabled());
                setPendingChange(player, "entity-cleanup-module-enabled", !entityCleanupEnabled);
                player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.module-toggled",
                        Map.of("module", "Entity Cleanup", "status", !entityCleanupEnabled ? "Enabled" : "Disabled")));
                openCleanupSettingsMenu(player);
                break;

            case 18: // Back button
                openMainMenu(player);
                break;
        }
    }

    /**
     * Handles clicks in the alert configuration menu.
     */
    private void handleAlertConfigClick(Player player, int slot, ClickType clickType) {
        switch (slot) {
            case 11: // Cooldown adjustment
                int currentCooldown = getEffectiveValue(player, "alert-cooldown-seconds", ConfigManager.getAlertCooldownDefaultSeconds());
                int adjustment = 0;

                if (clickType == ClickType.LEFT) {
                    adjustment = 5;
                } else if (clickType == ClickType.RIGHT) {
                    adjustment = -5;
                } else if (clickType == ClickType.SHIFT_LEFT) {
                    adjustment = 30;
                } else if (clickType == ClickType.SHIFT_RIGHT) {
                    adjustment = -30;
                }

                if (adjustment != 0) {
                    int newCooldown = Math.max(1, Math.min(300, currentCooldown + adjustment));
                    setPendingChange(player, "alert-cooldown-seconds", newCooldown);
                    player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.pending-change",
                            Map.of("setting", "Alert Cooldown", "value", newCooldown + "s")));
                    openAlertConfigMenu(player);
                }
                break;

            case 13: // Alerts module toggle
                boolean alertsEnabled = getEffectiveBooleanValue(player, "alerts-module-enabled", ConfigManager.isAlertsModuleEnabled());
                setPendingChange(player, "alerts-module-enabled", !alertsEnabled);
                player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.module-toggled",
                        Map.of("module", "Alerts", "status", !alertsEnabled ? "Enabled" : "Disabled")));
                openAlertConfigMenu(player);
                break;

            case 18: // Back button
                openMainMenu(player);
                break;
        }
    }

    /**
     * Handles clicks in the performance monitor menu.
     */
    private void handlePerformanceMonitorClick(Player player, int slot, ClickType clickType) {
        switch (slot) {
            case 11: // TPS Monitoring toggle
                boolean tpsEnabled = getEffectiveBooleanValue(player, "tps-monitoring-enabled", ConfigManager.isTPSMonitoringEnabled());
                setPendingChange(player, "tps-monitoring-enabled", !tpsEnabled);
                player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.module-toggled",
                        Map.of("module", "TPS Monitoring", "status", !tpsEnabled ? "Enabled" : "Disabled")));
                openPerformanceMonitorMenu(player);
                break;

            case 13: // Main monitoring module toggle
                boolean monitoringEnabled = getEffectiveBooleanValue(player, "monitoring-module-enabled", ConfigManager.isMonitoringModuleEnabled());
                setPendingChange(player, "monitoring-module-enabled", !monitoringEnabled);
                player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.module-toggled",
                        Map.of("module", "Performance Monitoring", "status", !monitoringEnabled ? "Enabled" : "Disabled")));
                openPerformanceMonitorMenu(player);
                break;

            case 15: // Memory Monitoring toggle
                boolean memoryEnabled = getEffectiveBooleanValue(player, "memory-monitoring-enabled", ConfigManager.isMemoryMonitoringEnabled());
                setPendingChange(player, "memory-monitoring-enabled", !memoryEnabled);
                player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.module-toggled",
                        Map.of("module", "Memory Monitoring", "status", !memoryEnabled ? "Enabled" : "Disabled")));
                openPerformanceMonitorMenu(player);
                break;

            case 18: // Back button
                openMainMenu(player);
                break;
        }
    }

    /**
     * Handles clicks in the advanced settings menu.
     */
    private void handleAdvancedSettingsClick(Player player, int slot, ClickType clickType) {
        switch (slot) {
            case 11: // Debug mode toggle
                boolean debugEnabled = getEffectiveBooleanValue(player, "debug-enabled", ConfigManager.isDebugEnabled());
                setPendingChange(player, "debug-enabled", !debugEnabled);
                player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.module-toggled",
                        Map.of("module", "Debug Mode", "status", !debugEnabled ? "Enabled" : "Disabled")));
                openAdvancedSettingsMenu(player);
                break;

            case 15: // Chunk management toggle
                boolean chunkManagementEnabled = getEffectiveBooleanValue(player, "chunk-management-enabled", ConfigManager.isChunkManagementModuleEnabled());
                setPendingChange(player, "chunk-management-enabled", !chunkManagementEnabled);
                player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.module-toggled",
                        Map.of("module", "Chunk Management", "status", !chunkManagementEnabled ? "Enabled" : "Disabled")));
                openAdvancedSettingsMenu(player);
                break;

            case 18: // Back button
                openMainMenu(player);
                break;
        }
    }

    /**
     * Gets the block configuration key for a given slot in the block limits menu.
     */
    private String getBlockConfigKeyForSlot(int slot) {
        switch (slot) {
            case 10: return "hoppers-per-chunk";
            case 11: return "chests-per-chunk";
            case 12: return "furnaces-per-chunk";
            case 13: return "blast-furnaces-per-chunk";
            case 14: return "smokers-per-chunk";
            case 15: return "barrels-per-chunk";
            case 16: return "droppers-per-chunk";
            case 19: return "dispensers-per-chunk";
            case 20: return "shulker-boxes-per-chunk";
            case 21: return "tnt-per-chunk";
            case 22: return "pistons-per-chunk";
            case 23: return "observers-per-chunk";
            default: return null;
        }
    }

    /**
     * Handles block limit adjustments based on click type.
     */
    private void handleBlockLimitAdjustment(Player player, String configKey, ClickType clickType) {
        int currentValue = getEffectiveValueForBlockKey(player, configKey);
        int adjustment = 0;

        switch (clickType) {
            case LEFT:
                adjustment = 1;
                break;
            case RIGHT:
                adjustment = -1;
                break;
            case SHIFT_LEFT:
                adjustment = 5;
                break;
            case SHIFT_RIGHT:
                adjustment = -5;
                break;
        }

        if (adjustment != 0) {
            int newValue = Math.max(0, Math.min(100, currentValue + adjustment));
            setPendingChange(player, configKey, newValue);

            String displayName = configKey.replace("-per-chunk", "").replace("-", " ");
            displayName = displayName.substring(0, 1).toUpperCase() + displayName.substring(1);

            player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.pending-change",
                    Map.of("setting", displayName + " Limit", "value", newValue)));
        }
    }

    /**
     * Gets the current effective value for a block configuration key.
     */
    private int getEffectiveValueForBlockKey(Player player, String configKey) {
        switch (configKey) {
            case "hoppers-per-chunk": return getEffectiveValue(player, configKey, ConfigManager.getMaxHoppersPerChunk());
            case "chests-per-chunk": return getEffectiveValue(player, configKey, ConfigManager.getMaxChestsPerChunk());
            case "furnaces-per-chunk": return getEffectiveValue(player, configKey, ConfigManager.getMaxFurnacesPerChunk());
            case "blast-furnaces-per-chunk": return getEffectiveValue(player, configKey, ConfigManager.getMaxBlastFurnacesPerChunk());
            case "smokers-per-chunk": return getEffectiveValue(player, configKey, ConfigManager.getMaxSmokersPerChunk());
            case "barrels-per-chunk": return getEffectiveValue(player, configKey, ConfigManager.getMaxBarrelsPerChunk());
            case "droppers-per-chunk": return getEffectiveValue(player, configKey, ConfigManager.getMaxDroppersPerChunk());
            case "dispensers-per-chunk": return getEffectiveValue(player, configKey, ConfigManager.getMaxDispensersPerChunk());
            case "shulker-boxes-per-chunk": return getEffectiveValue(player, configKey, ConfigManager.getMaxShulkerBoxesPerChunk());
            case "tnt-per-chunk": return getEffectiveValue(player, configKey, ConfigManager.getMaxTntPerChunk());
            case "pistons-per-chunk": return getEffectiveValue(player, configKey, ConfigManager.getMaxPistonsPerChunk());
            case "observers-per-chunk": return getEffectiveValue(player, configKey, ConfigManager.getMaxObserversPerChunk());
            default: return 0;
        }
    }

    /**
     * Shows detailed information about pending changes to the player.
     */
    private void showPendingChangesDetails(Player player) {
        Map<String, Object> changes = pendingChanges.get(player.getUniqueId());
        if (changes == null || changes.isEmpty()) {
            player.sendMessage(MessageManager.getPrefixedMessage("gui.no-changes"));
            return;
        }

        player.sendMessage(MessageManager.color("&6=== Pending Changes ==="));
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String key = entry.getKey();
            String displayName = key.replace("-", " ").replace("per chunk", "per chunk");
            displayName = displayName.substring(0, 1).toUpperCase() + displayName.substring(1);

            player.sendMessage(MessageManager.color("&e" + displayName + "&7: &f" + entry.getValue()));
        }
        player.sendMessage(MessageManager.color("&7Go back to main menu to save these changes."));
    }

    /**
     * Saves all pending changes for a player.
     */
    private void savePendingChanges(Player player) {
        Map<String, Object> changes = pendingChanges.get(player.getUniqueId());
        if (changes == null || changes.isEmpty()) {
            player.sendMessage(MessageManager.getPrefixedMessage("gui.no-changes"));
            return;
        }

        int changeCount = changes.size();

        try {
            // Apply all changes using ConfigManager
            boolean success = ConfigManager.applyGUIChanges(changes);

            if (success) {
                player.sendMessage(MessageManager.getPrefixedFormattedMessage("gui.changes-saved",
                        Map.of("count", changeCount)));

                // Log changes if debug is enabled
                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().info("GUI Changes saved by " + player.getName() + ":");
                    for (Map.Entry<String, Object> entry : changes.entrySet()) {
                        LagXpert.getInstance().getLogger().info("  " + entry.getKey() + " = " + entry.getValue());
                    }
                }

                // Clear pending changes after successful save
                changes.clear();

                // Notify about restart requirement for certain changes
                boolean requiresRestart = changes.keySet().stream()
                        .anyMatch(key -> key.contains("module-enabled") ||
                                key.equals("debug-enabled") ||
                                key.equals("redstone-active-ticks"));

                if (requiresRestart) {
                    player.sendMessage(MessageManager.color("&e⚠ &7Some changes may require a server restart to take full effect."));
                }

            } else {
                player.sendMessage(MessageManager.getPrefixedMessage("general.error-occurred"));
                player.sendMessage(MessageManager.color("&cSome changes could not be saved. Check console for details."));
            }

        } catch (Exception e) {
            player.sendMessage(MessageManager.getPrefixedMessage("general.error-occurred"));
            LagXpert.getInstance().getLogger().warning("Failed to save GUI changes for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Sets a pending change for a player.
     */
    private static void setPendingChange(Player player, String key, Object value) {
        pendingChanges.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(key, value);
    }

    /**
     * Gets the effective value for a configuration key, considering pending changes.
     */
    private static int getEffectiveValue(Player player, String key, int defaultValue) {
        Map<String, Object> changes = pendingChanges.get(player.getUniqueId());
        if (changes != null && changes.containsKey(key)) {
            Object value = changes.get(key);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        }
        return defaultValue;
    }

    /**
     * Gets the effective boolean value for a configuration key, considering pending changes.
     */
    private static boolean getEffectiveBooleanValue(Player player, String key, boolean defaultValue) {
        Map<String, Object> changes = pendingChanges.get(player.getUniqueId());
        if (changes != null && changes.containsKey(key)) {
            Object value = changes.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        }
        return defaultValue;
    }

    /**
     * Checks if a player has any pending changes.
     */
    private static boolean hasPendingChanges(Player player) {
        Map<String, Object> changes = pendingChanges.get(player.getUniqueId());
        return changes != null && !changes.isEmpty();
    }

    /**
     * Gets the number of pending changes for a player.
     */
    private static int getPendingChangesCount(Player player) {
        Map<String, Object> changes = pendingChanges.get(player.getUniqueId());
        return changes != null ? changes.size() : 0;
    }

    /**
     * Helper method to create a menu item with specified properties.
     */
    private static ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(MessageManager.color(name));
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore.stream()
                        .map(MessageManager::color)
                        .collect(java.util.stream.Collectors.toList()));
            }
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Helper method to add a block limit configuration item to the GUI.
     */
    private static void addBlockLimitItem(Inventory gui, int slot, Material material,
                                          String name, int currentLimit, String configKey) {
        ItemStack item = createMenuItem(
                material,
                "&6" + name,
                Arrays.asList(
                        "&7Current limit: &e" + currentLimit + "/chunk",
                        "",
                        "&7Left click: +1",
                        "&7Right click: -1",
                        "&7Shift+Left: +5",
                        "&7Shift+Right: -5"
                )
        );
        gui.setItem(slot, item);
    }

    /**
     * Helper method to add decorative borders to GUIs.
     */
    private static void addBorders(Inventory gui) {
        ItemStack border = createMenuItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);

        int size = gui.getSize();

        // Top and bottom rows
        for (int i = 0; i < 9; i++) {
            if (gui.getItem(i) == null) gui.setItem(i, border);
            if (gui.getItem(size - 9 + i) == null) gui.setItem(size - 9 + i, border);
        }

        // Side columns
        for (int i = 9; i < size - 9; i += 9) {
            if (gui.getItem(i) == null) gui.setItem(i, border);
            if (gui.getItem(i + 8) == null) gui.setItem(i + 8, border);
        }
    }

    /**
     * Helper method to add a back button to GUIs.
     */
    private static void addBackButton(Inventory gui, int slot) {
        ItemStack backButton = createMenuItem(
                Material.ARROW,
                "&cBack",
                Arrays.asList("&7Return to main menu")
        );
        gui.setItem(slot, backButton);
    }

    /**
     * Gets the player's currently open GUI type.
     */
    public static String getOpenGUI(Player player) {
        return openGUIs.get(player.getUniqueId());
    }

    /**
     * Clears all tracking data for a player.
     */
    public static void clearPlayerData(Player player) {
        UUID playerId = player.getUniqueId();
        openGUIs.remove(playerId);
        pendingChanges.remove(playerId);
    }
}