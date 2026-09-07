package me.koyere.lagxpert.gui;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.monitoring.TPSMonitor;
import me.koyere.lagxpert.system.ActionLogger;
import me.koyere.lagxpert.system.AdaptiveThresholdEngine;
import me.koyere.lagxpert.system.EmergencyController;
import me.koyere.lagxpert.system.EmergencyResponseCoordinator;
import me.koyere.lagxpert.system.LagDiagnosticsEngine;
import me.koyere.lagxpert.system.PerformanceHistory;
import me.koyere.lagxpert.system.ProfileManager;
import me.koyere.lagxpert.utils.BedrockPlayerUtils;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interactive lag diagnostics interface.
 *
 * Presents the {@link LagDiagnosticsEngine} report as a navigable set of screens:
 * an overview with plain-language conclusions, a ranked hotspot list, a per-chunk
 * drill-down with a teleport action, the corrective-action audit trail, and
 * historical trends.
 *
 * <h3>Bedrock compatibility</h3>
 * Bedrock clients render chest inventories through Geyser's translation layer and
 * do not tolerate everything Java does. This class therefore:
 * <ul>
 *   <li>Sizes every screen through {@link BedrockPlayerUtils#getSafeInventorySize}
 *       and computes its layout from that size rather than assuming six rows, so
 *       nothing is placed in a slot the client will never show.</li>
 *   <li>Uses only materials that exist across the whole supported version range
 *       and avoids spawn eggs, which Geyser renders inconsistently.</li>
 *   <li>Caps lore at ten lines, the point past which Bedrock starts truncating
 *       tooltips unpredictably.</li>
 *   <li>Uses legacy {@code &} colour codes only, never hex or components.</li>
 *   <li>Falls back to a complete chat report if the inventory cannot be opened,
 *       so a Bedrock player is never left with no way to read the diagnosis.</li>
 * </ul>
 *
 * <h3>Session handling</h3>
 * Per-player view state lives in this class rather than in
 * {@link GUIManager#hasActiveSession}, because navigating between screens closes
 * and reopens an inventory, which would otherwise end the manager's session on the
 * first click.
 */
public class DiagnosticsGUI implements Listener {

    /** Inventory title prefix, also used to recognise our own inventories. */
    private static final String TITLE_PREFIX = "LagXpert Diagnostics";

    private static final String TITLE_OVERVIEW = TITLE_PREFIX;
    private static final String TITLE_HOTSPOTS = TITLE_PREFIX + " - Hotspots";
    private static final String TITLE_DETAIL = TITLE_PREFIX + " - Chunk";
    private static final String TITLE_ACTIONS = TITLE_PREFIX + " - Actions";
    private static final String TITLE_TRENDS = TITLE_PREFIX + " - Trends";

    /** Maximum lore lines to emit; Bedrock truncates unpredictably beyond this. */
    private static final int MAX_LORE_LINES = 10;

    private enum Screen {
        OVERVIEW,
        HOTSPOTS,
        CHUNK_DETAIL,
        ACTIONS,
        TRENDS
    }

    /** Per-player view state. */
    private static class ViewState {
        Screen screen = Screen.OVERVIEW;
        int page = 0;
        int selectedChunkIndex = -1;
        LagDiagnosticsEngine.DiagnosticsReport report;
        /** Entry slot to hotspot index mapping for the currently rendered page. */
        final Map<Integer, Integer> slotToChunkIndex = new ConcurrentHashMap<>();
    }

    private static final Map<UUID, ViewState> viewStates = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────
    //  Entry points
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Opens the diagnostics interface, running a scan first if needed.
     *
     * @param player      the viewer
     * @param forceRescan ignore any cached report
     */
    public static void open(Player player, boolean forceRescan) {
        LagDiagnosticsEngine engine = LagDiagnosticsEngine.getInstance();

        LagDiagnosticsEngine.DiagnosticsReport cached =
                forceRescan ? null : engine.getCachedReport();

        if (cached != null) {
            renderOverview(player, cached);
            return;
        }

        player.sendMessage(MessageManager.getPrefixedMessage("diagnostics.scanning"));

        engine.requestReport(forceRescan, report -> {
            if (!player.isOnline()) {
                return;
            }
            if (report == null) {
                player.sendMessage(MessageManager.getPrefixedMessage("diagnostics.scan-in-progress"));
                return;
            }
            renderOverview(player, report);
        });
    }

    /**
     * Sends the full diagnosis as chat text.
     *
     * This is the console output path, the Bedrock fallback, and what
     * {@code /lagxpert diagnose} uses when the sender is not a player.
     */
    public static void sendChatReport(org.bukkit.command.CommandSender sender,
                                      LagDiagnosticsEngine.DiagnosticsReport report,
                                      int topCount) {
        String separator = MessageManager.color("&8&m------------------------------------------");

        sender.sendMessage(separator);
        sender.sendMessage(MessageManager.color("&b&lLagXpert Diagnostics"));
        sender.sendMessage(MessageManager.color(
                "&7Scanned &f" + report.getChunksScanned() + " &7chunks across &f" +
                        report.getWorldsScanned() + " &7world(s) in &f" + report.getScanDurationMs() + "ms"));
        sender.sendMessage("");

        // Server context
        sender.sendMessage(MessageManager.color("&e&lServer"));
        sender.sendMessage(MessageManager.color(
                "  &7State: " + stateColor(report.getServerState()) + report.getServerState()));
        sender.sendMessage(MessageManager.color(
                "  &7TPS: " + tpsColor(report.getTps()) + String.format("%.2f", report.getTps()) + " &7/ 20.00"));
        sender.sendMessage(MessageManager.color(
                "  &7Memory: " + memColor(report.getMemoryPercent()) +
                        String.format("%.1f%%", report.getMemoryPercent()) +
                        " &7(" + report.getUsedMemoryMb() + "MB / " + report.getMaxMemoryMb() + "MB)"));
        sender.sendMessage(MessageManager.color(
                "  &7Entities: &f" + report.getTotalEntities()));
        sender.sendMessage("");

        // The actual diagnosis
        sender.sendMessage(MessageManager.color("&e&lDiagnosis"));
        for (String observation : report.getObservations()) {
            for (String line : wrap(observation, 62)) {
                sender.sendMessage(MessageManager.color("  &f" + line));
            }
            sender.sendMessage("");
        }

        // Ranked hotspots
        List<LagDiagnosticsEngine.ChunkDiagnostic> top = report.getTopChunks(topCount);
        if (top.isEmpty()) {
            sender.sendMessage(MessageManager.color("&e&lHotspots"));
            sender.sendMessage(MessageManager.color("  &aNo problem chunks found."));
        } else {
            sender.sendMessage(MessageManager.color("&e&lWorst Chunks &7(top " + top.size() + ")"));
            int rank = 1;
            for (LagDiagnosticsEngine.ChunkDiagnostic chunk : top) {
                sender.sendMessage(MessageManager.color(
                        "  " + severityColor(chunk.getSeverity()) + "#" + rank + " &f" +
                                chunk.getWorldName() + " &7at &f" +
                                chunk.getCenterBlockX() + ", " + chunk.getCenterBlockZ() +
                                " &8(score " + Math.round(chunk.getScore()) + ")"));

                List<LagDiagnosticsEngine.MetricFinding> violations = chunk.getViolations();
                if (violations.isEmpty()) {
                    LagDiagnosticsEngine.MetricFinding primary = chunk.getPrimaryCause();
                    if (primary != null) {
                        sender.sendMessage(MessageManager.color(
                                "     &7" + primary.getMetric() + ": &f" + primary.getCount() +
                                        " &8/ " + primary.getLimit() + " (" + primary.getPercentOfLimit() + "%)"));
                    }
                } else {
                    for (LagDiagnosticsEngine.MetricFinding violation : violations) {
                        sender.sendMessage(MessageManager.color(
                                "     &c" + violation.getMetric() + ": &f" + violation.getCount() +
                                        " &8/ " + violation.getLimit() +
                                        " &c(" + violation.getPercentOfLimit() + "% of limit)"));
                    }
                }

                if (chunk.getInterventionCount() > 0) {
                    sender.sendMessage(MessageManager.color(
                            "     &8" + chunk.getInterventionCount() +
                                    " corrective action(s) here in the last hour"));
                }
                rank++;
            }
        }

        // Recent spikes give temporal context the chunk ranking cannot.
        if (!report.getRecentSpikes().isEmpty()) {
            sender.sendMessage("");
            sender.sendMessage(MessageManager.color("&e&lRecent Lag Spikes"));
            for (TPSMonitor.LagSpike spike : report.getRecentSpikes()) {
                sender.sendMessage(MessageManager.color(
                        "  &7" + formatAge(System.currentTimeMillis() - spike.getTimestamp()) +
                                " ago: &c" + String.format("%.0fms", spike.getTickTime()) +
                                " &8- " + spike.getPossibleCause()));
            }
        }

        sender.sendMessage(separator);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Screen rendering
    // ─────────────────────────────────────────────────────────────────────

    private static void renderOverview(Player player, LagDiagnosticsEngine.DiagnosticsReport report) {
        ViewState state = stateFor(player);
        state.screen = Screen.OVERVIEW;
        state.report = report;
        state.slotToChunkIndex.clear();

        int size = inventorySize(player);
        Inventory gui = Bukkit.createInventory(null, size, TITLE_OVERVIEW);

        Layout layout = new Layout(size);

        // Server state
        gui.setItem(SLOT_STATE, item(
                stateMaterial(report.getServerState()),
                t("state.title", "{color}&lServer State: {state}",
                        "color", stateColor(report.getServerState()),
                        "state", report.getServerState()),
                tList("state.lore", Arrays.asList(
                        "&7How LagXpert currently classifies",
                        "&7overall server health.",
                        "",
                        "&7Emergency AI freeze: {ai_freeze}",
                        "&7Blocking natural spawns: {block_spawns}"),
                        "ai_freeze", EmergencyResponseCoordinator.getInstance().isAiCurrentlyFrozen()
                                ? "&cactive" : "&aoff",
                        "block_spawns", EmergencyController.getInstance().shouldBlockNaturalSpawns()
                                ? "&cyes" : "&ano")));

        // TPS
        gui.setItem(SLOT_TPS, item(
                Material.CLOCK,
                t("tps.title", "{color}&lTPS: {tps}",
                        "color", tpsColor(report.getTps()),
                        "tps", String.format("%.2f", report.getTps())),
                tList("tps.lore", Arrays.asList(
                        "&7Ticks per second, target 20.00.",
                        "",
                        "&71 min:  &f{tps_1m}",
                        "&75 min:  &f{tps_5m}",
                        "&715 min: &f{tps_15m}",
                        "",
                        "&7Avg tick: &f{avg_tick}"),
                        "tps_1m", String.format("%.2f", TPSMonitor.getShortTermTPS()),
                        "tps_5m", String.format("%.2f", TPSMonitor.getMediumTermTPS()),
                        "tps_15m", String.format("%.2f", TPSMonitor.getLongTermTPS()),
                        "avg_tick", String.format("%.1fms", TPSMonitor.getAverageTickTime()))));

        // Memory
        gui.setItem(SLOT_MEMORY, item(
                Material.IRON_BLOCK,
                t("memory.title", "{color}&lMemory: {percent}",
                        "color", memColor(report.getMemoryPercent()),
                        "percent", String.format("%.1f%%", report.getMemoryPercent())),
                tList("memory.lore", Arrays.asList(
                        "&7Heap usage. Above 90% garbage",
                        "&7collection alone can cause lag.",
                        "",
                        "&7Used: &f{used} MB",
                        "&7Max:  &f{max} MB"),
                        "used", report.getUsedMemoryMb(),
                        "max", report.getMaxMemoryMb())));

        // Entities
        gui.setItem(SLOT_ENTITIES, item(
                Material.LEATHER,
                t("entities.title", "&e&lEntities: {count}",
                        "count", report.getTotalEntities()),
                buildEntityLore(report)));

        // Diagnosis: the plain-language conclusions
        gui.setItem(SLOT_DIAGNOSIS, item(
                Material.PAPER,
                t("diagnosis.title", "&b&lDiagnosis"),
                buildObservationLore(report)));

        // Adaptive limits
        AdaptiveThresholdEngine adaptive = AdaptiveThresholdEngine.getInstance();
        gui.setItem(SLOT_LIMITS, item(
                Material.COMPARATOR,
                t("limits.title", "{color}&lActive Limits",
                        "color", adaptive.isCurrentlyThrottling() ? "&e" : "&a"),
                tList("limits.lore", Arrays.asList(
                        "&7Limits currently being enforced,",
                        "&7as a percentage of your config.",
                        "",
                        "&7Health factor: &f{health}",
                        "&7Mobs: &f{mobs} &8| &7Storage: &f{storage}",
                        "&7Entities: &f{entities} &8| &7Redstone: &f{redstone}",
                        "",
                        "{summary}"),
                        "health", String.format("%.2f", adaptive.getHealthFactor()),
                        "mobs", percent(adaptive.getMobMultiplier()),
                        "storage", percent(adaptive.getStorageMultiplier()),
                        "entities", percent(adaptive.getEntityMultiplier()),
                        "redstone", percent(adaptive.getRedstoneMultiplier()),
                        "summary", adaptive.isCurrentlyThrottling()
                                ? "&eLimits are being tightened right now."
                                : "&aRunning at full configured limits.")));

        // Hotspots entry (navigation)
        int hotspotCount = report.getRankedChunks().size();
        gui.setItem(SLOT_HOTSPOTS, item(
                hotspotCount > 0 ? Material.REDSTONE_TORCH : Material.LIME_TERRACOTTA,
                t("hotspots.title", "{color}&lProblem Chunks: {count}",
                        "color", hotspotCount > 0 ? "&c" : "&a",
                        "count", hotspotCount),
                tList("hotspots.lore", Arrays.asList(
                        "&7Chunks ranked by how much",
                        "&7pressure they are creating.",
                        "",
                        "{hint}"),
                        "hint", hotspotCount > 0
                                ? t("hotspots.hint", "&eClick to see exactly where they are")
                                : t("hotspots.none", "&7Nothing to investigate."))));

        // Audit trail (navigation)
        gui.setItem(SLOT_ACTIONS, item(
                Material.BOOK,
                t("actions.title", "&6&lCorrective Actions"),
                tList("actions.lore", Arrays.asList(
                        "&7What LagXpert has actually done,",
                        "&7with timestamps.",
                        "",
                        "&7Logged total: &f{total}",
                        "&7Last hour: &f{recent}",
                        "",
                        "&eClick to view the audit trail"),
                        "total", ActionLogger.getInstance().getTotalActionsLogged(),
                        "recent", countRecentInterventions(report))));

        // Trends (navigation)
        gui.setItem(SLOT_TRENDS, item(
                Material.MAP,
                t("trends.title", "&d&lHistory & Trends"),
                tList("trends.lore", Arrays.asList(
                        "&7Long-term patterns: worst hour",
                        "&7of the day, peak players, growth.",
                        "",
                        "&7Snapshots stored: &f{snapshots}",
                        "",
                        "&eClick to view trends"),
                        "snapshots", PerformanceHistory.getInstance().getSnapshotCount())));

        addFooter(gui, layout, player, report, false, false);
        addBorder(gui, layout);

        openSafely(player, gui, report);
    }

    private static void renderHotspots(Player player, ViewState state) {
        LagDiagnosticsEngine.DiagnosticsReport report = state.report;
        if (report == null) {
            open(player, false);
            return;
        }

        state.screen = Screen.HOTSPOTS;
        state.slotToChunkIndex.clear();

        int size = inventorySize(player);
        Inventory gui = Bukkit.createInventory(null, size, TITLE_HOTSPOTS);
        Layout layout = new Layout(size);

        List<LagDiagnosticsEngine.ChunkDiagnostic> chunks = report.getRankedChunks();
        int perPage = layout.entryCapacity();
        int totalPages = Math.max(1, (int) Math.ceil(chunks.size() / (double) perPage));

        // Clamp the page in case the report shrank since the last render.
        if (state.page >= totalPages) {
            state.page = totalPages - 1;
        }
        if (state.page < 0) {
            state.page = 0;
        }

        int start = state.page * perPage;

        for (int i = 0; i < perPage; i++) {
            int index = start + i;
            if (index >= chunks.size()) {
                break;
            }
            LagDiagnosticsEngine.ChunkDiagnostic chunk = chunks.get(index);
            int slot = layout.entrySlot(i);

            gui.setItem(slot, item(
                    severityMaterial(chunk.getSeverity()),
                    severityColor(chunk.getSeverity()) + "&l#" + (index + 1) + " &f" +
                            chunk.getWorldName() + " &7" +
                            chunk.getCenterBlockX() + ", " + chunk.getCenterBlockZ(),
                    buildChunkSummaryLore(chunk)));

            state.slotToChunkIndex.put(slot, index);
        }

        boolean hasPrev = state.page > 0;
        boolean hasNext = state.page < totalPages - 1;

        // Page indicator
        gui.setItem(layout.footerSlot(4), item(
                Material.PAPER,
                "&7Page &f" + (state.page + 1) + " &7of &f" + totalPages,
                Arrays.asList(
                        "&7Showing &f" + Math.min(perPage, chunks.size() - start) +
                                " &7of &f" + chunks.size() + " &7problem chunks.",
                        "",
                        "&7Ranked by combined pressure:",
                        "&7how far over each limit, plus how",
                        "&7often we have had to intervene."
                )));

        addFooter(gui, layout, player, report, hasPrev, hasNext);
        addBorder(gui, layout);

        openSafely(player, gui, report);
    }

    private static void renderChunkDetail(Player player, ViewState state) {
        LagDiagnosticsEngine.DiagnosticsReport report = state.report;
        if (report == null || state.selectedChunkIndex < 0
                || state.selectedChunkIndex >= report.getRankedChunks().size()) {
            renderHotspots(player, state);
            return;
        }

        state.screen = Screen.CHUNK_DETAIL;
        state.slotToChunkIndex.clear();

        LagDiagnosticsEngine.ChunkDiagnostic chunk =
                report.getRankedChunks().get(state.selectedChunkIndex);

        int size = inventorySize(player);
        Inventory gui = Bukkit.createInventory(null, size, TITLE_DETAIL);
        Layout layout = new Layout(size);

        // Header describing the chunk itself
        gui.setItem(layout.slot(0), item(
                severityMaterial(chunk.getSeverity()),
                severityColor(chunk.getSeverity()) + "&l" + chunk.getSeverity().name() + " &8| &f" +
                        chunk.getWorldName(),
                Arrays.asList(
                        "&7Chunk: &f" + chunk.getChunkX() + ", " + chunk.getChunkZ(),
                        "&7Block: &f" + chunk.getCenterBlockX() + ", " + chunk.getCenterBlockZ(),
                        "",
                        "&7Score: &f" + Math.round(chunk.getScore()),
                        "&7Players nearby: " + (chunk.isPlayerNearby() ? "&eyes" : "&7no"),
                        "&7Interventions (1h): &f" + chunk.getInterventionCount()
                )));

        // Aggregate counts
        gui.setItem(layout.slot(1), item(
                Material.LEATHER,
                "&e&lContents",
                Arrays.asList(
                        "&7Total entities: &f" + chunk.getTotalEntities(),
                        "&7Living entities: &f" + chunk.getLivingEntities(),
                        "&7Tile entities: &f" + chunk.getTileEntities()
                )));

        // One item per measured metric, so the operator sees every contributor
        int slotIndex = 2;
        for (LagDiagnosticsEngine.MetricFinding finding : chunk.getFindings()) {
            if (slotIndex >= layout.headerCapacity()) {
                break;
            }
            boolean over = finding.isOverLimit();
            gui.setItem(layout.slot(slotIndex), item(
                    metricMaterial(finding.getMetric()),
                    (over ? "&c&l" : "&a&l") + capitalise(finding.getMetric()) +
                            ": " + finding.getCount() + " &7/ " + finding.getLimit(),
                    Arrays.asList(
                            "&7Using &f" + finding.getPercentOfLimit() + "% &7of the",
                            "&7limit currently in force.",
                            "",
                            over ? "&cOver the limit - this is a problem."
                                 : "&7Within the limit."
                    )));
            slotIndex++;
        }

        // Teleport action, gated by its own permission
        if (player.hasPermission("lagxpert.admin.diagnostics.teleport")
                || player.hasPermission("lagxpert.admin")) {
            gui.setItem(layout.footerSlot(2), item(
                    Material.ENDER_PEARL,
                    "&b&lTeleport Here",
                    Arrays.asList(
                            "&7Go to &f" + chunk.getCenterBlockX() + ", " + chunk.getCenterBlockZ(),
                            "&7in &f" + chunk.getWorldName(),
                            "",
                            "&7You will be placed at the highest",
                            "&7safe block above the chunk centre.",
                            "",
                            "&eClick to teleport"
                    )));
        }

        addFooter(gui, layout, player, report, false, false);
        addBorder(gui, layout);

        openSafely(player, gui, report);
    }

    private static void renderActions(Player player, ViewState state) {
        state.screen = Screen.ACTIONS;
        state.slotToChunkIndex.clear();

        int size = inventorySize(player);
        Inventory gui = Bukkit.createInventory(null, size, TITLE_ACTIONS);
        Layout layout = new Layout(size);

        List<ActionLogger.ActionRecord> records =
                ActionLogger.getInstance().getRecent(layout.entryCapacity());

        if (records.isEmpty()) {
            gui.setItem(layout.entrySlot(0), item(
                    Material.LIME_TERRACOTTA,
                    "&aNo corrective actions recorded",
                    Arrays.asList(
                            "&7LagXpert has not needed to",
                            "&7intervene since the last restart."
                    )));
        } else {
            int i = 0;
            for (ActionLogger.ActionRecord record : records) {
                if (i >= layout.entryCapacity()) {
                    break;
                }
                List<String> lore = new ArrayList<>();
                lore.add("&7When: &f" + formatAge(System.currentTimeMillis() - record.getTimestamp()) + " ago");
                lore.add("&7Count: &f" + record.getCount());
                lore.add("&7Trigger: &f" + safe(record.getTriggeredBy()));
                if (record.getWorld() != null) {
                    lore.add("&7World: &f" + record.getWorld());
                }
                if (record.getChunkKey() != null) {
                    lore.add("&7Chunk: &f" + record.getChunkKey());
                }
                if (record.getDetail() != null && !record.getDetail().isEmpty()) {
                    lore.add("");
                    for (String line : wrap(record.getDetail(), 34)) {
                        lore.add("&8" + line);
                    }
                }

                gui.setItem(layout.entrySlot(i), item(
                        actionMaterial(record.getType().name()),
                        (record.isSuccessful() ? "&f" : "&c") + prettyAction(record.getType().name()),
                        lore));
                i++;
            }
        }

        addFooter(gui, layout, player, state.report, false, false);
        addBorder(gui, layout);

        openSafely(player, gui, state.report);
    }

    private static void renderTrends(Player player, ViewState state) {
        state.screen = Screen.TRENDS;
        state.slotToChunkIndex.clear();

        int size = inventorySize(player);
        Inventory gui = Bukkit.createInventory(null, size, TITLE_TRENDS);
        Layout layout = new Layout(size);

        PerformanceHistory history = PerformanceHistory.getInstance();
        int snapshots = history.getSnapshotCount();

        if (snapshots == 0) {
            gui.setItem(layout.slot(0), item(
                    Material.PAPER,
                    "&7No history collected yet",
                    Arrays.asList(
                            "&7Snapshots are taken periodically.",
                            "&7Check back after the server has",
                            "&7been running for a while."
                    )));
        } else {
            int peakHour = history.getPeakLagHour();

            gui.setItem(layout.slot(0), item(
                    Material.CLOCK,
                    "&c&lWorst Hour: " + peakHour + ":00",
                    Arrays.asList(
                            "&7The hour of day with the",
                            "&7lowest average TPS.",
                            "",
                            "&7Average TPS then: &f" +
                                    String.format("%.2f", history.getAverageTpsForHour(peakHour)),
                            "&7Average players: &f" +
                                    String.format("%.1f", history.getAveragePlayersForHour(peakHour)),
                            "",
                            "&7Schedule restarts and heavy tasks",
                            "&7away from this window."
                    )));

            gui.setItem(layout.slot(1), item(
                    Material.PLAYER_HEAD,
                    "&e&lPeak Players: " + history.getPeakPlayerCount(),
                    Arrays.asList(
                            "&7Highest concurrent player count",
                            "&7observed in the stored history.",
                            "",
                            "&7Snapshots: &f" + snapshots
                    )));

            PerformanceHistory.TrendAnalysis entityTrend = history.getEntityTrend(6);
            gui.setItem(layout.slot(2), item(
                    trendMaterial(entityTrend.getDirection()),
                    "&b&lEntity Trend: " + entityTrend.getDirection(),
                    Arrays.asList(
                            "&7Direction of entity growth over",
                            "&7the last 6 hours.",
                            "",
                            "&7Current: &f" + Math.round(entityTrend.getCurrentValue()),
                            "&7Change/hour: &f" + String.format("%+.0f", entityTrend.getHourlyChange()),
                            "&7Projected 24h: &f" + Math.round(entityTrend.getProjected24h()),
                            "",
                            "&7A steadily rising count means",
                            "&7cleanup is not keeping pace."
                    )));
        }

        // Active profile, since it explains why limits are what they are
        ProfileManager profiles = ProfileManager.getInstance();
        String active = profiles.getActiveProfile();
        gui.setItem(layout.slot(3), item(
                Material.WRITABLE_BOOK,
                "&6&lProfile: " + (active == null ? "none" : active),
                Arrays.asList(
                        "&7Optimization profile currently applied.",
                        "",
                        active == null
                                ? "&7Using your own configuration."
                                : "&7Applied by &f" + safe(profiles.getAppliedBy()),
                        "",
                        "&7Change with &f/lagxpert profile <name>"
                )));

        addFooter(gui, layout, player, state.report, false, false);
        addBorder(gui, layout);

        openSafely(player, gui, state.report);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Layout
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Computes slot positions from the actual inventory size.
     *
     * Bedrock screens are clamped to fewer rows than Java ones, so nothing may
     * assume six rows. Every position is derived here instead.
     */
    private static class Layout {
        private final int size;
        private final int rows;

        Layout(int size) {
            this.size = size;
            this.rows = size / 9;
        }

        /** Rows available for content, excluding the top border and footer row. */
        private int contentRows() {
            return Math.max(1, rows - 2);
        }

        /** Slots usable for header-style items (row-major, columns 1..7). */
        int headerCapacity() {
            return contentRows() * 7;
        }

        /** Sequential content slot, skipping the border columns. */
        int slot(int index) {
            int row = 1 + (index / 7);
            int col = 1 + (index % 7);
            int result = row * 9 + col;
            return Math.min(result, size - 10);
        }

        /** Number of list entries that fit on one page. */
        int entryCapacity() {
            return headerCapacity();
        }

        int entrySlot(int index) {
            return slot(index);
        }

        /** Slot within the bottom row, 0..8. */
        int footerSlot(int column) {
            return size - 9 + Math.max(0, Math.min(8, column));
        }
    }

    private static void addFooter(Inventory gui, Layout layout, Player player,
                                 LagDiagnosticsEngine.DiagnosticsReport report,
                                 boolean hasPrev, boolean hasNext) {
        ViewState state = stateFor(player);

        // Back / close
        if (state.screen == Screen.OVERVIEW) {
            gui.setItem(layout.footerSlot(0), item(
                    Material.BARRIER,
                    t("close.title", "&cClose"),
                    tList("close.lore", Arrays.asList("&7Close this interface"))));
        } else {
            gui.setItem(layout.footerSlot(0), item(
                    Material.ARROW,
                    t("back.title", "&eBack"),
                    tList("back.lore", Arrays.asList("&7Return to the previous screen"))));
        }

        if (hasPrev) {
            gui.setItem(layout.footerSlot(3), item(
                    Material.ARROW,
                    t("prev.title", "&ePrevious Page"),
                    tList("prev.lore", Arrays.asList("&7Show the previous page"))));
        }
        if (hasNext) {
            gui.setItem(layout.footerSlot(5), item(
                    Material.ARROW,
                    t("next.title", "&eNext Page"),
                    tList("next.lore", Arrays.asList("&7Show the next page"))));
        }

        // Rescan
        List<String> refreshLore = new ArrayList<>(tList("refresh.lore",
                Arrays.asList("&7Run a fresh scan of all loaded chunks.")));
        if (report != null) {
            refreshLore.add("");
            refreshLore.add(t("refresh.age", "&7This data is &f{age} &7old.",
                    "age", formatAge(report.getAgeMs())));
        }
        gui.setItem(layout.footerSlot(8), item(
                Material.SUNFLOWER,
                t("refresh.title", "&aRefresh"),
                refreshLore));
    }

    private static void addBorder(Inventory gui, Layout layout) {
        ItemStack border = item(Material.GRAY_STAINED_GLASS_PANE, "&r ", null);
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                int col = i % 9;
                int row = i / 9;
                boolean isEdge = col == 0 || col == 8 || row == 0 || row == (gui.getSize() / 9) - 1;
                if (isEdge) {
                    gui.setItem(i, border);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Event handling
    // ─────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(TITLE_PREFIX)) {
            return;
        }

        // Our screens are read-only; never let items be taken or inserted.
        event.setCancelled(true);

        ViewState state = viewStates.get(player.getUniqueId());
        if (state == null) {
            player.closeInventory();
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR
                || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }

        int size = event.getInventory().getSize();
        Layout layout = new Layout(size);
        int slot = event.getRawSlot();

        // Clicks in the player's own inventory are already cancelled; ignore them.
        if (slot < 0 || slot >= size) {
            return;
        }

        // Footer actions
        if (slot == layout.footerSlot(0)) {
            if (state.screen == Screen.OVERVIEW) {
                player.closeInventory();
            } else if (state.screen == Screen.CHUNK_DETAIL) {
                renderHotspots(player, state);
            } else {
                renderOverview(player, state.report);
            }
            return;
        }
        if (slot == layout.footerSlot(8)) {
            player.closeInventory();
            open(player, true);
            return;
        }
        if (slot == layout.footerSlot(3) && state.screen == Screen.HOTSPOTS) {
            state.page = Math.max(0, state.page - 1);
            renderHotspots(player, state);
            return;
        }
        if (slot == layout.footerSlot(5) && state.screen == Screen.HOTSPOTS) {
            state.page++;
            renderHotspots(player, state);
            return;
        }
        if (slot == layout.footerSlot(2) && state.screen == Screen.CHUNK_DETAIL) {
            teleportToChunk(player, state);
            return;
        }

        // Screen-specific content clicks
        switch (state.screen) {
            case OVERVIEW:
                if (slot == SLOT_HOTSPOTS) {
                    state.page = 0;
                    renderHotspots(player, state);
                } else if (slot == SLOT_ACTIONS) {
                    renderActions(player, state);
                } else if (slot == SLOT_TRENDS) {
                    renderTrends(player, state);
                }
                break;

            case HOTSPOTS:
                Integer index = state.slotToChunkIndex.get(slot);
                if (index != null) {
                    state.selectedChunkIndex = index;
                    renderChunkDetail(player, state);
                }
                break;

            default:
                break;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(TITLE_PREFIX)) {
            return;
        }
        // View state is deliberately retained here. Navigating between screens
        // closes one inventory and opens another, so discarding state on close
        // would break every navigation click. It is cleared on quit instead.
    }

    /**
     * Releases per-player state when a player disconnects.
     *
     * Also evicts the Bedrock platform cache entry, which previously had no
     * eviction path at all and grew for the lifetime of the server.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        viewStates.remove(event.getPlayer().getUniqueId());
        BedrockPlayerUtils.removePlayerFromCache(event.getPlayer());
    }

    /**
     * Teleports the viewer to the selected chunk.
     *
     * Uses the highest block at the chunk centre so the admin does not arrive
     * inside terrain. Dispatched as a region task for Folia correctness.
     */
    private static void teleportToChunk(Player player, ViewState state) {
        if (state.report == null || state.selectedChunkIndex < 0
                || state.selectedChunkIndex >= state.report.getRankedChunks().size()) {
            return;
        }

        LagDiagnosticsEngine.ChunkDiagnostic chunk =
                state.report.getRankedChunks().get(state.selectedChunkIndex);

        World world = Bukkit.getWorld(chunk.getWorldName());
        if (world == null) {
            player.sendMessage(MessageManager.getPrefixedMessage("diagnostics.world-unavailable"));
            return;
        }

        player.closeInventory();

        int x = chunk.getCenterBlockX();
        int z = chunk.getCenterBlockZ();

        me.koyere.lagxpert.utils.SchedulerWrapper.runTaskForRegion(
                world, chunk.getChunkX(), chunk.getChunkZ(), () -> {
                    try {
                        int y = world.getHighestBlockYAt(x, z) + 1;
                        Location target = new Location(world, x + 0.5, y, z + 0.5);
                        player.teleport(target);

                        java.util.Map<String, Object> placeholders = new java.util.HashMap<>();
                        placeholders.put("world", chunk.getWorldName());
                        placeholders.put("x", x);
                        placeholders.put("y", y);
                        placeholders.put("z", z);
                        player.sendMessage(MessageManager.getPrefixedFormattedMessage(
                                "diagnostics.teleported", placeholders));
                    } catch (Exception e) {
                        player.sendMessage(MessageManager.getPrefixedMessage("general.error-occurred"));
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────

    private static ViewState stateFor(Player player) {
        return viewStates.computeIfAbsent(player.getUniqueId(), id -> new ViewState());
    }

    /**
     * Resolves the inventory size to use for this player.
     *
     * Bedrock clients get the operator-configured safe size (36 by default);
     * Java clients get a full six rows.
     */
    private static int inventorySize(Player player) {
        // Four rows for everyone: one border row, two content rows and a footer.
        //
        // Six rows was chosen initially because it is the maximum, but the screens
        // only ever hold nine tiles, which left two and a half rows of filler glass
        // and made the interface look unfinished. Four rows is also exactly the
        // Bedrock-safe size, so Java and Bedrock now render an identical layout
        // instead of two differently-proportioned ones.
        int size = 36;

        // Honor a stricter operator-configured Bedrock size if one is set.
        if (BedrockPlayerUtils.isBedrockPlayer(player)) {
            size = Math.min(size, BedrockPlayerUtils.getSafeInventorySize(player));
        }
        return Math.max(27, (size / 9) * 9);
    }

    // ─── Overview slot map ──────────────────────────────────────────────
    // Explicit constants rather than sequential positions, so the arrangement is
    // deliberate and the click handler cannot drift out of step with the layout.

    /** Row 1: read-only status tiles. */
    private static final int SLOT_STATE = 10;
    private static final int SLOT_TPS = 11;
    private static final int SLOT_MEMORY = 12;
    private static final int SLOT_ENTITIES = 13;
    private static final int SLOT_LIMITS = 14;
    private static final int SLOT_DIAGNOSIS = 15;

    /** Row 2: navigation tiles, spaced so they read as buttons. */
    private static final int SLOT_HOTSPOTS = 20;
    private static final int SLOT_ACTIONS = 22;
    private static final int SLOT_TRENDS = 24;

    /**
     * Opens the inventory, falling back to a chat report if that fails.
     *
     * Geyser occasionally refuses an inventory for reasons outside our control.
     * Without this fallback a Bedrock admin would be left with no diagnosis at all.
     */
    private static void openSafely(Player player, Inventory gui,
                                  LagDiagnosticsEngine.DiagnosticsReport report) {
        try {
            if (!me.koyere.lagxpert.utils.BedrockUI.adaptAndOpen(player, gui)) {
                throw new IllegalStateException("inventory could not be opened");
            }
        } catch (Exception e) {
            LagXpert.getInstance().getLogger().warning(
                    "[DiagnosticsGUI] Could not open inventory for " + player.getName() +
                            ", falling back to chat: " + e.getMessage());
            player.sendMessage(MessageManager.getPrefixedMessage("diagnostics.gui-unavailable"));
            if (report != null) {
                sendChatReport(player, report, 5);
            }
        }
    }

    /**
     * Resolves a translatable interface string.
     *
     * Every label in this interface goes through here so it can be overridden in
     * messages.yml under {@code diagnostics.gui.*}. The English text stays in code
     * as the fallback, which means the interface keeps working on installations
     * whose messages.yml predates these keys.
     */
    private static String t(String key, String fallback, Object... placeholders) {
        return MessageManager.getOrDefault("diagnostics.gui." + key, fallback, pairs(placeholders));
    }

    /**
     * Resolves a translatable multi-line tooltip.
     */
    private static List<String> tList(String key, List<String> fallback, Object... placeholders) {
        return MessageManager.getListOrDefault("diagnostics.gui." + key, fallback, pairs(placeholders));
    }

    /** Builds a placeholder map from alternating key/value arguments. */
    private static Map<String, Object> pairs(Object... keyValuePairs) {
        if (keyValuePairs == null || keyValuePairs.length == 0) {
            return java.util.Collections.emptyMap();
        }
        Map<String, Object> map = new java.util.HashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            map.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return map;
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(MessageManager.color(name));
            }
            if (lore != null && !lore.isEmpty()) {
                List<String> coloured = new ArrayList<>();
                for (String line : lore) {
                    if (coloured.size() >= MAX_LORE_LINES) {
                        break;
                    }
                    coloured.add(MessageManager.color(line));
                }
                meta.setLore(coloured);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Builds the diagnosis tooltip.
     *
     * Shows the single most important observation in full rather than cramming
     * several in and clipping the last one mid-sentence, which is what happened
     * before. Remaining observations are advertised by count, with a pointer to
     * the command that prints all of them.
     */
    private static List<String> buildObservationLore(LagDiagnosticsEngine.DiagnosticsReport report) {
        List<String> lore = new ArrayList<>();
        List<String> observations = report.getObservations();

        if (observations.isEmpty()) {
            lore.add("&7Nothing noteworthy detected.");
            return lore;
        }

        // Reserve the last two lines for the "more" hint so the primary
        // observation is never cut off part-way through a sentence.
        int budget = MAX_LORE_LINES - 2;
        List<String> wrapped = wrap(observations.get(0), 38);

        for (String line : wrapped) {
            if (lore.size() >= budget) {
                lore.add("&8(truncated)");
                break;
            }
            lore.add("&f" + line);
        }

        lore.add("");
        if (observations.size() > 1) {
            lore.add("&8+" + (observations.size() - 1) + " more - /lagxpert diagnose");
        } else {
            lore.add("&8Full report: /lagxpert diagnose");
        }
        return lore;
    }

    /**
     * Builds the entity tooltip, heaviest world first.
     *
     * Previously this iterated the map in world-registration order, so on a server
     * with many worlds the visible rows were all empty ones and the world actually
     * holding the entities was hidden behind the truncation marker.
     */
    private static List<String> buildEntityLore(LagDiagnosticsEngine.DiagnosticsReport report) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Entity count per world, busiest first.");
        lore.add("");

        List<Map.Entry<String, Integer>> worlds =
                new ArrayList<>(report.getEntitiesByWorld().entrySet());
        worlds.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        int shown = 0;
        int hidden = 0;
        for (Map.Entry<String, Integer> entry : worlds) {
            // Worlds with nothing in them are not worth a row.
            if (entry.getValue() <= 0) {
                hidden++;
                continue;
            }
            if (shown >= 5) {
                hidden++;
                continue;
            }
            int chunks = report.getChunksByWorld().getOrDefault(entry.getKey(), 0);
            lore.add("&7" + entry.getKey() + ": &f" + entry.getValue() +
                    " &8(" + chunks + " chunks)");
            shown++;
        }

        if (shown == 0) {
            lore.add("&7No entities in any loaded chunk.");
        } else if (hidden > 0) {
            lore.add("&8+" + hidden + " world(s) with none or fewer");
        }
        return lore;
    }

    private static List<String> buildChunkSummaryLore(LagDiagnosticsEngine.ChunkDiagnostic chunk) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Score: &f" + Math.round(chunk.getScore()) +
                " &8(" + chunk.getSeverity().name() + ")");
        lore.add("");

        List<LagDiagnosticsEngine.MetricFinding> violations = chunk.getViolations();
        if (violations.isEmpty()) {
            LagDiagnosticsEngine.MetricFinding primary = chunk.getPrimaryCause();
            if (primary != null) {
                lore.add("&7Highest: &f" + primary.getCount() + " " + primary.getMetric() +
                        " &8(" + primary.getPercentOfLimit() + "% of limit)");
            }
        } else {
            lore.add("&cOver limit:");
            int shown = 0;
            for (LagDiagnosticsEngine.MetricFinding violation : violations) {
                if (shown >= 3) {
                    lore.add("&8...and " + (violations.size() - shown) + " more");
                    break;
                }
                lore.add("&7 " + violation.getMetric() + ": &f" + violation.getCount() +
                        "&8/" + violation.getLimit());
                shown++;
            }
        }

        if (chunk.getInterventionCount() > 0) {
            lore.add("&8" + chunk.getInterventionCount() + " action(s) here in 1h");
        }
        lore.add("");
        lore.add("&eClick for details");
        return lore;
    }

    private static int countRecentInterventions(LagDiagnosticsEngine.DiagnosticsReport report) {
        int total = 0;
        for (int count : report.getInterventionsByType().values()) {
            total += count;
        }
        return total;
    }

    /** Wraps text at word boundaries so tooltips stay readable. */
    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() > 0 && current.length() + word.length() + 1 > width) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static String percent(double multiplier) {
        return String.format("%.0f%%", multiplier * 100.0);
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value;
    }

    private static String capitalise(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String spaced = value.replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String prettyAction(String actionType) {
        return capitalise(actionType.toLowerCase());
    }

    private static String formatAge(long ms) {
        if (ms < 1000) return "just now";
        if (ms < 60_000) return (ms / 1000) + "s";
        if (ms < 3_600_000) return (ms / 60_000) + "m";
        if (ms < 86_400_000) return (ms / 3_600_000) + "h";
        return (ms / 86_400_000) + "d";
    }

    private static String tpsColor(double tps) {
        if (tps >= 19.0) return "&a";
        if (tps >= 16.0) return "&e";
        if (tps >= 12.0) return "&6";
        return "&c";
    }

    private static String memColor(double percent) {
        if (percent < 70) return "&a";
        if (percent < 85) return "&e";
        return "&c";
    }

    private static String stateColor(String state) {
        switch (state) {
            case "NORMAL": return "&a";
            case "WARNING": return "&e";
            case "CRITICAL": return "&c";
            case "EMERGENCY": return "&4";
            default: return "&7";
        }
    }

    private static Material stateMaterial(String state) {
        switch (state) {
            case "NORMAL": return Material.LIME_TERRACOTTA;
            case "WARNING": return Material.YELLOW_TERRACOTTA;
            case "CRITICAL": return Material.ORANGE_TERRACOTTA;
            case "EMERGENCY": return Material.RED_TERRACOTTA;
            default: return Material.LIGHT_GRAY_TERRACOTTA;
        }
    }

    private static String severityColor(LagDiagnosticsEngine.Severity severity) {
        switch (severity) {
            case CRITICAL: return "&4";
            case HIGH: return "&c";
            case MODERATE: return "&e";
            default: return "&7";
        }
    }

    private static Material severityMaterial(LagDiagnosticsEngine.Severity severity) {
        switch (severity) {
            case CRITICAL: return Material.RED_TERRACOTTA;
            case HIGH: return Material.ORANGE_TERRACOTTA;
            case MODERATE: return Material.YELLOW_TERRACOTTA;
            default: return Material.LIME_TERRACOTTA;
        }
    }

    /**
     * Picks an icon that visually matches the metric.
     *
     * Deliberately avoids spawn eggs, which Geyser renders inconsistently for
     * Bedrock clients.
     */
    private static Material metricMaterial(String metric) {
        switch (metric) {
            case "mobs": return Material.ROTTEN_FLESH;
            case "entities": return Material.LEATHER;
            case "hoppers": return Material.HOPPER;
            case "chests": return Material.CHEST;
            case "furnaces": return Material.FURNACE;
            case "shulker_boxes": return Material.SHULKER_SHELL;
            case "barrels": return Material.BARREL;
            case "droppers": return Material.DROPPER;
            case "dispensers": return Material.DISPENSER;
            case "tnt": return Material.TNT;
            case "pistons": return Material.PISTON;
            case "observers": return Material.OBSERVER;
            default: return Material.PAPER;
        }
    }

    private static Material actionMaterial(String actionType) {
        if (actionType.startsWith("MOB") || actionType.startsWith("SPAWN")) {
            return Material.ROTTEN_FLESH;
        }
        if (actionType.startsWith("ITEM")) {
            return Material.HOPPER;
        }
        if (actionType.startsWith("ENTITY")) {
            return Material.LEATHER;
        }
        if (actionType.startsWith("REDSTONE")) {
            return Material.REDSTONE;
        }
        if (actionType.startsWith("CHUNK")) {
            return Material.GRASS_BLOCK;
        }
        if (actionType.startsWith("VEHICLE")) {
            return Material.MINECART;
        }
        if (actionType.startsWith("EXPLOSION")) {
            return Material.TNT;
        }
        if (actionType.startsWith("EMERGENCY") || actionType.startsWith("STATE")) {
            return Material.RED_TERRACOTTA;
        }
        if (actionType.startsWith("AI_")) {
            return Material.BONE;
        }
        if (actionType.startsWith("CONFIG") || actionType.startsWith("MANUAL")) {
            return Material.WRITABLE_BOOK;
        }
        return Material.PAPER;
    }

    private static Material trendMaterial(String direction) {
        if (direction == null) {
            return Material.PAPER;
        }
        String upper = direction.toUpperCase();
        if (upper.contains("GROW") || upper.contains("RIS") || upper.contains("INCREAS")) {
            return Material.RED_TERRACOTTA;
        }
        if (upper.contains("SHRINK") || upper.contains("FALL") || upper.contains("DECREAS")) {
            return Material.LIME_TERRACOTTA;
        }
        return Material.YELLOW_TERRACOTTA;
    }

    /** Clears all view state, used on plugin shutdown. */
    public static void clearAll() {
        viewStates.clear();
    }
}
