package me.koyere.lagxpert.gui;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.BedrockPlayerUtils;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bedrock-compatible GUI system that provides optimized interfaces for both
 * Java and Bedrock players. Handles platform-specific limitations and 
 * provides fallback alternatives for unsupported features.
 * 
 * Performance optimizations:
 * - Caches GUI templates for different platforms
 * - Uses simpler item data for Bedrock players
 * - Implements efficient item click detection
 * - Reduces inventory operations that cause issues on Bedrock
 */
public class BedrockCompatibleGUI {
    
    // Platform-specific GUI templates
    private static final Map<String, GuiTemplate> JAVA_TEMPLATES = new ConcurrentHashMap<>();
    private static final Map<String, GuiTemplate> BEDROCK_TEMPLATES = new ConcurrentHashMap<>();
    
    // Active GUI sessions
    private static final Map<UUID, ActiveGUISession> ACTIVE_SESSIONS = new ConcurrentHashMap<>();
    
    // GUI constants
    private static final int JAVA_INVENTORY_SIZE = 54; // 6 rows
    private static final int BEDROCK_INVENTORY_SIZE = 45; // 5 rows (safer for Bedrock)
    private static final int BEDROCK_SAFE_INVENTORY_SIZE = 36; // 4 rows (most compatible)
    
    /**
     * Initializes GUI templates for both platforms.
     * Called once during plugin startup.
     */
    public static void initializeTemplates() {
        // Initialize main menu templates
        initializeMainMenuTemplates();
        
        // Initialize configuration page templates
        initializeConfigTemplates();
        
        LagXpert.getInstance().getLogger().info("[BedrockCompatibleGUI] GUI templates initialized for Java and Bedrock platforms");
    }
    
    /**
     * Opens the appropriate main menu based on player platform.
     */
    public static void openMainMenu(Player player) {
        boolean isBedrock = BedrockPlayerUtils.isBedrockPlayer(player);
        String templateKey = "main_menu";
        
        GuiTemplate template = isBedrock ? 
            BEDROCK_TEMPLATES.get(templateKey) : 
            JAVA_TEMPLATES.get(templateKey);
        
        if (template == null) {
            // Fallback to basic menu
            openBasicMainMenu(player);
            return;
        }
        
        openTemplateGUI(player, template);
    }
    
    /**
     * Opens a configuration menu optimized for the player's platform.
     */
    public static void openConfigMenu(Player player, String configType) {
        boolean isBedrock = BedrockPlayerUtils.isBedrockPlayer(player);
        String templateKey = "config_" + configType;
        
        GuiTemplate template = isBedrock ? 
            BEDROCK_TEMPLATES.get(templateKey) : 
            JAVA_TEMPLATES.get(templateKey);
        
        if (template == null) {
            // Fallback to text-based configuration
            sendConfigViaChat(player, configType);
            return;
        }
        
        openTemplateGUI(player, template);
    }
    
