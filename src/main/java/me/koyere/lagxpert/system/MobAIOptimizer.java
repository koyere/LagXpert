package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Optimizes server performance by managing entity AI.
 * Can disable AI for specific mob types, in specific worlds, or based on
 * distance.
 */
public class MobAIOptimizer {

    private static MobAIOptimizer instance;
    private boolean enabled;
    private Set<String> disabledAiTypes;
    private Set<String> disabledAiWorlds;
    private boolean distanceOptimizationEnabled;
    private int distanceThreshold;

    private MobAIOptimizer() {
        reloadConfig();
    }

    public static MobAIOptimizer getInstance() {
        if (instance == null) {
            instance = new MobAIOptimizer();
        }
        return instance;
    }

    public void reloadConfig() {
        File mobsFile = new File(LagXpert.getInstance().getDataFolder(), "mobs.yml");
        if (!mobsFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(mobsFile);
        this.enabled = config.getBoolean("ai-optimizer.enabled", true);

        this.disabledAiTypes = new HashSet<>();
        List<String> types = config.getStringList("ai-optimizer.disable-ai-types");
        for (String type : types) {
            this.disabledAiTypes.add(type.toUpperCase());
        }

        this.disabledAiWorlds = new HashSet<>();
        List<String> worlds = config.getStringList("ai-optimizer.disable-ai-worlds");
        for (String world : worlds) {
            this.disabledAiWorlds.add(world.toLowerCase());
        }

        this.distanceOptimizationEnabled = config.getBoolean("ai-optimizer.distance-optimization.enabled", true);
        this.distanceThreshold = config.getInt("ai-optimizer.distance-optimization.distance-threshold", 64);
    }

    /**
     * Checks if the entity should have its AI disabled based on config.
     * Call this on CreatureSpawnEvent or when loading entities.
     */
    public void optimizeEntity(LivingEntity entity) {
        if (!enabled)
            return;
        if (entity instanceof Player)
            return;
        if (entity.hasMetadata("NPC"))
            return; // Don't mess with Citizens NPCs etc

        // Check if world is disabled
        if (disabledAiWorlds.contains(entity.getWorld().getName().toLowerCase())) {
            entity.setAI(false);
            return;
        }

        // Check if entity type is disabled
        if (disabledAiTypes.contains(entity.getType().name().toUpperCase())) {
            entity.setAI(false);
            return;
        }

        // Distance optimization usually happens in a periodic task,
        // but we can check initial spawn if needed, though usually spawn happens near
        // players.
    }

    /**
     * Checks distance to nearest player and toggles AI accordingly.
     * To be called from a periodic task for active chunks.
     */
    public void checkDistanceOptimization(LivingEntity entity) {
        if (!enabled || !distanceOptimizationEnabled)
            return;
        if (entity instanceof Player)
            return;
        if (!entity.isValid())
            return;

        // If explicitly disabled by type/world, keep it disabled
        if (disabledAiTypes.contains(entity.getType().name().toUpperCase()) ||
                disabledAiWorlds.contains(entity.getWorld().getName().toLowerCase())) {
            if (entity.hasAI())
                entity.setAI(false);
            return;
        }

        Player nearest = findNearestPlayer(entity);
        if (nearest == null) {
            // No players in world? Disable AI to be safe/efficient
            if (entity.hasAI())
                entity.setAI(false);
            return;
        }

        double distanceSq = entity.getLocation().distanceSquared(nearest.getLocation());
        double thresholdSq = distanceThreshold * distanceThreshold;

        if (distanceSq > thresholdSq) {
            if (entity.hasAI())
                entity.setAI(false);
        } else {
            // Re-enable AI if player comes close
            if (!entity.hasAI())
                entity.setAI(true);
        }
    }

    private Player findNearestPlayer(Entity entity) {
        World world = entity.getWorld();
        Player nearest = null;
        double minDstSq = Double.MAX_VALUE;

        for (Player p : world.getPlayers()) {
            double dstSq = p.getLocation().distanceSquared(entity.getLocation());
            if (dstSq < minDstSq) {
                minDstSq = dstSq;
                nearest = p;
            }
        }
        return nearest;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
