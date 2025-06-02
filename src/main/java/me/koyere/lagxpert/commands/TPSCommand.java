package me.koyere.lagxpert.commands;

import me.koyere.lagxpert.monitoring.PerformanceTracker;
import me.koyere.lagxpert.monitoring.TPSMonitor;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles the /tps command for displaying server performance information.
 * Provides real-time TPS data, memory usage, chunk statistics, and lag spike information.
 * Supports different detail levels and administrative functions.
 */
public class TPSCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("summary", "detailed", "memory", "chunks", "lagspikes", "history", "reset");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isMonitoringModuleEnabled()) {
            sender.sendMessage(MessageManager.getPrefixedMessage("general.command-not-implemented"));
            return true;
        }

        // Permission check
        if (!sender.hasPermission("lagxpert.tps")) {
            sender.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
            return true;
        }

        // Default to summary if no arguments
        if (args.length == 0) {
            showTPSSummary(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "summary":
                showTPSSummary(sender);
                break;
            case "detailed":
                showDetailedInfo(sender);
                break;
            case "memory":
                showMemoryInfo(sender);
                break;
            case "chunks":
                showChunkInfo(sender);
                break;
            case "lagspikes":
                showLagSpikeInfo(sender);
                break;
            case "history":
                showPerformanceHistory(sender);
                break;
            case "reset":
                if (!sender.hasPermission("lagxpert.admin")) {
                    sender.sendMessage(MessageManager.getPrefixedMessage("general.no-permission"));
                    return true;
                }
                resetStatistics(sender);
                break;
            default:
                sender.sendMessage(MessageManager.getPrefixedMessage("general.invalid-command"));
                showTPSHelp(sender);
                break;
        }

        return true;
    }

    /**
     * Shows a concise TPS summary with color-coded performance indicators.
     */
    private void showTPSSummary(CommandSender sender) {
        StringBuilder message = new StringBuilder();

        message.append(MessageManager.color("&8&m------------------------------------------\n"));
        message.append(MessageManager.color("&b&lServer Performance Summary\n"));
        message.append(MessageManager.color("&8&m------------------------------------------\n"));

        // TPS Information with color coding
        double currentTPS = TPSMonitor.getCurrentTPS();
        double shortTermTPS = TPSMonitor.getShortTermTPS();
        double mediumTermTPS = TPSMonitor.getMediumTermTPS();
        double longTermTPS = TPSMonitor.getLongTermTPS();

        message.append(MessageManager.color("&f• &eTPS: "))
                .append(formatTPS("Current", currentTPS))
                .append(MessageManager.color(" &7| "))
                .append(formatTPS("1m", shortTermTPS))
                .append(MessageManager.color(" &7| "))
                .append(formatTPS("5m", mediumTermTPS))
                .append(MessageManager.color(" &7| "))
                .append(formatTPS("15m", longTermTPS))
                .append("\n");

        // Memory Information
        double memoryUsage = PerformanceTracker.getCurrentMemoryUsage();
        long usedMemoryMB = PerformanceTracker.getUsedMemory() / 1024 / 1024;
        long totalMemoryMB = PerformanceTracker.getTotalMemory() / 1024 / 1024;

        message.append(MessageManager.color("&f• &eMemory: "))
                .append(formatMemory(memoryUsage))
                .append(MessageManager.color(" &7(&f"))
                .append(usedMemoryMB)
                .append(MessageManager.color("&7/&f"))
                .append(totalMemoryMB)
                .append(MessageManager.color("&7 MB)\n"));

        // Chunk Information
        long chunksLoaded = PerformanceTracker.getTotalChunksLoaded();
        int chunkLoadingRate = PerformanceTracker.getChunkLoadingRate();

        message.append(MessageManager.color("&f• &eChunks: &f"))
                .append(chunksLoaded)
                .append(MessageManager.color(" &7loaded &7(&f"))
                .append(chunkLoadingRate)
                .append(MessageManager.color("&7/min)\n"));

        // Tick Time Information
        double avgTickTime = TPSMonitor.getAverageTickTime();
        double maxTickTime = TPSMonitor.getMaxTickTime();

        message.append(MessageManager.color("&f• &eTick Time: &f"))
                .append(String.format("%.2f", avgTickTime))
                .append(MessageManager.color("&7ms avg &7(&fMax: "))
                .append(String.format("%.2f", maxTickTime))
                .append(MessageManager.color("&7ms)\n"));

        message.append(MessageManager.color("&8&m------------------------------------------"));

        sender.sendMessage(message.toString());
    }

    /**
     * Shows detailed performance information including lag spikes and performance state.
     */
    private void showDetailedInfo(CommandSender sender) {
        StringBuilder message = new StringBuilder();

        message.append(MessageManager.color("&8&m------------------------------------------\n"));
        message.append(MessageManager.color("&b&lDetailed Performance Information\n"));
        message.append(MessageManager.color("&8&m------------------------------------------\n"));

        // Basic TPS info
        message.append(MessageManager.color("&6TPS Information:\n"));
        message.append(MessageManager.color("&f  Current: ")).append(formatTPS("", TPSMonitor.getCurrentTPS())).append("\n");
        message.append(MessageManager.color("&f  1 minute: ")).append(formatTPS("", TPSMonitor.getShortTermTPS())).append("\n");
        message.append(MessageManager.color("&f  5 minutes: ")).append(formatTPS("", TPSMonitor.getMediumTermTPS())).append("\n");
        message.append(MessageManager.color("&f  15 minutes: ")).append(formatTPS("", TPSMonitor.getLongTermTPS())).append("\n");

        // Tick information
        message.append(MessageManager.color("&6Tick Information:\n"));
        message.append(MessageManager.color("&f  Average: &e")).append(String.format("%.2f", TPSMonitor.getAverageTickTime())).append("ms\n");
        message.append(MessageManager.color("&f  Maximum: &e")).append(String.format("%.2f", TPSMonitor.getMaxTickTime())).append("ms\n");
        message.append(MessageManager.color("&f  Minimum: &e")).append(String.format("%.2f", TPSMonitor.getMinTickTime())).append("ms\n");
        message.append(MessageManager.color("&f  Total Ticks: &e")).append(TPSMonitor.getTotalTicks()).append("\n");

        // Recent lag spikes
        List<TPSMonitor.LagSpike> recentSpikes = TPSMonitor.getRecentLagSpikes();
        message.append(MessageManager.color("&6Recent Lag Spikes: &e")).append(recentSpikes.size()).append("\n");

        if (!recentSpikes.isEmpty() && recentSpikes.size() > 0) {
            // Show last 3 lag spikes
            int spikesToShow = Math.min(3, recentSpikes.size());
            for (int i = recentSpikes.size() - spikesToShow; i < recentSpikes.size(); i++) {
                TPSMonitor.LagSpike spike = recentSpikes.get(i);
                message.append(MessageManager.color("&f  • &c")).append(String.format("%.2f", spike.getTickTime()))
                        .append(MessageManager.color("&7ms - ")).append(spike.getPossibleCause()).append("\n");
            }
        }

        message.append(MessageManager.color("&8&m------------------------------------------"));
        sender.sendMessage(message.toString());
    }

    /**
     * Shows detailed memory usage information.
     */
    private void showMemoryInfo(CommandSender sender) {
        StringBuilder message = new StringBuilder();

        message.append(MessageManager.color("&8&m------------------------------------------\n"));
        message.append(MessageManager.color("&b&lMemory Usage Information\n"));
        message.append(MessageManager.color("&8&m------------------------------------------\n"));

        double currentUsage = PerformanceTracker.getCurrentMemoryUsage();
        double maxUsage = PerformanceTracker.getMaxMemoryUsage();
        long usedMB = PerformanceTracker.getUsedMemory() / 1024 / 1024;
        long totalMB = PerformanceTracker.getTotalMemory() / 1024 / 1024;
        long freeMB = PerformanceTracker.getFreeMemory() / 1024 / 1024;

        message.append(MessageManager.color("&f• &eCurrent Usage: ")).append(formatMemory(currentUsage)).append("\n");
        message.append(MessageManager.color("&f• &ePeak Usage: ")).append(formatMemory(maxUsage)).append("\n");
        message.append(MessageManager.color("&f• &eUsed Memory: &f")).append(usedMB).append(" MB\n");
        message.append(MessageManager.color("&f• &eFree Memory: &f")).append(freeMB).append(" MB\n");
        message.append(MessageManager.color("&f• &eTotal Memory: &f")).append(totalMB).append(" MB\n");

        // Memory status
        String memoryStatus;
        if (currentUsage >= ConfigManager.getMemoryCriticalThreshold()) {
            memoryStatus = "&c&lCRITICAL";
        } else if (currentUsage >= ConfigManager.getMemoryWarningThreshold()) {
            memoryStatus = "&e&lWARNING";
        } else {
            memoryStatus = "&a&lGOOD";
        }

        message.append(MessageManager.color("&f• &eStatus: ")).append(MessageManager.color(memoryStatus)).append("\n");

        message.append(MessageManager.color("&8&m------------------------------------------"));
        sender.sendMessage(message.toString());
    }

    /**
     * Shows chunk loading and management information.
     */
    private void showChunkInfo(CommandSender sender) {
        StringBuilder message = new StringBuilder();

        message.append(MessageManager.color("&8&m------------------------------------------\n"));
        message.append(MessageManager.color("&b&lChunk Information\n"));
        message.append(MessageManager.color("&8&m------------------------------------------\n"));

        long totalChunks = PerformanceTracker.getTotalChunksLoaded();
        int loadingRate = PerformanceTracker.getChunkLoadingRate();
        int maxChunksWarning = ConfigManager.getMaxLoadedChunksWarning();

        message.append(MessageManager.color("&f• &eTotal Loaded: &f")).append(totalChunks).append("\n");
        message.append(MessageManager.color("&f• &eLoading Rate: &f")).append(loadingRate).append(" chunks/min\n");
        message.append(MessageManager.color("&f• &eWarning Threshold: &f")).append(maxChunksWarning).append("\n");

        // Chunk status
        String chunkStatus;
        if (totalChunks > maxChunksWarning) {
            chunkStatus = "&c&lHIGH";
        } else if (totalChunks > maxChunksWarning * 0.8) {
            chunkStatus = "&e&lMODERATE";
        } else {
            chunkStatus = "&a&lNORMAL";
        }

        message.append(MessageManager.color("&f• &eStatus: ")).append(MessageManager.color(chunkStatus)).append("\n");

        message.append(MessageManager.color("&8&m------------------------------------------"));
        sender.sendMessage(message.toString());
    }

    /**
     * Shows lag spike information and analysis.
     */
    private void showLagSpikeInfo(CommandSender sender) {
        List<TPSMonitor.LagSpike> lagSpikes = TPSMonitor.getRecentLagSpikes();

        StringBuilder message = new StringBuilder();
        message.append(MessageManager.color("&8&m------------------------------------------\n"));
        message.append(MessageManager.color("&b&lLag Spike Information\n"));
        message.append(MessageManager.color("&8&m------------------------------------------\n"));

        if (lagSpikes.isEmpty()) {
            message.append(MessageManager.color("&a&lNo recent lag spikes detected!\n"));
        } else {
            message.append(MessageManager.color("&f• &eTotal Spikes: &f")).append(lagSpikes.size()).append("\n");

            // Show last 5 lag spikes
            int spikesToShow = Math.min(5, lagSpikes.size());
            message.append(MessageManager.color("&f• &eRecent Spikes (&f")).append(spikesToShow).append("&e):\n");

            for (int i = lagSpikes.size() - spikesToShow; i < lagSpikes.size(); i++) {
                TPSMonitor.LagSpike spike = lagSpikes.get(i);
                long timeAgo = (System.currentTimeMillis() - spike.getTimestamp()) / 1000;

                message.append(MessageManager.color("&f  • &c")).append(String.format("%.2f", spike.getTickTime()))
                        .append(MessageManager.color("&7ms &f(")).append(timeAgo).append("s ago) - ")
                        .append(spike.getPossibleCause()).append("\n");
            }
        }

        message.append(MessageManager.color("&8&m------------------------------------------"));
        sender.sendMessage(message.toString());
    }

    /**
     * Shows performance history and trends.
     */
    private void showPerformanceHistory(CommandSender sender) {
        List<PerformanceTracker.PerformanceSnapshot> history = PerformanceTracker.getPerformanceHistory();

        StringBuilder message = new StringBuilder();
        message.append(MessageManager.color("&8&m------------------------------------------\n"));
        message.append(MessageManager.color("&b&lPerformance History\n"));
        message.append(MessageManager.color("&8&m------------------------------------------\n"));

        if (history.isEmpty()) {
            message.append(MessageManager.color("&e&lNo performance history available yet.\n"));
        } else {
            message.append(MessageManager.color("&f• &eSnapshots: &f")).append(history.size()).append("\n");

            // Calculate averages from last 10 snapshots
            int snapshotsToAnalyze = Math.min(10, history.size());
            double avgTPS = 0;
            double avgMemory = 0;

            for (int i = history.size() - snapshotsToAnalyze; i < history.size(); i++) {
                PerformanceTracker.PerformanceSnapshot snapshot = history.get(i);
                avgTPS += snapshot.getTps();
                avgMemory += snapshot.getMemoryUsage();
            }

            avgTPS /= snapshotsToAnalyze;
            avgMemory /= snapshotsToAnalyze;

            message.append(MessageManager.color("&f• &eAverage TPS (last 10): ")).append(formatTPS("", avgTPS)).append("\n");
            message.append(MessageManager.color("&f• &eAverage Memory (last 10): ")).append(formatMemory(avgMemory)).append("\n");

            // Show trend
            if (history.size() >= 2) {
                PerformanceTracker.PerformanceSnapshot latest = history.get(history.size() - 1);
                PerformanceTracker.PerformanceSnapshot previous = history.get(history.size() - 2);

                double tpsTrend = latest.getTps() - previous.getTps();
                String trendIndicator = tpsTrend > 0 ? "&a↑" : tpsTrend < 0 ? "&c↓" : "&e→";

                message.append(MessageManager.color("&f• &eTrend: ")).append(MessageManager.color(trendIndicator))
                        .append(MessageManager.color(" &7(")).append(String.format("%.2f", Math.abs(tpsTrend)))
                        .append(MessageManager.color(" TPS)\n"));
            }
        }

        message.append(MessageManager.color("&8&m------------------------------------------"));
        sender.sendMessage(message.toString());
    }

    /**
     * Resets performance statistics (admin only).
     */
    private void resetStatistics(CommandSender sender) {
        PerformanceTracker.resetStatistics();
        TPSMonitor.resetStatistics();

        sender.sendMessage(MessageManager.getPrefixedMessage("general.config-reloaded")
                .replace("configuration has been reloaded", "performance statistics have been reset"));
    }

    /**
     * Shows help for the TPS command.
     */
    private void showTPSHelp(CommandSender sender) {
        StringBuilder help = new StringBuilder();
        help.append(MessageManager.color("&8&m------------------------------------------\n"));
        help.append(MessageManager.color("&b&lTPS Command Help\n"));
        help.append(MessageManager.color("&8&m------------------------------------------\n"));
        help.append(MessageManager.color("&e/tps &8- &7Show basic TPS summary\n"));
        help.append(MessageManager.color("&e/tps summary &8- &7Show TPS summary\n"));
        help.append(MessageManager.color("&e/tps detailed &8- &7Show detailed performance info\n"));
        help.append(MessageManager.color("&e/tps memory &8- &7Show memory usage details\n"));
        help.append(MessageManager.color("&e/tps chunks &8- &7Show chunk loading information\n"));
        help.append(MessageManager.color("&e/tps lagspikes &8- &7Show recent lag spikes\n"));
        help.append(MessageManager.color("&e/tps history &8- &7Show performance history\n"));

        if (sender.hasPermission("lagxpert.admin")) {
            help.append(MessageManager.color("&e/tps reset &8- &7Reset performance statistics\n"));
        }

        help.append(MessageManager.color("&8&m------------------------------------------"));
        sender.sendMessage(help.toString());
    }

    /**
     * Formats TPS values with appropriate colors.
     */
    private String formatTPS(String label, double tps) {
        String color;
        if (tps >= ConfigManager.getTPSGoodThreshold()) {
            color = "&a"; // Green for good TPS
        } else if (tps >= ConfigManager.getTPSWarningThreshold()) {
            color = "&e"; // Yellow for warning TPS
        } else {
            color = "&c"; // Red for critical TPS
        }

        String formattedTPS = MessageManager.color(color + String.format("%.2f", tps));
        return label.isEmpty() ? formattedTPS : MessageManager.color("&f" + label + ": " + formattedTPS);
    }

    /**
     * Formats memory usage values with appropriate colors.
     */
    private String formatMemory(double memoryPercent) {
        String color;
        if (memoryPercent >= ConfigManager.getMemoryCriticalThreshold()) {
            color = "&c"; // Red for critical memory
        } else if (memoryPercent >= ConfigManager.getMemoryWarningThreshold()) {
            color = "&e"; // Yellow for warning memory
        } else {
            color = "&a"; // Green for good memory
        }

        return MessageManager.color(color + String.format("%.1f%%", memoryPercent));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("lagxpert.tps")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String currentArg = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(sub -> {
                        // Filter admin commands
                        if (sub.equals("reset") && !sender.hasPermission("lagxpert.admin")) {
                            return false;
                        }
                        return sub.toLowerCase().startsWith(currentArg);
                    })
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}