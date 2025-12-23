package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import org.bukkit.Chunk;
import org.bukkit.World;
// import org.bukkit.block.Bloc; // Typo fix
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.block.BlockExplodeEvent;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Optimizes explosions by limiting radius, preventing massive chain reactions,
 * and controlling block drops.
 */
public class ExplosionController implements Listener {

    private boolean enabled;
    private boolean preventChainReaction;
    private int maxPrimedTntPerChunk;
    private boolean disableExplosionDrops;
    private double dropChance;
    private Set<String> disabledWorlds;

    // Radius limits
    private double maxTntRadius;
    private double maxCreeperRadius;
    private double maxCrystalRadius;
    private double maxWitherRadius;
    private double maxOtherRadius;

    public ExplosionController() {
        reloadConfig();
    }

    public void reloadConfig() {
        File file = new File(LagXpert.getInstance().getDataFolder(), "explosions.yml");
        if (!file.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        this.enabled = config.getBoolean("enabled", true);
        this.preventChainReaction = config.getBoolean("settings.prevent-chain-reaction", true);
        this.maxPrimedTntPerChunk = config.getInt("settings.max-primed-tnt-per-chunk", 20);
        this.disableExplosionDrops = config.getBoolean("settings.disable-explosion-drops", false);
        this.dropChance = config.getDouble("settings.drop-chance", 0.3);

        this.disabledWorlds = new HashSet<>();
        List<String> worlds = config.getStringList("disabled-worlds");
        for (String w : worlds) {
            this.disabledWorlds.add(w.toLowerCase());
        }

        this.maxTntRadius = config.getDouble("radius-limits.tnt", 4.0);
        this.maxCreeperRadius = config.getDouble("radius-limits.creeper", 3.0);
        this.maxCrystalRadius = config.getDouble("radius-limits.crystal", 6.0);
        this.maxWitherRadius = config.getDouble("radius-limits.wither", 7.0);
        this.maxOtherRadius = config.getDouble("radius-limits.other", 4.0);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (!enabled || isDisabledWorld(event.getEntity().getWorld()))
            return;

        Entity entity = event.getEntity();
        double radius = event.getRadius();
        double maxRadius = maxOtherRadius;

        if (entity instanceof TNTPrimed) {
            maxRadius = maxTntRadius;
        } else if (entity instanceof Creeper) {
            maxRadius = maxCreeperRadius;
        } else if (entity instanceof EnderCrystal) {
            maxRadius = maxCrystalRadius;
        } else if (entity instanceof Wither || entity instanceof WitherSkull) {
            maxRadius = maxWitherRadius;
        }

        if (radius > maxRadius) {
            event.setRadius((float) maxRadius);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!enabled || isDisabledWorld(event.getEntity().getWorld()))
            return;

        // Handle drops
        if (disableExplosionDrops) {
            event.setYield(0.0f);
        } else if (dropChance < 1.0) {
            event.setYield((float) dropChance);
        }

        // Prevent chain reactions
        if (preventChainReaction && event.getEntity() instanceof TNTPrimed) {
            Chunk chunk = event.getLocation().getChunk();
            long tntCount = Arrays.stream(chunk.getEntities())
                    .filter(e -> e instanceof TNTPrimed)
                    .count();

            if (tntCount > maxPrimedTntPerChunk) {
                event.setCancelled(true); // Don't explode if too many are already primed
                // Ideally we remove it, but cancelling safe-guards against calculations
            }
        }
    }

    // Also handle block explosions (like beds in nether)
    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!enabled || isDisabledWorld(event.getBlock().getWorld()))
            return;

        if (disableExplosionDrops) {
            event.setYield(0.0f);
        } else if (dropChance < 1.0) {
            event.setYield((float) dropChance);
        }
    }

    private boolean isDisabledWorld(World world) {
        return disabledWorlds.contains(world.getName().toLowerCase());
    }
}
