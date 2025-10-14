package me.koyere.lagxpert.tasks;

import me.koyere.lagxpert.system.ChunkManager;
import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Periodically purges stale chunk activity data to keep memory usage predictable.
 */
public class ChunkActivityCleanupTask extends BukkitRunnable {

    @Override
    public void run() {
        if (!ConfigManager.isChunkManagementModuleEnabled() || !ConfigManager.isChunkActivityTrackingEnabled()) {
            return;
        }

        ChunkManager.cleanupOldActivity();
    }
}