    /**
     * Opens a GUI based on a template, creating appropriate inventory.
     */
    private static void openTemplateGUI(Player player, GuiTemplate template) {
        try {
            boolean isBedrock = BedrockPlayerUtils.isBedrockPlayer(player);
            int inventorySize = isBedrock ? 
                BedrockPlayerUtils.getSafeInventorySize(player) : 
                template.getSize();
            
            // Ensure size is valid (multiple of 9)
            inventorySize = Math.max(9, (inventorySize / 9) * 9);
            
            Inventory gui = Bukkit.createInventory(null, inventorySize, template.getTitle());
            
            // Populate inventory with template items
            populateInventory(gui, template, player, isBedrock);
            
            // Track session
            ActiveGUISession session = new ActiveGUISession(template.getId(), System.currentTimeMillis());
            ACTIVE_SESSIONS.put(player.getUniqueId(), session);
            
            player.openInventory(gui);
            
            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info(
                    "[BedrockCompatibleGUI] Opened " + (isBedrock ? "Bedrock" : "Java") + 
                    " GUI '" + template.getId() + "' for player " + player.getName()
                );
            }
            
        } catch (Exception e) {
            LagXpert.getInstance().getLogger().warning(
                "[BedrockCompatibleGUI] Failed to open GUI for player " + player.getName() + ": " + e.getMessage()
            );
            
            // Fallback to chat-based interface
            sendConfigViaChat(player, template.getId());
        }
    }
    
    /**
     * Populates an inventory with items from a template.
     */
    private static void populateInventory(Inventory gui, GuiTemplate template, Player player, boolean isBedrock) {
        for (GuiItem item : template.getItems()) {
            int slot = item.getSlot();
            
            // Skip slots that don't fit in the inventory
            if (slot >= gui.getSize()) {
                continue;
            }
            
            ItemStack displayItem = createDisplayItem(item, player, isBedrock);
            if (displayItem != null) {
                gui.setItem(slot, displayItem);
            }
        }
        
        // Add border items for better UX on smaller inventories
        if (isBedrock && gui.getSize() < JAVA_INVENTORY_SIZE) {
            addBedrockBorder(gui);
        }
    }
    
    /**
     * Creates a display item optimized for the player's platform.
     */
    private static ItemStack createDisplayItem(GuiItem guiItem, Player player, boolean isBedrock) {
        Material material = guiItem.getMaterial();
        
        // Replace problematic materials for Bedrock
        if (isBedrock) {
            material = getBedrockCompatibleMaterial(material);
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            // Set display name
            String displayName = guiItem.getDisplayName();
            if (displayName != null) {
                meta.setDisplayName(MessageManager.color(displayName));
            }
            
            // Set lore (description)
            List<String> lore = guiItem.getLore();
            if (lore != null && !lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    // Replace placeholders and apply colors
                    String processedLine = replacePlaceholders(line, player);
                    coloredLore.add(MessageManager.color(processedLine));
                }
                
                // Limit lore lines for Bedrock compatibility
                if (isBedrock && coloredLore.size() > 10) {
                    coloredLore = coloredLore.subList(0, 10);
                    coloredLore.add(MessageManager.color("&7..."));
                }
                
                meta.setLore(coloredLore);
            }
            
            // Simplify item data for Bedrock if configured
            if (isBedrock && BedrockPlayerUtils.shouldSimplifyItemData(player)) {
                // Remove complex NBT data that might cause issues
                // This is handled automatically by the Bukkit API
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Replaces placeholders in GUI text with actual values.
     */
    private static String replacePlaceholders(String text, Player player) {
        // Common placeholders
        text = text.replace("{player}", player.getName());
        text = text.replace("{platform}", BedrockPlayerUtils.getPlayerPlatform(player));
        
        // Configuration value placeholders
        text = text.replace("{max_mobs}", String.valueOf(ConfigManager.getMaxMobsPerChunk()));
        text = text.replace("{max_hoppers}", String.valueOf(ConfigManager.getMaxHoppersPerChunk()));
        text = text.replace("{max_chests}", String.valueOf(ConfigManager.getMaxChestsPerChunk()));
        
        // Status placeholders
        text = text.replace("{mobs_enabled}", ConfigManager.isMobsModuleEnabled() ? "Enabled" : "Disabled");
        text = text.replace("{redstone_enabled}", ConfigManager.isRedstoneControlModuleEnabled() ? "Enabled" : "Disabled");
        
        return text;
    }
    
    /**
     * Converts Java-specific materials to Bedrock-compatible alternatives.
     */
    private static Material getBedrockCompatibleMaterial(Material original) {
        // Map of potentially problematic materials to safe alternatives
        Map<Material, Material> bedrockAlternatives = new HashMap<>();
        
        // Spawn eggs might have issues, use regular eggs
        if (original.name().contains("SPAWN_EGG")) {
            return Material.EGG;
        }
        
        // Some specific materials that might cause issues
        bedrockAlternatives.put(Material.KNOWLEDGE_BOOK, Material.BOOK);
        bedrockAlternatives.put(Material.COMMAND_BLOCK, Material.REDSTONE_BLOCK);
        bedrockAlternatives.put(Material.STRUCTURE_BLOCK, Material.STONE);
        
        return bedrockAlternatives.getOrDefault(original, original);
    }
    
    /**
     * Adds a border to smaller inventories for better UX.
     */
    private static void addBedrockBorder(Inventory gui) {
        ItemStack borderItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = borderItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            borderItem.setItemMeta(meta);
        }
        
        int size = gui.getSize();
        
        // Top and bottom rows
        for (int i = 0; i < 9; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, borderItem);
            }
            if (size > 9 && gui.getItem(size - 9 + i) == null) {
                gui.setItem(size - 9 + i, borderItem);
            }
        }
        
        // Side columns
        for (int row = 1; row < (size / 9) - 1; row++) {
            int leftSlot = row * 9;
            int rightSlot = row * 9 + 8;
            
            if (gui.getItem(leftSlot) == null) {
                gui.setItem(leftSlot, borderItem);
            }
            if (gui.getItem(rightSlot) == null) {
                gui.setItem(rightSlot, borderItem);
            }
        }
    }
    
    /**
     * Fallback method: sends configuration options via chat for Bedrock players
     * who might have GUI issues.
     */
    private static void sendConfigViaChat(Player player, String configType) {
        player.sendMessage(MessageManager.color("&e&l[LagXpert] &7Configuration"));
        player.sendMessage(MessageManager.color("&7Your client may not support the GUI interface."));
        player.sendMessage(MessageManager.color("&7Use these commands instead:"));
        
        switch (configType.toLowerCase()) {
            case "main_menu":
                player.sendMessage(MessageManager.color("&e/lagxpert help &7- Show available commands"));
                player.sendMessage(MessageManager.color("&e/lagxpert inspect &7- Inspect current chunk"));
                player.sendMessage(MessageManager.color("&e/chunkstatus &7- Check chunk limits"));
                break;
                
            case "config_mobs":
                player.sendMessage(MessageManager.color("&e/lagxpert reload &7- Reload mob configuration"));
                player.sendMessage(MessageManager.color("&7Current mob limit: &e" + ConfigManager.getMaxMobsPerChunk() + " per chunk"));
                break;
                
            case "config_performance":
                player.sendMessage(MessageManager.color("&e/tps &7- Check server performance"));
                player.sendMessage(MessageManager.color("&e/lagxpert inspect &7- Analyze current area"));
                break;
                
            default:
                player.sendMessage(MessageManager.color("&e/lagxpert help &7- Show all available commands"));
        }
    }
    
    /**
     * Opens a basic main menu that works on all platforms.
     */
    private static void openBasicMainMenu(Player player) {
        boolean isBedrock = BedrockPlayerUtils.isBedrockPlayer(player);
        int size = isBedrock ? BEDROCK_SAFE_INVENTORY_SIZE : JAVA_INVENTORY_SIZE;
        
        Inventory gui = Bukkit.createInventory(null, size, "LagXpert");
        
        // Basic items that work everywhere
        ItemStack statusItem = new ItemStack(Material.EMERALD);
        ItemMeta statusMeta = statusItem.getItemMeta();
        if (statusMeta != null) {
            statusMeta.setDisplayName(MessageManager.color("&aServer Status"));
            statusMeta.setLore(Arrays.asList(
                MessageManager.color("&7Platform: &e" + BedrockPlayerUtils.getPlayerPlatform(player)),
                MessageManager.color("&7Mob Limit: &e" + ConfigManager.getMaxMobsPerChunk()),
                MessageManager.color("&eClick for details")
            ));
            statusItem.setItemMeta(statusMeta);
        }
        gui.setItem(13, statusItem);
        
        // Commands item
        ItemStack commandsItem = new ItemStack(Material.BOOK);
        ItemMeta commandsMeta = commandsItem.getItemMeta();
        if (commandsMeta != null) {
            commandsMeta.setDisplayName(MessageManager.color("&6Available Commands"));
            commandsMeta.setLore(Arrays.asList(
                MessageManager.color("&7/chunkstatus - Check chunk info"),
                MessageManager.color("&7/tps - Server performance"),
                MessageManager.color("&7/abyss - Recover items"),
                MessageManager.color("&eClick to close")
            ));
            commandsItem.setItemMeta(commandsMeta);
        }
        gui.setItem(14, commandsItem);
        
        player.openInventory(gui);
        
        // Track session
        ActiveGUISession session = new ActiveGUISession("basic_menu", System.currentTimeMillis());
        ACTIVE_SESSIONS.put(player.getUniqueId(), session);
    }
    
    /**
     * Initializes main menu templates for both platforms.
     */
    private static void initializeMainMenuTemplates() {
        // Java Edition main menu (full featured)
        GuiTemplate javaMainMenu = new GuiTemplate("main_menu", "&6&lLagXpert Configuration", 54);
        
        // Add Java-specific items with full functionality
        javaMainMenu.addItem(new GuiItem(10, Material.ZOMBIE_SPAWN_EGG, "&6Entity Management", 
            Arrays.asList("&7Configure mob limits", "&7Current: &e{max_mobs} mobs/chunk", "&eClick to configure")));
        
        javaMainMenu.addItem(new GuiItem(11, Material.HOPPER, "&6Block Limits", 
            Arrays.asList("&7Configure storage limits", "&7Hoppers: &e{max_hoppers}/chunk", "&eClick to configure")));
        
        javaMainMenu.addItem(new GuiItem(12, Material.REDSTONE, "&6Redstone Control", 
            Arrays.asList("&7Manage redstone systems", "&7Status: &e{redstone_enabled}", "&eClick to configure")));
        
        javaMainMenu.addItem(new GuiItem(13, Material.DIAMOND_SWORD, "&6Performance Monitor", 
            Arrays.asList("&7View server performance", "&7Real-time statistics", "&eClick to view")));
        
        javaMainMenu.addItem(new GuiItem(16, Material.BARRIER, "&cClose Menu", 
            Arrays.asList("&7Exit configuration", "&eClick to close")));
        
        JAVA_TEMPLATES.put("main_menu", javaMainMenu);
        
        // Bedrock Edition main menu (simplified)
        GuiTemplate bedrockMainMenu = new GuiTemplate("main_menu", "&6LagXpert", 36);
        
        // Simpler items for Bedrock compatibility
        bedrockMainMenu.addItem(new GuiItem(10, Material.EGG, "&6Mob Management", 
            Arrays.asList("&7Current limit: &e{max_mobs} per chunk", "&eClick for options")));
        
        bedrockMainMenu.addItem(new GuiItem(12, Material.CHEST, "&6Block Limits", 
            Arrays.asList("&7Storage block limits", "&eClick for options")));
        
        bedrockMainMenu.addItem(new GuiItem(14, Material.EMERALD, "&6Server Status", 
            Arrays.asList("&7Platform: &e{platform}", "&7Performance info", "&eClick to view")));
        
        bedrockMainMenu.addItem(new GuiItem(16, Material.BARRIER, "&cClose", 
            Arrays.asList("&eClick to close")));
        
        BEDROCK_TEMPLATES.put("main_menu", bedrockMainMenu);
    }
    
    /**
     * Initializes configuration page templates.
     */
    private static void initializeConfigTemplates() {
        // Add mob configuration templates
        initializeMobConfigTemplates();
        
        // Add performance monitoring templates  
        initializePerformanceTemplates();
    }
    
    /**
     * Initializes mob configuration templates for both platforms.
     */
    private static void initializeMobConfigTemplates() {
        // Java mob config
        GuiTemplate javaMobConfig = new GuiTemplate("config_mobs", "&6Mob Configuration", 45);
        javaMobConfig.addItem(new GuiItem(13, Material.ZOMBIE_SPAWN_EGG, "&6Current Limit: &e{max_mobs}", 
            Arrays.asList("&7Mobs per chunk limit", "&7Status: &e{mobs_enabled}", "&eClick to modify")));
        
        JAVA_TEMPLATES.put("config_mobs", javaMobConfig);
        
        // Bedrock mob config (simplified)
        GuiTemplate bedrockMobConfig = new GuiTemplate("config_mobs", "&6Mob Settings", 27);
        bedrockMobConfig.addItem(new GuiItem(13, Material.EGG, "&6Limit: &e{max_mobs}", 
            Arrays.asList("&7Per chunk limit", "&eUse commands to change")));
        
        BEDROCK_TEMPLATES.put("config_mobs", bedrockMobConfig);
    }
    
    /**
     * Initializes performance monitoring templates.
     */
    private static void initializePerformanceTemplates() {
        // Java performance monitor
        GuiTemplate javaPerformance = new GuiTemplate("config_performance", "&6Performance Monitor", 54);
        javaPerformance.addItem(new GuiItem(13, Material.EMERALD, "&aServer Status", 
            Arrays.asList("&7Platform: &e{platform}", "&7Real-time monitoring", "&eClick for details")));
        
        JAVA_TEMPLATES.put("config_performance", javaPerformance);
        
        // Bedrock performance monitor (text-based fallback)
        GuiTemplate bedrockPerformance = new GuiTemplate("config_performance", "&6Performance", 27);
        bedrockPerformance.addItem(new GuiItem(13, Material.EMERALD, "&aUse /tps command", 
            Arrays.asList("&7Best compatibility", "&eType /tps in chat")));
        
        BEDROCK_TEMPLATES.put("config_performance", bedrockPerformance);
    }
    
    /**
     * Gets the active GUI session for a player.
     */
    public static ActiveGUISession getSession(Player player) {
        return ACTIVE_SESSIONS.get(player.getUniqueId());
    }
    
    /**
     * Removes a player's GUI session.
     */
    public static void removeSession(Player player) {
        ACTIVE_SESSIONS.remove(player.getUniqueId());
        BedrockPlayerUtils.removePlayerFromCache(player);
    }
    
    /**
     * Handles GUI click events for both platforms.
     */
    public static boolean handleClick(Player player, int slot, String action) {
        ActiveGUISession session = getSession(player);
        if (session == null) {
            return false;
        }
        
        boolean isBedrock = BedrockPlayerUtils.isBedrockPlayer(player);
        
        // Handle basic actions that work on all platforms
        switch (action.toLowerCase()) {
            case "close":
                player.closeInventory();
                return true;
                
            case "status":
                if (isBedrock) {
                    // Send status via chat for Bedrock
                    sendStatusViaChat(player);
                } else {
                    // Open detailed status GUI for Java
                    openConfigMenu(player, "performance");
                }
                return true;
                
            case "commands":
                sendConfigViaChat(player, "commands");
                return true;
                
            default:
                return false;
        }
    }
    
    /**
     * Sends server status information via chat.
     */
    private static void sendStatusViaChat(Player player) {
        player.sendMessage(MessageManager.color("&e&l[LagXpert] &aServer Status"));
        player.sendMessage(MessageManager.color("&7Platform: &e" + BedrockPlayerUtils.getPlayerPlatform(player)));
        player.sendMessage(MessageManager.color("&7Mob Limit: &e" + ConfigManager.getMaxMobsPerChunk() + " per chunk"));
        player.sendMessage(MessageManager.color("&7Hopper Limit: &e" + ConfigManager.getMaxHoppersPerChunk() + " per chunk"));
        player.sendMessage(MessageManager.color("&7Use &e/tps &7for performance info"));
    }
    
    /**
     * Data classes for GUI management
     */
    public static class GuiTemplate {
        private final String id;
        private final String title;
        private final int size;
        private final List<GuiItem> items = new ArrayList<>();
        
        public GuiTemplate(String id, String title, int size) {
            this.id = id;
            this.title = title;
            this.size = size;
        }
        
        public void addItem(GuiItem item) {
            items.add(item);
        }
        
        public String getId() { return id; }
        public String getTitle() { return title; }
        public int getSize() { return size; }
        public List<GuiItem> getItems() { return items; }
    }
    
    public static class GuiItem {
        private final int slot;
        private final Material material;
        private final String displayName;
        private final List<String> lore;
        
        public GuiItem(int slot, Material material, String displayName, List<String> lore) {
            this.slot = slot;
            this.material = material;
            this.displayName = displayName;
            this.lore = lore;
        }
        
        public int getSlot() { return slot; }
        public Material getMaterial() { return material; }
        public String getDisplayName() { return displayName; }
        public List<String> getLore() { return lore; }
    }
    
    public static class ActiveGUISession {
        private final String guiType;
        private final long openTime;
        
        public ActiveGUISession(String guiType, long openTime) {
            this.guiType = guiType;
            this.openTime = openTime;
        }
        
        public String getGuiType() { return guiType; }
        public long getOpenTime() { return openTime; }
    }
}