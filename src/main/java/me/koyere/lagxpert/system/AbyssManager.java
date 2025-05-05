package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Stores recently removed items in memory so players can recover them for a short time.
 */
public class AbyssManager {

    private static final Map<UUID, List<AbyssEntry>> abyss = new HashMap<>();
    private static boolean enabled;
    private static int retentionSeconds;
    private static int maxItemsPerPlayer;
    private static String recoverMessage;
    private static String emptyMessage;

    public static void loadConfig() {
        FileConfiguration config = LagXpert.loadYaml(LagXpert.getInstance().getDataFolder().toPath().resolve("itemcleaner.yml").toFile());

        enabled = config.getBoolean("abyss.enabled", true);
        retentionSeconds = config.getInt("abyss.retention-seconds", 120);
        maxItemsPerPlayer = config.getInt("abyss.max-items-per-player", 30);
        recoverMessage = config.getString("abyss.recover-message", "&aYou recovered &f{count} &aitem(s) from the abyss.");
        emptyMessage = config.getString("abyss.empty-message", "&7You have no items to recover.");
    }

    public static void add(Item item) {
        if (!enabled) return;
        if (item.getThrower() == null) return;

        UUID uuid = item.getThrower();
        abyss.putIfAbsent(uuid, new LinkedList<>());

        List<AbyssEntry> list = abyss.get(uuid);

        // Limit to max items
        if (list.size() >= maxItemsPerPlayer) {
            list.remove(0);
        }

        list.add(new AbyssEntry(item.getItemStack().clone(), System.currentTimeMillis()));
    }

    public static void tryRecover(Player player) {
        if (!enabled) return;

        List<AbyssEntry> list = abyss.getOrDefault(player.getUniqueId(), new ArrayList<>());
        if (list.isEmpty()) {
            player.sendMessage(emptyMessage);
            return;
        }

        long now = System.currentTimeMillis();
        int recovered = 0;
        Iterator<AbyssEntry> it = list.iterator();

        while (it.hasNext()) {
            AbyssEntry entry = it.next();
            if ((now - entry.timestamp) > (retentionSeconds * 1000L)) {
                it.remove();
                continue;
            }

            player.getInventory().addItem(entry.item.clone());
            recovered++;
            it.remove();
        }

        if (recovered > 0) {
            player.sendMessage(recoverMessage.replace("{count}", String.valueOf(recovered)));
        } else {
            player.sendMessage(emptyMessage);
        }
    }

    private static class AbyssEntry {
        private final ItemStack item;
        private final long timestamp;

        public AbyssEntry(ItemStack item, long timestamp) {
            this.item = item;
            this.timestamp = timestamp;
        }
    }
}
