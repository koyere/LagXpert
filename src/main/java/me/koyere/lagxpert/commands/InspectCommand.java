package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Tag;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles the logic for the /lagxpert inspect <x> <z> [world_name] command.
 * Provides methods to collect and display technical information about a specified chunk.
 * This class is intended to be used by a primary command executor (e.g., LagXpertCommand).
 */
public class InspectCommand {

    /**
     * Executes the inspect logic for the given chunk coordinates and optional world.
     *
     * @param sender The CommandSender who issued the command.
     * @param args   The arguments for inspection, expected to be: <x> <z> [world_name].
     * args[0] should be chunk X, args[1] should be chunk Z, args[2] (optional) is world name.
     * @return true if the command logic was processed.
     */
    public static boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageManager.getPrefixedMessage("inspect.usage"));
            return true;
        }

        if (!isNumeric(args[0]) || !isNumeric(args[1])) {
            sender.sendMessage(MessageManager.getPrefixedMessage("inspect.invalid-coords"));
            return true;
        }

        int chunkX = Integer.parseInt(args[0]);
        int chunkZ = Integer.parseInt(args[1]);
        World targetWorld = null;

        if (args.length >= 3) {
            targetWorld = Bukkit.getWorld(args[2]);
            if (targetWorld == null) {
                // Use Map<String, Object> for placeholders
                Map<String, Object> placeholders = new HashMap<>();
                placeholders.put("world_name", args[2]);
                sender.sendMessage(MessageManager.getPrefixedFormattedMessage("inspect.world-not-found", placeholders));
                return true;
            }
        }

        Chunk targetChunk = findLoadedChunk(targetWorld, chunkX, chunkZ);

        if (targetChunk == null) {
            // Use Map<String, Object> for placeholders
            Map<String, Object> placeholders = new HashMap<>();
            placeholders.put("chunk_x", String.valueOf(chunkX)); // String.valueOf to be explicit, though auto-boxing would handle int for Object
            placeholders.put("chunk_z", String.valueOf(chunkZ));
            placeholders.put("world_name", targetWorld != null ? targetWorld.getName() : "any loaded world");
            sender.sendMessage(MessageManager.getPrefixedFormattedMessage("inspect.chunk-not-loaded", placeholders));
            return true;
        }

        Map<String, Object> chunkData = collectChunkData(targetChunk);
        chunkData.put("chunk_x", String.valueOf(targetChunk.getX()));
        chunkData.put("chunk_z", String.valueOf(targetChunk.getZ()));
        chunkData.put("world_name", targetChunk.getWorld().getName());

        sender.sendMessage(MessageManager.getPrefixedFormattedMessage("inspect.header", chunkData));
        sender.sendMessage(MessageManager.getPrefixedFormattedMessage("inspect.line.entities", chunkData));
        sender.sendMessage(MessageManager.getPrefixedFormattedMessage("inspect.line.hoppers", chunkData));
        sender.sendMessage(MessageManager.getPrefixedFormattedMessage("inspect.line.chests", chunkData));
        sender.sendMessage(MessageManager.getPrefixedFormattedMessage("inspect.line.furnaces", chunkData));
        sender.sendMessage(MessageManager.getPrefixedFormattedMessage("inspect.line.shulker_boxes", chunkData));
        sender.sendMessage(MessageManager.getPrefixedFormattedMessage("inspect.line.barrels", chunkData));
        sender.sendMessage(MessageManager.getPrefixedFormattedMessage("inspect.line.dispensers_droppers", chunkData));

        return true;
    }

    private static Chunk findLoadedChunk(World world, int chunkX, int chunkZ) {
        if (world != null) {
            return world.isChunkLoaded(chunkX, chunkZ) ? world.getChunkAt(chunkX, chunkZ) : null;
        } else {
            for (World w : Bukkit.getWorlds()) {
                if (w.isChunkLoaded(chunkX, chunkZ)) {
                    return w.getChunkAt(chunkX, chunkZ);
                }
            }
        }
        return null;
    }

    private static Map<String, Object> collectChunkData(Chunk chunk) {
        Map<String, Object> data = new HashMap<>();

        int livingEntityCount = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity) {
                livingEntityCount++;
            }
        }
        data.put("entities", livingEntityCount); // int will be auto-boxed to Integer (Object)

        Map<Material, Integer> tileCounts = new EnumMap<>(Material.class);
        Material[] tileEntitiesToCount = {
                Material.HOPPER, Material.CHEST, Material.TRAPPED_CHEST,
                Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
                Material.BARREL, Material.DISPENSER, Material.DROPPER
        };
        for(Material mat : tileEntitiesToCount) tileCounts.put(mat, 0);
        int shulkerBoxCount = 0;

        for (BlockState blockState : chunk.getTileEntities()) {
            Material type = blockState.getType();
            tileCounts.computeIfPresent(type, (key, val) -> val + 1);
            if (Tag.SHULKER_BOXES.isTagged(type)) {
                shulkerBoxCount++;
            }
        }

        data.put("hoppers", tileCounts.getOrDefault(Material.HOPPER, 0));
        data.put("chests", tileCounts.getOrDefault(Material.CHEST, 0) + tileCounts.getOrDefault(Material.TRAPPED_CHEST, 0));
        data.put("furnaces", tileCounts.getOrDefault(Material.FURNACE, 0) +
                tileCounts.getOrDefault(Material.BLAST_FURNACE, 0) +
                tileCounts.getOrDefault(Material.SMOKER, 0));
        data.put("shulker_boxes", shulkerBoxCount);
        data.put("barrels", tileCounts.getOrDefault(Material.BARREL, 0));
        data.put("dispensers_droppers", tileCounts.getOrDefault(Material.DISPENSER, 0) + tileCounts.getOrDefault(Material.DROPPER, 0));

        return data;
    }

    private static boolean isNumeric(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(input);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}