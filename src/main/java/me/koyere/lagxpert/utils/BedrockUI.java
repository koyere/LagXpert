package me.koyere.lagxpert.utils;

import me.koyere.lagxpert.LagXpert;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared Bedrock compatibility layer for inventory interfaces.
 *
 * Bedrock clients reach the server through Geyser, which translates Java
 * inventory packets into Bedrock ones. That translation is not lossless, and a
 * few specific things behave badly:
 *
 * <ul>
 *   <li><b>Spawn eggs</b> render as the wrong item or as nothing at all, because
 *       Bedrock keys them differently from Java.</li>
 *   <li><b>Command and structure blocks</b> may be filtered out entirely
 *       depending on client permissions.</li>
 *   <li><b>Long tooltips</b> get truncated at an unpredictable point, so a lore
 *       line carrying important information can silently disappear.</li>
 * </ul>
 *
 * Rather than maintaining a parallel set of Bedrock-specific screens, which is
 * what the previous approach attempted and then never wired up, this class
 * post-processes a finished inventory in place. Any screen can become
 * Bedrock-safe with a single call at the point it is opened, which means the
 * compatibility rules cannot drift out of sync between screens.
 *
 * For Java players every method here is a no-op, so there is no cost to routing
 * all inventory opens through it.
 */
public final class BedrockUI {

    /**
     * Lore lines beyond this count are dropped, with a marker added.
     * Bedrock begins truncating tooltips unpredictably past roughly this point.
     */
    private static final int MAX_LORE_LINES = 10;

    /** Materials that Geyser renders incorrectly, mapped to safe equivalents. */
    private static final Map<Material, Material> SUBSTITUTIONS = new HashMap<>();

    static {
        // Command-style blocks may be filtered based on client permissions.
        put(SUBSTITUTIONS, "COMMAND_BLOCK", "REDSTONE_BLOCK");
        put(SUBSTITUTIONS, "CHAIN_COMMAND_BLOCK", "REDSTONE_BLOCK");
        put(SUBSTITUTIONS, "REPEATING_COMMAND_BLOCK", "REDSTONE_BLOCK");
        put(SUBSTITUTIONS, "STRUCTURE_BLOCK", "STONE");
        put(SUBSTITUTIONS, "STRUCTURE_VOID", "STONE");
        put(SUBSTITUTIONS, "JIGSAW", "STONE");
        put(SUBSTITUTIONS, "BARRIER", "RED_TERRACOTTA");
        put(SUBSTITUTIONS, "KNOWLEDGE_BOOK", "BOOK");
        put(SUBSTITUTIONS, "DEBUG_STICK", "STICK");
        put(SUBSTITUTIONS, "PLAYER_HEAD", "SKELETON_SKULL");
    }

    /**
     * Registers a substitution by material name.
     *
     * Names are resolved reflectively so that a material absent from an older or
     * newer server version simply skips registration instead of preventing the
     * class from loading.
     */
    private static void put(Map<Material, Material> map, String from, String to) {
        Material fromMaterial = resolve(from);
        Material toMaterial = resolve(to);
        if (fromMaterial != null && toMaterial != null) {
            map.put(fromMaterial, toMaterial);
        }
    }

    private static Material resolve(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private BedrockUI() {
    }

    /**
     * Applies Bedrock-safe adjustments to an inventory and opens it.
     *
     * This is the single entry point every screen should use instead of calling
     * {@link Player#openInventory} directly.
     *
     * @param player the viewer
     * @param gui    the fully populated inventory
     * @return true if the inventory was opened
     */
    public static boolean adaptAndOpen(Player player, Inventory gui) {
        try {
            adapt(gui, player);
            player.openInventory(gui);
            return true;
        } catch (Exception e) {
            LagXpert.getInstance().getLogger().warning(
                    "[BedrockUI] Failed to open inventory for " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Rewrites every item in the inventory to be safe for the viewer's platform.
     *
     * Does nothing for Java players.
     *
     * @param gui    the inventory to adjust in place
     * @param player the viewer whose platform determines the adjustments
     */
    public static void adapt(Inventory gui, Player player) {
        if (gui == null || player == null) {
            return;
        }
        if (!BedrockPlayerUtils.isBedrockPlayer(player)) {
            return;
        }
        if (!ConfigManager.isBedrockGUIOptimizationEnabled()) {
            return;
        }

        for (int slot = 0; slot < gui.getSize(); slot++) {
            ItemStack original = gui.getItem(slot);
            if (original == null || original.getType() == Material.AIR) {
                continue;
            }
            ItemStack adapted = adaptItem(original);
            if (adapted != null) {
                gui.setItem(slot, adapted);
            }
        }
    }

    /**
     * Returns a Bedrock-safe copy of a single item.
     *
     * The display name and lore are preserved; only the material and the lore
     * length are adjusted.
     */
    public static ItemStack adaptItem(ItemStack original) {
        if (original == null) {
            return null;
        }

        Material safeMaterial = safeMaterial(original.getType());
        ItemStack result = original;

        if (safeMaterial != original.getType()) {
            result = new ItemStack(safeMaterial, original.getAmount());
            ItemMeta originalMeta = original.getItemMeta();
            if (originalMeta != null) {
                // Copying meta across a material change can fail for meta subtypes
                // that do not apply to the replacement, so name and lore are
                // transferred explicitly rather than wholesale.
                ItemMeta newMeta = result.getItemMeta();
                if (newMeta != null) {
                    if (originalMeta.hasDisplayName()) {
                        newMeta.setDisplayName(originalMeta.getDisplayName());
                    }
                    if (originalMeta.hasLore()) {
                        newMeta.setLore(originalMeta.getLore());
                    }
                    result.setItemMeta(newMeta);
                }
            }
        }

        // Cap lore length.
        ItemMeta meta = result.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null && lore.size() > MAX_LORE_LINES) {
                List<String> trimmed = new ArrayList<>(lore.subList(0, MAX_LORE_LINES - 1));
                trimmed.add(MessageManager.color("&8..."));
                meta.setLore(trimmed);
                result.setItemMeta(meta);
            }
        }

        return result;
    }

    /**
     * Maps a material to one that renders correctly on Bedrock.
     *
     * Spawn eggs are handled by name because there are dozens of them and the set
     * changes between versions.
     */
    public static Material safeMaterial(Material original) {
        if (original == null) {
            return Material.PAPER;
        }

        if (original.name().endsWith("_SPAWN_EGG") || original.name().equals("SPAWN_EGG")) {
            Material egg = resolve("EGG");
            return egg != null ? egg : original;
        }

        Material substitute = SUBSTITUTIONS.get(original);
        return substitute != null ? substitute : original;
    }

    /**
     * Returns the largest inventory size that is safe for this player.
     *
     * @param preferredSize the size the screen would like to use
     */
    public static int safeSize(Player player, int preferredSize) {
        int size = preferredSize;
        if (BedrockPlayerUtils.isBedrockPlayer(player)) {
            size = Math.min(size, BedrockPlayerUtils.getSafeInventorySize(player));
        }
        // Normalise to a legal chest size.
        size = (size / 9) * 9;
        return Math.max(9, Math.min(54, size));
    }
}
