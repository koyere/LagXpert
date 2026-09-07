package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.cache.ChunkDataCache;
import me.koyere.lagxpert.monitoring.TPSMonitor;
import me.koyere.lagxpert.utils.ChunkUtils;
import me.koyere.lagxpert.utils.ConfigManager;
import me.koyere.lagxpert.utils.SchedulerWrapper;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Answers the question every admin actually asks: <em>why is my server lagging,
 * and where?</em>
 *
 * The plugin already measured plenty of things before this class existed, but all
 * of it was either server-wide (TPS, memory) or single-chunk on demand
 * (/chunkstatus). Nothing ranked chunks against each other, so there was no way
 * to find the one hopper farm dragging everything down.
 *
 * This engine scans loaded chunks, scores each one against the limits that
 * actually apply to it, and produces a ranked report. Design constraints:
 *
 * <ul>
 *   <li><b>Scoring is relative to limits, not to raw counts.</b> A chunk with 40
 *       mobs where the limit is 200 is fine; a chunk with 12 hoppers where the
 *       limit is 8 is a problem. Ranking by raw count would surface the wrong
 *       chunks, so every metric contributes its percentage of the applicable
 *       limit.</li>
 *   <li><b>Limits come from the adaptive engine.</b> The report therefore reflects
 *       what is being enforced right now, matching what players experience.</li>
 *   <li><b>Intervention history is a first-class signal.</b> A chunk the plugin
 *       has had to clean up twenty times is more interesting than one that is
 *       merely full, so {@link ActionLogger} history is folded into the score.</li>
 *   <li><b>Counting happens off the main thread.</b> Scanning thousands of chunks
 *       synchronously would itself cause the lag being diagnosed. Chunk snapshots
 *       are gathered on the owning thread, then scored asynchronously.</li>
 * </ul>
 *
 * Results are cached briefly so that a command and a GUI opened seconds apart
 * share one scan instead of triggering two.
 */
public class LagDiagnosticsEngine {

    private static LagDiagnosticsEngine instance;

    /** How long a completed report stays servable before a rescan is required. */
    private static final long REPORT_TTL_MS = 30_000L;

    /** Guards against two overlapping scans. */
    private final AtomicBoolean scanInProgress = new AtomicBoolean(false);

    private volatile DiagnosticsReport cachedReport = null;

    private LagDiagnosticsEngine() {
    }

