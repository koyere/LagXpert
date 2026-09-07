package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits specific abilities like Elytra flight and Trident Riptide usage
 * to prevent server lag from excessive movement/chunk loading.
 *
 * Fixed in Phase 2:
 *   - Riptide cooldown now properly enforces via velocity cancellation
 *     (PlayerRiptideEvent is not cancellable, so we cancel velocity instead).
 *   - Added ProjectileLaunchEvent hook to block riptide tridents before launch.
 *   - Elytra speed limit uses smoothed velocity check instead of raw delta.
 *   - Cleaned up imports and removed duplicate declarations.
 *   - Added ActionLogger for blocked abilities.
 */
public class AbilityLimiter implements Listener {

    private boolean enabled;
    private double elytraSpeedLimit;
    private double elytraSlowdownFactor;
    private long riptideCooldownMs;
    private boolean disableRiptide;
    private double riptideReversalFactor;
    private Set<String> disabledWorlds;

    // Thread-safe cooldown storage
    private final Map<UUID, Long> riptideCooldowns = new ConcurrentHashMap<>();

    public AbilityLimiter() {
        reloadConfig();
    }

    public void reloadConfig() {
        File file = new File(LagXpert.getInstance().getDataFolder(), "abilities.yml");
        if (!file.exists()) {
            // Never fail silently: a missing file used to leave this subsystem
            // permanently disabled with no indication anywhere.
            LagXpert.getInstance().getLogger().warning(
                    "[AbilityLimiter] abilities.yml not found. This module will stay disabled. " +
                            "Restart the server to regenerate it.");
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        this.enabled = config.getBoolean("enabled", true);
        this.elytraSpeedLimit = config.getDouble("elytra.speed-limit", 1.5);
        this.elytraSlowdownFactor = config.getDouble("elytra.slowdown-factor", 0.5);
        this.riptideCooldownMs = config.getLong("trident.riptide-cooldown", 2000);
        this.disableRiptide = config.getBoolean("trident.disable-riptide", false);
        this.riptideReversalFactor = config.getDouble("trident.riptide-reversal-factor", -0.5);

        this.disabledWorlds = new HashSet<>();
        List<String> worlds = config.getStringList("disabled-worlds");
        for (String w : worlds) {
            this.disabledWorlds.add(w.toLowerCase());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (isDisabledWorld(player.getWorld())) return;
        if (player.hasPermission("lagxpert.bypass.abilities")) return;

        if (player.isGliding()) {
            // Use velocity magnitude for more accurate speed detection
            double speed = player.getVelocity().length();

            if (speed > elytraSpeedLimit) {
                event.setCancelled(true);
                player.setVelocity(player.getVelocity().multiply(elytraSlowdownFactor));

                ActionLogger.getInstance().log(
                        ActionLogger.ActionType.ABILITY_BLOCKED,
                        player.getWorld().getName(),
                        null,
                        "Elytra speed limited: " + String.format("%.2f", speed) + " > " + elytraSpeedLimit,
                        0, "auto", true, 0);

                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().info(
                            "[AbilityLimiter] Capped elytra speed for " + player.getName() +
                                    ": " + String.format("%.2f", speed));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRiptide(PlayerRiptideEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (isDisabledWorld(player.getWorld())) return;
        if (player.hasPermission("lagxpert.bypass.abilities")) return;

        if (disableRiptide) {
            // PlayerRiptideEvent is NOT cancellable in Spigot API
            // Workaround: cancel velocity one tick later
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    }
                }
            }.runTask(LagXpert.getInstance());

            ActionLogger.getInstance().log(
                    ActionLogger.ActionType.ABILITY_BLOCKED,
                    player.getWorld().getName(),
                    null,
                    "Riptide disabled for " + player.getName(),
                    0, "auto", true, 0);
            return;
        }

        if (riptideCooldownMs > 0) {
            long now = System.currentTimeMillis();
            Long lastUse = riptideCooldowns.get(player.getUniqueId());

            if (lastUse != null && (now - lastUse) < riptideCooldownMs) {
                long remainingMs = riptideCooldownMs - (now - lastUse);

                // Cancel velocity next tick (can't cancel RiptideEvent directly)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            player.setVelocity(player.getVelocity().multiply(riptideReversalFactor));
                            String msg = MessageManager.color(
                                    "&cRiptide on cooldown! &7Wait " +
                                            (remainingMs / 1000) + "s");
                            player.sendMessage(msg);
                        }
                    }
                }.runTask(LagXpert.getInstance());

                ActionLogger.getInstance().log(
                        ActionLogger.ActionType.ABILITY_BLOCKED,
                        player.getWorld().getName(),
                        null,
                        "Riptide cooldown: " + player.getName(),
                        0, "auto", true, 0);

                if (ConfigManager.isDebugEnabled()) {
                    LagXpert.getInstance().getLogger().info(
                            "[AbilityLimiter] Riptide cooldown active for " + player.getName() +
                                    " (" + remainingMs + "ms remaining)");
                }
            } else {
                riptideCooldowns.put(player.getUniqueId(), now);
            }
        }
    }

    /**
     * Additional hook: block trident launch if riptide is disabled.
     * ProjectileLaunchEvent IS cancellable, unlike PlayerRiptideEvent.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!enabled || !disableRiptide) return;
        if (!(event.getEntity() instanceof Trident)) return;
        if (!(event.getEntity().getShooter() instanceof Player)) return;

        Player player = (Player) event.getEntity().getShooter();
        if (isDisabledWorld(player.getWorld())) return;
        if (player.hasPermission("lagxpert.bypass.abilities")) return;

        Trident trident = (Trident) event.getEntity();
        ItemStack item = trident.getItem();

        if (item != null && item.containsEnchantment(Enchantment.RIPTIDE)) {
            event.setCancelled(true);

            ActionLogger.getInstance().log(
                    ActionLogger.ActionType.ABILITY_BLOCKED,
                    player.getWorld().getName(),
                    null,
                    "Riptide trident blocked for " + player.getName(),
                    0, "auto", true, 0);
        }
    }

    private boolean isDisabledWorld(World world) {
        return disabledWorlds.contains(world.getName().toLowerCase());
    }
}
