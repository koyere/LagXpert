package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Filters console logging to prevent spam.
 * Injects a filter into the root logger.
 */
public class ConsoleFilter implements Filter {

    private boolean enabled;
    private List<Pattern> patterns;
    private boolean forwardToAdmins;
    private String forwardPermission;
    private boolean initialized = false;

    public ConsoleFilter() {
        reloadConfig();
    }

    public void reloadConfig() {
        File file = new File(LagXpert.getInstance().getDataFolder(), "console-filter.yml");
        if (!file.exists()) {
            // Default config handled by saveDefaultConfigurations? Or create manually here?
            // Assuming created by saveDefaultConfigurations or similar step.
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        this.enabled = config.getBoolean("enabled", true);
        this.forwardToAdmins = config.getBoolean("forward-to-admins", false);
        this.forwardPermission = config.getString("forward-permission", "lagxpert.console.view-filtered");

        this.patterns = new ArrayList<>();
        List<String> regexList = config.getStringList("filters");
        for (String regex : regexList) {
            try {
                patterns.add(Pattern.compile(regex));
            } catch (Exception e) {
                LagXpert.getInstance().getLogger().warning("[ConsoleFilter] Invalid regex pattern: " + regex);
            }
        }

        if (enabled && !initialized) {
            injectFilter();
            initialized = true;
        } else if (!enabled && initialized) {
            removeFilter();
            initialized = false;
        }
    }

    private void injectFilter() {
        getLogger().setFilter(this);
        LagXpert.getInstance().getLogger().info("[ConsoleFilter] Injected into root logger.");
    }

    private void removeFilter() {
        if (getLogger().getFilter() == this) {
            getLogger().setFilter(null);
        }
    }

    private Logger getLogger() {
        return Bukkit.getLogger();
    }

    @Override
    public boolean isLoggable(LogRecord record) {
        if (!enabled)
            return true;

        String message = record.getMessage();
        if (message == null)
            return true;

        for (Pattern pattern : patterns) {
            if (pattern.matcher(message).matches()) {
                if (forwardToAdmins) {
                    forwardMessageToAdmins(message);
                }
                return false; // Filter out (hide)
            }
        }

        return true; // Pass through
    }

    private void forwardMessageToAdmins(String message) {
        // Maybe run async to not block logging thread?
        // Logging is usually sync? Buikit might not be thread safe here if logging from
        // async thread.
        // But for sendMessage it should be fine mostly or schedule it.

        // Simple implementation:
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(forwardPermission)) {
                p.sendMessage(MessageManager.color("&8[Filtered] " + message));
            }
        }
    }

    public void shutdown() {
        removeFilter();
    }
}