    public static LagDiagnosticsEngine getInstance() {
        if (instance == null) {
            instance = new LagDiagnosticsEngine();
        }
        return instance;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Data model
    // ─────────────────────────────────────────────────────────────────────

    /**
     * One measured contributor within a chunk, e.g. "hoppers: 12 of 8".
     */
    public static class MetricFinding {
        private final String metric;
        private final int count;
        private final int limit;

        public MetricFinding(String metric, int count, int limit) {
            this.metric = metric;
            this.count = count;
            this.limit = limit;
        }

        public String getMetric() { return metric; }
        public int getCount() { return count; }
        public int getLimit() { return limit; }

        /** Usage as a fraction of the applicable limit; may exceed 1.0. */
        public double getRatio() {
            return limit <= 0 ? 0.0 : (double) count / (double) limit;
        }

        public int getPercentOfLimit() {
            return (int) Math.round(getRatio() * 100.0);
        }

        public boolean isOverLimit() {
            return limit > 0 && count > limit;
        }
    }

    /**
     * A single chunk's diagnostic result.
     */
    public static class ChunkDiagnostic {
        private final String worldName;
        private final int chunkX;
        private final int chunkZ;
        private final int totalEntities;
        private final int livingEntities;
        private final int tileEntities;
        private final List<MetricFinding> findings;
        private final int interventionCount;
        private final double score;
        private final boolean playerNearby;

        ChunkDiagnostic(String worldName, int chunkX, int chunkZ,
                        int totalEntities, int livingEntities, int tileEntities,
                        List<MetricFinding> findings, int interventionCount,
                        double score, boolean playerNearby) {
            this.worldName = worldName;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.totalEntities = totalEntities;
            this.livingEntities = livingEntities;
            this.tileEntities = tileEntities;
            this.findings = findings;
            this.interventionCount = interventionCount;
            this.score = score;
            this.playerNearby = playerNearby;
        }

        public String getWorldName() { return worldName; }
        public int getChunkX() { return chunkX; }
        public int getChunkZ() { return chunkZ; }
        public int getTotalEntities() { return totalEntities; }
        public int getLivingEntities() { return livingEntities; }
        public int getTileEntities() { return tileEntities; }
        public int getInterventionCount() { return interventionCount; }
        public double getScore() { return score; }
        public boolean isPlayerNearby() { return playerNearby; }

        public List<MetricFinding> getFindings() {
            return Collections.unmodifiableList(findings);
        }

        public String getChunkKey() {
            return worldName + "_" + chunkX + "_" + chunkZ;
        }

        /** Block coordinate at the centre of this chunk, for teleporting. */
        public int getCenterBlockX() { return (chunkX << 4) + 8; }
        public int getCenterBlockZ() { return (chunkZ << 4) + 8; }

        /** Findings that exceed their limit, worst first. */
        public List<MetricFinding> getViolations() {
            List<MetricFinding> violations = new ArrayList<>();
            for (MetricFinding finding : findings) {
                if (finding.isOverLimit()) {
                    violations.add(finding);
                }
            }
            violations.sort(Comparator.comparingDouble(MetricFinding::getRatio).reversed());
            return violations;
        }

        /**
         * The single biggest contributor to this chunk's score, used as the
         * one-line explanation of why the chunk was flagged.
         */
        public MetricFinding getPrimaryCause() {
            MetricFinding worst = null;
            for (MetricFinding finding : findings) {
                if (worst == null || finding.getRatio() > worst.getRatio()) {
                    worst = finding;
                }
            }
            return worst;
        }

        /** Coarse banding used for colour coding in output. */
        public Severity getSeverity() {
            if (score >= 200) return Severity.CRITICAL;
            if (score >= 120) return Severity.HIGH;
            if (score >= 80) return Severity.MODERATE;
            return Severity.LOW;
        }
    }

    public enum Severity {
        LOW,
        MODERATE,
        HIGH,
        CRITICAL
    }

    /**
     * A complete diagnostics report: server-wide context plus ranked chunks.
     */
    public static class DiagnosticsReport {
        private final long generatedAt;
        private final long scanDurationMs;
        private final int chunksScanned;
        private final int worldsScanned;
        private final double tps;
        private final double memoryPercent;
        private final long usedMemoryMb;
        private final long maxMemoryMb;
        private final String serverState;
        private final List<ChunkDiagnostic> rankedChunks;
        private final Map<String, Integer> entitiesByWorld;
        private final Map<String, Integer> chunksByWorld;
        private final Map<String, Integer> interventionsByType;
        private final List<TPSMonitor.LagSpike> recentSpikes;
        private final int totalEntities;
        private final List<String> observations;

        DiagnosticsReport(long generatedAt, long scanDurationMs, int chunksScanned, int worldsScanned,
                          double tps, double memoryPercent, long usedMemoryMb, long maxMemoryMb,
                          String serverState, List<ChunkDiagnostic> rankedChunks,
                          Map<String, Integer> entitiesByWorld, Map<String, Integer> chunksByWorld,
                          Map<String, Integer> interventionsByType,
                          List<TPSMonitor.LagSpike> recentSpikes, int totalEntities,
                          List<String> observations) {
            this.generatedAt = generatedAt;
            this.scanDurationMs = scanDurationMs;
            this.chunksScanned = chunksScanned;
            this.worldsScanned = worldsScanned;
            this.tps = tps;
            this.memoryPercent = memoryPercent;
            this.usedMemoryMb = usedMemoryMb;
            this.maxMemoryMb = maxMemoryMb;
            this.serverState = serverState;
            this.rankedChunks = rankedChunks;
            this.entitiesByWorld = entitiesByWorld;
            this.chunksByWorld = chunksByWorld;
            this.interventionsByType = interventionsByType;
            this.recentSpikes = recentSpikes;
            this.totalEntities = totalEntities;
            this.observations = observations;
        }

        public long getGeneratedAt() { return generatedAt; }
        public long getScanDurationMs() { return scanDurationMs; }
        public int getChunksScanned() { return chunksScanned; }
        public int getWorldsScanned() { return worldsScanned; }
        public double getTps() { return tps; }
        public double getMemoryPercent() { return memoryPercent; }
        public long getUsedMemoryMb() { return usedMemoryMb; }
        public long getMaxMemoryMb() { return maxMemoryMb; }
        public String getServerState() { return serverState; }
        public int getTotalEntities() { return totalEntities; }

        public List<ChunkDiagnostic> getRankedChunks() {
            return Collections.unmodifiableList(rankedChunks);
        }

        public Map<String, Integer> getEntitiesByWorld() { return entitiesByWorld; }
        public Map<String, Integer> getChunksByWorld() { return chunksByWorld; }
        public Map<String, Integer> getInterventionsByType() { return interventionsByType; }
        public List<TPSMonitor.LagSpike> getRecentSpikes() { return recentSpikes; }

        /**
         * Plain-language conclusions derived from the numbers, so the operator is
         * not left to interpret raw statistics themselves.
         */
        public List<String> getObservations() {
            return Collections.unmodifiableList(observations);
        }

        /** Top N chunks by score. */
        public List<ChunkDiagnostic> getTopChunks(int limit) {
            return rankedChunks.subList(0, Math.min(limit, rankedChunks.size()));
        }

        public boolean isStale() {
            return System.currentTimeMillis() - generatedAt > REPORT_TTL_MS;
        }

        public long getAgeMs() {
            return System.currentTimeMillis() - generatedAt;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Scanning
    // ─────────────────────────────────────────────────────────────────────

    /** Immutable per-chunk snapshot captured on the owning thread. */
    private static class ChunkSnapshot {
        final String worldName;
        final int chunkX;
        final int chunkZ;
        final int totalEntities;
        final int livingEntities;
        final int tileEntities;
        final Map<String, Integer> counts = new LinkedHashMap<>();
        final Map<String, Integer> limits = new LinkedHashMap<>();
        final boolean playerNearby;

        ChunkSnapshot(String worldName, int chunkX, int chunkZ, int totalEntities,
                      int livingEntities, int tileEntities, boolean playerNearby) {
            this.worldName = worldName;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.totalEntities = totalEntities;
            this.livingEntities = livingEntities;
            this.tileEntities = tileEntities;
            this.playerNearby = playerNearby;
        }
    }

    public boolean isScanInProgress() {
        return scanInProgress.get();
    }

    /**
     * Returns the cached report if one exists and is still fresh, else null.
     */
    public DiagnosticsReport getCachedReport() {
        DiagnosticsReport report = cachedReport;
        return (report != null && !report.isStale()) ? report : null;
    }

    /**
     * Returns the last report regardless of age, or null if none was ever built.
     */
    public DiagnosticsReport getLastReport() {
        return cachedReport;
    }

    /**
     * Produces a diagnostics report, reusing a fresh cached one when available.
     *
     * The callback is always invoked on the main thread so it is safe to send
     * messages or open inventories from it.
     *
     * @param forceRescan ignore the cache and scan again
     * @param callback    receives the report, or null if a scan was already running
     */
    public void requestReport(boolean forceRescan, Consumer<DiagnosticsReport> callback) {
        if (!forceRescan) {
            DiagnosticsReport cached = getCachedReport();
            if (cached != null) {
                SchedulerWrapper.runTask(() -> callback.accept(cached));
                return;
            }
        }

        // Reject rather than queue: a second concurrent full scan would add load
        // to a server that is, by definition, already suspected of struggling.
        if (!scanInProgress.compareAndSet(false, true)) {
            SchedulerWrapper.runTask(() -> callback.accept(null));
            return;
        }

        final long startedAt = System.currentTimeMillis();

        // Phase 1: gather snapshots. Reading entities and tile entities must happen
        // on the thread that owns the chunk.
        SchedulerWrapper.runTask(() -> {
            List<ChunkSnapshot> snapshots = new ArrayList<>();
            Map<String, Integer> entitiesByWorld = new LinkedHashMap<>();
            Map<String, Integer> chunksByWorld = new LinkedHashMap<>();
            int totalEntities = 0;
            int worldsScanned = 0;

            try {
                int maxChunks = ConfigManager.isDebugEnabled() ? 20000 : 8000;

                for (World world : Bukkit.getWorlds()) {
                    worldsScanned++;
                    Chunk[] loaded;
                    try {
                        loaded = world.getLoadedChunks();
                    } catch (Exception e) {
                        continue;
                    }

                    chunksByWorld.put(world.getName(), loaded.length);
                    int worldEntities = 0;
                    List<Player> playersInWorld = world.getPlayers();

                    for (Chunk chunk : loaded) {
                        if (snapshots.size() >= maxChunks) {
                            break;
                        }
                        try {
                            ChunkSnapshot snapshot = captureChunk(chunk, playersInWorld);
                            if (snapshot != null) {
                                snapshots.add(snapshot);
                                worldEntities += snapshot.totalEntities;
                            }
                        } catch (Exception e) {
                            // A single unreadable chunk must not abort the scan.
                        }
                    }

                    entitiesByWorld.put(world.getName(), worldEntities);
                    totalEntities += worldEntities;
                }
            } catch (Exception e) {
                LagXpert.getInstance().getLogger().warning(
                        "[LagDiagnosticsEngine] Snapshot phase failed: " + e.getMessage());
            }

            final List<ChunkSnapshot> finalSnapshots = snapshots;
            final int finalTotalEntities = totalEntities;
            final int finalWorldsScanned = worldsScanned;

            // Phase 2: score and rank asynchronously. This is pure arithmetic over
            // the snapshots and touches no Bukkit state.
            SchedulerWrapper.runTaskAsynchronously(() -> {
                DiagnosticsReport report = null;
                try {
                    report = buildReport(finalSnapshots, entitiesByWorld, chunksByWorld,
                            finalTotalEntities, finalWorldsScanned, startedAt);
                    cachedReport = report;
                } catch (Exception e) {
                    LagXpert.getInstance().getLogger().warning(
                            "[LagDiagnosticsEngine] Scoring phase failed: " + e.getMessage());
                } finally {
                    scanInProgress.set(false);
                }

                final DiagnosticsReport finalReport = report;
                SchedulerWrapper.runTask(() -> callback.accept(finalReport));
            });
        });
    }

    /**
     * Captures the counts for one chunk, reusing the shared analysis cache so a
     * diagnostics scan does not duplicate work the scan task already did.
     */
    private ChunkSnapshot captureChunk(Chunk chunk, List<Player> playersInWorld) {
        if (!chunk.isLoaded()) {
            return null;
        }

        World world = chunk.getWorld();
        int totalEntities = chunk.getEntities().length;

        ChunkDataCache.ChunkData data = ChunkUtils.performCompleteChunkAnalysis(chunk);
        int livingEntities = data != null ? data.getLivingEntities() : 0;

        int tileEntities = 0;
        try {
            tileEntities = chunk.getTileEntities().length;
        } catch (Exception ignored) {
            // Some server forks throw on partially-loaded chunks; zero is acceptable.
        }

        boolean playerNearby = false;
        int centerX = (chunk.getX() << 4) + 8;
        int centerZ = (chunk.getZ() << 4) + 8;
        for (Player player : playersInWorld) {
            double dx = player.getLocation().getX() - centerX;
            double dz = player.getLocation().getZ() - centerZ;
            if ((dx * dx + dz * dz) <= (128 * 128)) {
                playerNearby = true;
                break;
            }
        }

        ChunkSnapshot snapshot = new ChunkSnapshot(world.getName(), chunk.getX(), chunk.getZ(),
                totalEntities, livingEntities, tileEntities, playerNearby);

        AdaptiveThresholdEngine adaptive = AdaptiveThresholdEngine.getInstance();

        // Mobs
        addMetric(snapshot, "mobs", livingEntities,
                adaptive.getEffectiveLimit(AdaptiveThresholdEngine.LimitCategory.MOBS,
                        ConfigManager.getMaxMobsPerChunk(world)));

        // Total entity ceiling
        addMetric(snapshot, "entities", totalEntities,
                adaptive.getEffectiveLimit(AdaptiveThresholdEngine.LimitCategory.ENTITIES,
                        ConfigManager.getMaxEntitiesPerChunk(world)));

        if (data != null) {
            addMetric(snapshot, "hoppers", data.getBlockCount(Material.HOPPER),
                    adaptive.getEffectiveLimit(AdaptiveThresholdEngine.LimitCategory.STORAGE,
                            ConfigManager.getMaxHoppersPerChunk(world)));
            addMetric(snapshot, "chests", data.getCustomCount("all_chests"),
                    adaptive.getEffectiveLimit(AdaptiveThresholdEngine.LimitCategory.STORAGE,
                            ConfigManager.getMaxChestsPerChunk(world)));
            addMetric(snapshot, "furnaces", data.getCustomCount("all_furnaces"),
                    adaptive.getEffectiveLimit(AdaptiveThresholdEngine.LimitCategory.STORAGE,
                            ConfigManager.getMaxFurnacesPerChunk(world)));
            addMetric(snapshot, "shulker_boxes", data.getCustomCount("all_shulker_boxes"),
                    adaptive.getEffectiveLimit(AdaptiveThresholdEngine.LimitCategory.STORAGE,
                            ConfigManager.getMaxShulkerBoxesPerChunk(world)));
            addMetric(snapshot, "barrels", data.getBlockCount(Material.BARREL),
                    adaptive.getEffectiveLimit(AdaptiveThresholdEngine.LimitCategory.STORAGE,
                            ConfigManager.getMaxBarrelsPerChunk(world)));
            addMetric(snapshot, "droppers", data.getBlockCount(Material.DROPPER),
                    adaptive.getEffectiveLimit(AdaptiveThresholdEngine.LimitCategory.STORAGE,
                            ConfigManager.getMaxDroppersPerChunk(world)));
            addMetric(snapshot, "dispensers", data.getBlockCount(Material.DISPENSER),
                    adaptive.getEffectiveLimit(AdaptiveThresholdEngine.LimitCategory.STORAGE,
                            ConfigManager.getMaxDispensersPerChunk(world)));
        }

        return snapshot;
    }

    private void addMetric(ChunkSnapshot snapshot, String name, int count, int limit) {
        if (count <= 0 || limit <= 0) {
            return; // Nothing to report, or the limit is disabled.
        }
        snapshot.counts.put(name, count);
        snapshot.limits.put(name, limit);
    }

    /**
     * Scores the snapshots and assembles the final report.
     *
     * Runs off the main thread; must not call into Bukkit.
     */
    private DiagnosticsReport buildReport(List<ChunkSnapshot> snapshots,
                                          Map<String, Integer> entitiesByWorld,
                                          Map<String, Integer> chunksByWorld,
                                          int totalEntities,
                                          int worldsScanned,
                                          long startedAt) {

        // Intervention history, grouped by chunk. A chunk the plugin keeps having to
        // clean up is a stronger signal than a chunk that is merely full right now.
        Map<String, Integer> interventionsByChunk = new ConcurrentHashMap<>();
        Map<String, Integer> interventionsByType = new LinkedHashMap<>();

        try {
            long since = System.currentTimeMillis() - (60L * 60L * 1000L); // last hour
            for (ActionLogger.ActionRecord record : ActionLogger.getInstance().getSince(since)) {
                String type = record.getType().name();
                interventionsByType.merge(type, 1, Integer::sum);

                String chunkKey = record.getChunkKey();
                if (chunkKey != null && !chunkKey.isEmpty()) {
                    interventionsByChunk.merge(chunkKey, 1, Integer::sum);
                }
            }
        } catch (Exception ignored) {
            // History is a bonus signal; its absence must not fail the report.
        }

        List<ChunkDiagnostic> diagnostics = new ArrayList<>();

        for (ChunkSnapshot snapshot : snapshots) {
            List<MetricFinding> findings = new ArrayList<>();
            double score = 0.0;

            for (Map.Entry<String, Integer> entry : snapshot.counts.entrySet()) {
                String metric = entry.getKey();
                int count = entry.getValue();
                int limit = snapshot.limits.getOrDefault(metric, 0);

                MetricFinding finding = new MetricFinding(metric, count, limit);
                findings.add(finding);

                double ratio = finding.getRatio();

                // Percentage of limit is the base contribution. Exceeding a limit is
                // weighted far more heavily than approaching it, because the former
                // is an active problem and the latter is merely worth watching.
                double contribution = ratio * 100.0;
                if (ratio > 1.0) {
                    contribution += (ratio - 1.0) * 150.0;
                }
                score += contribution;
            }

            String chunkKey = snapshot.worldName + "_" + snapshot.chunkX + "_" + snapshot.chunkZ;
            int interventions = interventionsByChunk.getOrDefault(chunkKey, 0);

            // Each recorded intervention in the last hour adds to the score, capped so
            // history can inform the ranking without completely dominating it.
            score += Math.min(interventions * 15.0, 150.0);

            // Chunks with no players nearby are cheaper to fix (unload or clean) and
            // less likely to be a build someone cares about, so nudge them up.
            if (!snapshot.playerNearby) {
                score *= 1.15;
            }

            // Only keep chunks that are actually interesting. Without this the report
            // would be thousands of empty chunks scoring near zero.
            if (score < 25.0 && interventions == 0) {
                continue;
            }

            findings.sort(Comparator.comparingDouble(MetricFinding::getRatio).reversed());

            diagnostics.add(new ChunkDiagnostic(
                    snapshot.worldName, snapshot.chunkX, snapshot.chunkZ,
                    snapshot.totalEntities, snapshot.livingEntities, snapshot.tileEntities,
                    findings, interventions, score, snapshot.playerNearby));
        }

        diagnostics.sort(Comparator.comparingDouble(ChunkDiagnostic::getScore).reversed());

        // Server-wide context
        double tps = TPSMonitor.getCurrentTPS();
        long maxMem = Runtime.getRuntime().maxMemory();
        long usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        double memPercent = maxMem > 0 ? ((double) usedMem / maxMem) * 100.0 : 0.0;

        List<TPSMonitor.LagSpike> spikes;
        try {
            List<TPSMonitor.LagSpike> all = TPSMonitor.getRecentLagSpikes();
            spikes = all.size() > 5 ? new ArrayList<>(all.subList(all.size() - 5, all.size()))
                    : new ArrayList<>(all);
        } catch (Exception e) {
            spikes = new ArrayList<>();
        }

        String serverState;
        try {
            serverState = EmergencyController.getInstance().getCurrentState().name();
        } catch (Exception e) {
            serverState = "UNKNOWN";
        }

        List<String> observations = deriveObservations(
                diagnostics, tps, memPercent, totalEntities, entitiesByWorld,
                chunksByWorld, interventionsByType);

        return new DiagnosticsReport(
                System.currentTimeMillis(),
                System.currentTimeMillis() - startedAt,
                snapshots.size(),
                worldsScanned,
                tps, memPercent, usedMem / 1024 / 1024, maxMem / 1024 / 1024,
                serverState, diagnostics,
                entitiesByWorld, chunksByWorld, interventionsByType,
                spikes, totalEntities, observations);
    }

    /**
     * Turns the measurements into plain-language conclusions.
     *
     * This is the difference between a dashboard and a diagnosis. Numbers alone
     * still leave the operator to work out what they mean.
     */
    private List<String> deriveObservations(List<ChunkDiagnostic> diagnostics,
                                            double tps, double memPercent, int totalEntities,
                                            Map<String, Integer> entitiesByWorld,
                                            Map<String, Integer> chunksByWorld,
                                            Map<String, Integer> interventionsByType) {
        List<String> observations = new ArrayList<>();

        // Is there anything wrong at all?
        if (tps >= 19.0 && memPercent < 80.0 && diagnostics.isEmpty()) {
            observations.add("No performance problems detected. TPS and memory are healthy " +
                    "and no chunk is close to its limits.");
            return observations;
        }

        // Concentration analysis: is the load in a few chunks or spread everywhere?
        if (!diagnostics.isEmpty()) {
            double totalScore = 0.0;
            for (ChunkDiagnostic diagnostic : diagnostics) {
                totalScore += diagnostic.getScore();
            }
            double topFiveScore = 0.0;
            for (ChunkDiagnostic diagnostic : diagnostics.subList(0, Math.min(5, diagnostics.size()))) {
                topFiveScore += diagnostic.getScore();
            }

            if (totalScore > 0 && (topFiveScore / totalScore) > 0.5) {
                ChunkDiagnostic worst = diagnostics.get(0);
                MetricFinding cause = worst.getPrimaryCause();
                observations.add("Load is concentrated: the top 5 chunks account for " +
                        Math.round((topFiveScore / totalScore) * 100) + "% of all detected pressure. " +
                        "Fixing a handful of locations will likely resolve most of it, starting with " +
                        worst.getWorldName() + " at " + worst.getCenterBlockX() + ", " + worst.getCenterBlockZ() +
                        (cause != null ? " (" + cause.getCount() + " " + cause.getMetric() + ")" : "") + ".");
            } else if (diagnostics.size() > 20) {
                observations.add("Load is spread across " + diagnostics.size() + " chunks rather than " +
                        "concentrated in a few. This usually points to global settings being too permissive " +
                        "rather than one bad build. Consider a stricter profile via /lagxpert profile.");
            }

            // What kind of thing is the dominant problem?
            Map<String, Integer> causeTally = new LinkedHashMap<>();
            for (ChunkDiagnostic diagnostic : diagnostics.subList(0, Math.min(25, diagnostics.size()))) {
                for (MetricFinding violation : diagnostic.getViolations()) {
                    causeTally.merge(violation.getMetric(), 1, Integer::sum);
                }
            }
            String dominant = null;
            int dominantCount = 0;
            for (Map.Entry<String, Integer> entry : causeTally.entrySet()) {
                if (entry.getValue() > dominantCount) {
                    dominant = entry.getKey();
                    dominantCount = entry.getValue();
                }
            }
            if (dominant != null && dominantCount >= 3) {
                observations.add("The most common violation is '" + dominant + "', over its limit in " +
                        dominantCount + " of the top chunks. Lowering that limit, or dealing with the " +
                        "builds responsible, will have the widest effect.");
            }
        }

        // Memory pressure is a distinct failure mode from tick pressure.
        if (memPercent >= 90.0) {
            observations.add("Memory usage is at " + Math.round(memPercent) + "%. At this level " +
                    "garbage collection pauses alone can cause the lag you are seeing, independently " +
                    "of entity or block counts. Consider raising the heap size.");
        } else if (memPercent >= 80.0) {
            observations.add("Memory usage is at " + Math.round(memPercent) + "%, which is high enough " +
                    "that GC pauses may be contributing.");
        }

        // Entity totals relative to loaded chunks.
        int totalChunks = 0;
        for (int count : chunksByWorld.values()) {
            totalChunks += count;
        }
        if (totalChunks > 0) {
            double perChunk = (double) totalEntities / totalChunks;
            if (perChunk > 8.0) {
                observations.add(String.format(
                        "Average entity density is %.1f per loaded chunk across %d chunks (%d entities total), " +
                                "which is high. Entity cleanup and mob limits are the levers here.",
                        perChunk, totalChunks, totalEntities));
            }
        }

        // Which world is carrying the load?
        String heaviestWorld = null;
        int heaviestCount = 0;
        for (Map.Entry<String, Integer> entry : entitiesByWorld.entrySet()) {
            if (entry.getValue() > heaviestCount) {
                heaviestWorld = entry.getKey();
                heaviestCount = entry.getValue();
            }
        }
        if (heaviestWorld != null && totalEntities > 0 && heaviestCount > totalEntities * 0.6) {
            observations.add("World '" + heaviestWorld + "' holds " +
                    Math.round((heaviestCount * 100.0) / totalEntities) + "% of all entities (" +
                    heaviestCount + "). Per-world limits for that world may be worth tightening.");
        }

        // Is the plugin already working hard to compensate?
        int totalInterventions = 0;
        for (int count : interventionsByType.values()) {
            totalInterventions += count;
        }
        if (totalInterventions > 200) {
            observations.add("LagXpert performed " + totalInterventions + " corrective actions in the last " +
                    "hour. The server is being held together by cleanup rather than by sane limits; " +
                    "the underlying builds or settings need attention.");
        }

        // TPS present but nothing found is itself informative.
        if (tps < 18.0 && diagnostics.isEmpty()) {
            observations.add(String.format(
                    "TPS is %.1f but no chunk exceeded its limits. The cause is likely outside LagXpert's " +
                            "scope: another plugin, world generation, disk I/O, or an undersized host.", tps));
        }

        if (observations.isEmpty()) {
            observations.add("Some chunks are approaching their limits but nothing is critical yet.");
        }

        return observations;
    }

    /**
     * Clears the cached report, forcing the next request to rescan.
     */
    public void invalidateCache() {
        cachedReport = null;
    }
}
