package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag; // For Shulker Boxes
import org.bukkit.block.BlockState; // For Tile Entities
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;

/**
 * Handles the /chunkstatus command for players to inspect their current chunk.
 * Shows usage of mobs and various important blocks in a visually clean format,
 * using optimized counting methods.
 */
public class ChunkStatusCommand implements CommandExecutor {

    // Define materials to count specifically for clarity and iteration
    private static final Material[] TILE_ENTITY_MATERIALS_TO_COUNT = {
            Material.HOPPER, Material.CHEST, Material.TRAPPED_CHEST, Material.FURNACE,
            Material.BLAST_FURNACE, Material.SMOKER, Material.BARREL, Material.DROPPER,
            Material.DISPENSER // Shulker boxes handled separately due to color variants
    };

    private static final Material[] NON_TILE_ENTITY_MATERIALS_TO_COUNT = {
            Material.OBSERVER, Material.PISTON, Material.STICKY_PISTON, Material.TNT
    };

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Reverted from pattern matching for Java 11 compatibility
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageManager.getPrefixedMessage("general.players-only"));
            return true;
        }
        Player player = (Player) sender; // Explicit cast after instanceof check

        // Permission Check (e.g., "lagxpert.chunkstatus" or reuse "lagxpert.use")
        if (!player.hasPermission("lagxpert.chunkstatus")) {
            player.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();

        // --- Optimized Counting ---
        int mobCount = countLivingEntitiesInChunk(chunk);
        Map<Material, Integer> blockCounts = countAllRelevantBlocksOptimized(chunk);

        // --- Build Display Message using messages.yml ---
        StringBuilder msg = new StringBuilder();

        Map<String, Object> titlePlaceholders = new java.util.HashMap<>();
        titlePlaceholders.put("chunk_x", String.valueOf(chunk.getX()));
        titlePlaceholders.put("chunk_z", String.valueOf(chunk.getZ()));
        titlePlaceholders.put("world", chunk.getWorld().getName());

        msg.append(MessageManager.get("chunkstatus.header")).append("\n");
        msg.append(MessageManager.getFormatted("chunkstatus.title", titlePlaceholders)).append("\n");

        // Display counts using translatable names from messages.yml
        appendTranslatedBlockCount(msg, "mobs", mobCount);
        appendTranslatedBlockCount(msg, "hoppers", blockCounts.getOrDefault(Material.HOPPER, 0));
        appendTranslatedBlockCount(msg, "chests",
                blockCounts.getOrDefault(Material.CHEST, 0) + blockCounts.getOrDefault(Material.TRAPPED_CHEST, 0));
        appendTranslatedBlockCount(msg, "furnaces", blockCounts.getOrDefault(Material.FURNACE, 0));
        appendTranslatedBlockCount(msg, "blast_furnaces", blockCounts.getOrDefault(Material.BLAST_FURNACE, 0));
        appendTranslatedBlockCount(msg, "smokers", blockCounts.getOrDefault(Material.SMOKER, 0));
        appendTranslatedBlockCount(msg, "shulker_boxes", blockCounts.getOrDefault(Material.SHULKER_BOX, 0));
        appendTranslatedBlockCount(msg, "droppers", blockCounts.getOrDefault(Material.DROPPER, 0));
        appendTranslatedBlockCount(msg, "dispensers", blockCounts.getOrDefault(Material.DISPENSER, 0));
        appendTranslatedBlockCount(msg, "barrels", blockCounts.getOrDefault(Material.BARREL, 0));
        appendTranslatedBlockCount(msg, "observers", blockCounts.getOrDefault(Material.OBSERVER, 0));
        appendTranslatedBlockCount(msg, "pistons",
                blockCounts.getOrDefault(Material.PISTON, 0) + blockCounts.getOrDefault(Material.STICKY_PISTON, 0));
        appendTranslatedBlockCount(msg, "tnt", blockCounts.getOrDefault(Material.TNT, 0));

        msg.append(MessageManager.get("chunkstatus.footer"));

        player.sendMessage(msg.toString());
        return true;
    }

    /**
     * Appends a translated line from messages.yml for a given block type and count,
     * if the count is greater than zero.
     * @param msg StringBuilder to append to.
     * @param translationKey The key used in both translations and chunkstatus line sections.
     * @param count The number of blocks of this type.
     */
    private void appendTranslatedBlockCount(StringBuilder msg, String translationKey, int count) {
        if (count > 0) {
            String translatedName = MessageManager.getTranslation(translationKey, translationKey);
            Map<String, Object> placeholders = new java.util.HashMap<>();
            placeholders.put("name", translatedName);
            placeholders.put("count", String.valueOf(count));
            msg.append(MessageManager.getFormatted("chunkstatus.line-" + translationKey, placeholders)).append("\n");
        }
    }


    private int countLivingEntitiesInChunk(Chunk chunk) {
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts all relevant block types in a chunk efficiently.
     * Tile Entities are counted by iterating tile entities.
     * Non-Tile Entities are counted in a single pass through all blocks.
     */
    private Map<Material, Integer> countAllRelevantBlocksOptimized(Chunk chunk) {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);

        // Initialize counts for materials we are interested in
        for (Material mat : TILE_ENTITY_MATERIALS_TO_COUNT) counts.put(mat, 0);
        for (Material mat : NON_TILE_ENTITY_MATERIALS_TO_COUNT) counts.put(mat, 0);
        counts.put(Material.SHULKER_BOX, 0); // Special key for all shulker boxes

        // 1. Count Tile Entities
        for (BlockState blockState : chunk.getTileEntities()) {
            Material type = blockState.getType();
            counts.computeIfPresent(type, (key, val) -> val + 1); // Increments if key exists

            // Special handling for all shulker boxes (as they have different material types per color)
            if (Tag.SHULKER_BOXES.isTagged(type)) { // Bukkit's Tag API for shulker boxes
                counts.put(Material.SHULKER_BOX, counts.getOrDefault(Material.SHULKER_BOX, 0) + 1);
            }
        }

        // 2. Count Non-Tile Entities in a single pass (if any are defined)
        if (NON_TILE_ENTITY_MATERIALS_TO_COUNT.length > 0) {
            int minHeight = chunk.getWorld().getMinHeight();
            int maxHeight = chunk.getWorld().getMaxHeight();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minHeight; y < maxHeight; y++) {
                        Material type = chunk.getBlock(x, y, z).getType();
                        // Check if this type is one of the non-tile entities we're looking for
                        for(Material nonTileMat : NON_TILE_ENTITY_MATERIALS_TO_COUNT) {
                            if (type == nonTileMat) {
                                counts.computeIfPresent(type, (key, val) -> val + 1); // Increments if key exists
                                break; // Found its type, move to next block in this y-column
                            }
                        }
                    }
                }
            }
        }
        return counts;
    }
}