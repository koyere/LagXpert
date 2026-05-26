package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Filters console logging to prevent spam.
 * Injects a filter into the root logger.
 *
 * Fixed in Phase 2:
 *   - Thread-safe: CopyOnWriteArrayList for patterns, AtomicBoolean for state.
 *   - Chain of responsibility: preserves existing filter if present.
 *   - Hot-reloadable patterns without removing/re-injecting filter.
 *   - Forwarding uses async scheduling to avoid blocking logger thread.
 *   - Proper cleanup on shutdown restoring original filter.
 */
public class ConsoleFilter implements Filter {

    private volatile boolean enabled;
    private final List<Pattern> patterns = new CopyOnWriteArrayList<>();
    private final AtomicBoolean injected = new AtomicBoolean(false);
    private boolean forwardToAdmins;
    private String forwardPermission;

    private Filter chainedFilter;

    public ConsoleFilter() {
        reloadConfig();
    }

    public void reloadConfig() {
        File file = new File(LagXpert.getInstance().getDataFolder(), "console-filter.yml");
        if (!file.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        boolean wasEnabled = this.enabled;
        this.enabled = config.getBoolean("enabled", true);
        this.forwardToAdmins = config.getBoolean("forward-to-admins", false);
        this.forwardPermission = config.getString("forward-permission", "lagxpert.console.view-filtered");

        // Reload patterns (thread-safe Clear + add)
        this.patterns.clear();
        List<String> regexList = config.getStringList("filters");
        for (String regex : regexList) {
            try {
                patterns.add(Pattern.compile(regex));
            } catch (PatternSyntaxException e) {
                LagXpert.getInstance().getLogger().warning(
                        "[ConsoleFilter] Invalid regex pattern: " + regex + " — " + e.getMessage());
            }
        }

        // Handle injection state changes
        if (enabled && !injected.get()) {
            injectFilter();
        } else if (!enabled && injected.get()) {
            removeFilter();
        }
    }

    private void injectFilter() {
        Logger rootLogger = getLogger();

        // Preserve existing filter for chain of responsibility
        Filter existingFilter = rootLogger.getFilter();
        if (existingFilter != null && existingFilter != this) {
            this.chainedFilter = existingFilter;
        }

        rootLogger.setFilter(this);
        injected.set(true);
        LagXpert.getInstance().getLogger().info(
                "[ConsoleFilter] Injected into root logger. " + patterns.size() + " patterns active.");
    }

    private void removeFilter() {
        if (getLogger().getFilter() == this) {
            // Restore chained filter if it existed
            getLogger().setFilter(chainedFilter);
            chainedFilter = null;
            injected.set(false);
        }
    }

    private Logger getLogger() {
        return Bukkit.getLogger();
    }

    @Override
    public boolean isLoggable(LogRecord record) {
        if (!enabled) {
            return passThrough(record);
        }

        String message = record.getMessage();
        if (message == null) {
            return passThrough(record);
        }

        for (Pattern pattern : patterns) {
            if (pattern.matcher(message).matches()) {
                // Forward filtered message to admins if configured
                if (forwardToAdmins) {
                    // Schedule async to avoid blocking the logger thread
                    final String msg = message;
                    Bukkit.getScheduler().runTask(LagXpert.getInstance(),
                            () -> forwardMessageToAdmins(msg));
                }

                // Still pass through chained filter
                if (chainedFilter != null) {
                    return chainedFilter.isLoggable(record);
                }
                return false; // Filtered out
            }
        }

        return passThrough(record);
    }

    /**
     * Passes the log record through the chained filter if present.
     */
    private boolean passThrough(LogRecord record) {
        if (chainedFilter != null) {
            return chainedFilter.isLoggable(record);
        }
        return true;
    }

    private void forwardMessageToAdmins(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(forwardPermission)) {
                p.sendMessage(MessageManager.color("&8[Filtered] &7" + message));
            }
        }
    }

    public void shutdown() {
        removeFilter();
    }
}
