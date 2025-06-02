package me.koyere.lagxpert.tasks;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.cache.ChunkDataCache;
import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Asynchronous chunk analyzer that performs heavy chunk scanning operations
 * off the main thread to prevent server lag. Uses CompletableFuture for
 * non-blocking operations and provides result callbacks.
 */
public class AsyncChunkAnalyzer {

    private static final int THREAD_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE, new ChunkAnalyzerThreadFactory());

    // Statistics tracking
    private static final AtomicInteger completedAnalyses = new AtomicInteger(0);
    private static final AtomicInteger queuedAnalyses = new AtomicInteger(0);
    private static final AtomicInteger failedAnalyses = new AtomicInteger(0);

    /**
     * Custom thread factory for chunk analyzer threads.
     */
    private static class ChunkAnalyzerThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix = "LagXpert-ChunkAnalyzer-";

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true); // Daemon threads won't prevent JVM shutdown
            t.setPriority(Thread.NORM_PRIORITY - 1); // Slightly lower priority
            return t;
        }
    }

    /**
     * Result container for chunk analysis operations.
     */
    public static class ChunkAnalysisResult {
        private final String chunkKey;
        private final int livingEntities;
        private final Map<Material, Integer> blockCounts;
        private final Map<String, Integer> customCounts;
        private final boolean success;
        private final long analysisTimeMs;
        private final Exception error;

        public ChunkAnalysisResult(String chunkKey, int livingEntities, Map<Material, Integer> blockCounts,
                                   Map<String, Integer> customCounts, long analysisTimeMs) {
            this.chunkKey = chunkKey;
            this.livingEntities = livingEntities;
            this.blockCounts = blockCounts;
            this.customCounts = customCounts;
            this.analysisTimeMs = analysisTimeMs;
            this.success = true;
            this.error = null;
        }

        public ChunkAnalysisResult(String chunkKey, Exception error, long analysisTimeMs) {
            this.chunkKey = chunkKey;
            this.livingEntities = 0;
            this.blockCounts = new EnumMap<>(Material.class);
            this.customCounts = new ConcurrentHashMap<>();
            this.analysisTimeMs = analysisTimeMs;
            this.success = false;
            this.error = error;
        }

        // Getters
        public String getChunkKey() { return chunkKey; }
        public int getLivingEntities() { return livingEntities; }
        public Map<Material, Integer> getBlockCounts() { return new EnumMap<>(blockCounts); }
        public Map<String, Integer> getCustomCounts() { return new ConcurrentHashMap<>(customCounts); }
        public boolean isSuccess() { return success; }
        public long getAnalysisTimeMs() { return analysisTimeMs; }
        public Exception getError() { return error; }
    }

    /**
     * Analyzes a chunk asynchronously and returns a CompletableFuture.
     * The analysis is performed on a background thread to avoid blocking the main server thread.
     *
     * @param chunk The chunk to analyze
     * @return CompletableFuture containing the analysis result
     */
    public static CompletableFuture<ChunkAnalysisResult> analyzeChunkAsync(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) {
            return CompletableFuture.completedFuture(
                    new ChunkAnalysisResult("invalid_chunk", new IllegalArgumentException("Chunk is null or not loaded"), 0)
            );
        }

        // Check cache first - if data exists and is valid, return immediately
        ChunkDataCache.ChunkData cachedData = ChunkDataCache.getCachedData(chunk);
        if (cachedData != null && cachedData.isComplete()) {
            ChunkAnalysisResult result = new ChunkAnalysisResult(
                    generateChunkKey(chunk),
                    cachedData.getLivingEntities(),
                    cachedData.getBlockCounts(),
                    cachedData.getCustomCounts(),
                    0 // No analysis time since it was cached
            );
            return CompletableFuture.completedFuture(result);
        }

        queuedAnalyses.incrementAndGet();

        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String chunkKey = generateChunkKey(chunk);

            try {
                // Verify chunk is still loaded
                if (!chunk.isLoaded()) {
                    throw new IllegalStateException("Chunk became unloaded during analysis");
                }

                // Perform the analysis
                int livingEntities = 0;
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof LivingEntity) {
                        livingEntities++;
                    }
                }

                // Count tile entities by material
                Map<Material, Integer> blockCounts = new EnumMap<>(Material.class);
                Map<String, Integer> customCounts = new ConcurrentHashMap<>();

                for (BlockState blockState : chunk.getTileEntities()) {
                    Material type = blockState.getType();
                    blockCounts.put(type, blockCounts.getOrDefault(type, 0) + 1);

                    // Special handling for shulker boxes (all colors)
                    if (Tag.SHULKER_BOXES.isTagged(type)) {
                        customCounts.put("all_shulker_boxes", customCounts.getOrDefault("all_shulker_boxes", 0) + 1);
                    }
                }

                // Calculate combined counts for related materials
                int allChests = blockCounts.getOrDefault(Material.CHEST, 0) + blockCounts.getOrDefault(Material.TRAPPED_CHEST, 0);
                customCounts.put("all_chests", allChests);

                int allFurnaces = blockCounts.getOrDefault(Material.FURNACE, 0) +
                        blockCounts.getOrDefault(Material.BLAST_FURNACE, 0) +
                        blockCounts.getOrDefault(Material.SMOKER, 0);
                customCounts.put("all_furnaces", allFurnaces);

                int allPistons = blockCounts.getOrDefault(Material.PISTON, 0) + blockCounts.getOrDefault(Material.STICKY_PISTON, 0);
                customCounts.put("all_pistons", allPistons);

                int allDroppersDispensers = blockCounts.getOrDefault(Material.DROPPER, 0) + blockCounts.getOrDefault(Material.DISPENSER, 0);
                customCounts.put("all_droppers_dispensers", allDroppersDispensers);

                long analysisTime = System.currentTimeMillis() - startTime;
                completedAnalyses.incrementAndGet();

                if (ConfigManager.isDebugEnabled()) {
                    Bukkit.getScheduler().runTask(LagXpert.getInstance(), () -> {
                        LagXpert.getInstance().getLogger().info("[AsyncChunkAnalyzer] Completed analysis for chunk " +
                                chunkKey + " in " + analysisTime + "ms");
                    });
                }

                return new ChunkAnalysisResult(chunkKey, livingEntities, blockCounts, customCounts, analysisTime);

            } catch (Exception e) {
                long analysisTime = System.currentTimeMillis() - startTime;
                failedAnalyses.incrementAndGet();

                if (ConfigManager.isDebugEnabled()) {
                    Bukkit.getScheduler().runTask(LagXpert.getInstance(), () -> {
                        LagXpert.getInstance().getLogger().warning("[AsyncChunkAnalyzer] Failed to analyze chunk " +
                                chunkKey + ": " + e.getMessage());
                    });
                }

                return new ChunkAnalysisResult(chunkKey, e, analysisTime);
            }
        }, executorService);
    }

    /**
     * Analyzes a chunk asynchronously and caches the result automatically.
     * Provides a callback for when the analysis is complete.
     *
     * @param chunk The chunk to analyze
     * @param onComplete Callback function called on the main thread when analysis is complete
     */
    public static void analyzeAndCache(Chunk chunk, Consumer<ChunkAnalysisResult> onComplete) {
        analyzeChunkAsync(chunk).thenAccept(result -> {
            // Cache the result if successful
            if (result.isSuccess()) {
                Bukkit.getScheduler().runTask(LagXpert.getInstance(), () -> {
                    ChunkDataCache.cacheData(chunk, result.getLivingEntities(),
                            result.getBlockCounts(), result.getCustomCounts(), true);
                    if (onComplete != null) {
                        onComplete.accept(result);
                    }
                });
            } else {
                // Still call the callback even if analysis failed
                if (onComplete != null) {
                    Bukkit.getScheduler().runTask(LagXpert.getInstance(), () -> {
                        onComplete.accept(result);
                    });
                }
            }
        });
    }

    /**
     * Analyzes multiple chunks asynchronously and returns when all are complete.
     *
     * @param chunks Array of chunks to analyze
     * @return CompletableFuture that completes when all chunk analyses are done
     */
    public static CompletableFuture<Map<String, ChunkAnalysisResult>> analyzeChunksAsync(Chunk... chunks) {
        if (chunks == null || chunks.length == 0) {
            return CompletableFuture.completedFuture(new ConcurrentHashMap<>());
        }

        Map<String, CompletableFuture<ChunkAnalysisResult>> futures = new ConcurrentHashMap<>();

        for (Chunk chunk : chunks) {
            if (chunk != null && chunk.isLoaded()) {
                String key = generateChunkKey(chunk);
                futures.put(key, analyzeChunkAsync(chunk));
            }
        }

        return CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<String, ChunkAnalysisResult> results = new ConcurrentHashMap<>();
                    futures.forEach((key, future) -> {
                        try {
                            results.put(key, future.get());
                        } catch (Exception e) {
                            results.put(key, new ChunkAnalysisResult(key, e, 0));
                        }
                    });
                    return results;
                });
    }

    /**
     * Analyzes chunks in batches to prevent overwhelming the thread pool.
     *
     * @param chunks Array of chunks to analyze
     * @param batchSize Number of chunks to process simultaneously
     * @param onBatchComplete Callback called after each batch completes
     * @return CompletableFuture that completes when all batches are processed
     */
    public static CompletableFuture<Void> analyzeBatches(Chunk[] chunks, int batchSize,
                                                         Consumer<Map<String, ChunkAnalysisResult>> onBatchComplete) {
        if (chunks == null || chunks.length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            for (int i = 0; i < chunks.length; i += batchSize) {
                int endIndex = Math.min(i + batchSize, chunks.length);
                Chunk[] batch = new Chunk[endIndex - i];
                System.arraycopy(chunks, i, batch, 0, endIndex - i);

                try {
                    Map<String, ChunkAnalysisResult> batchResults = analyzeChunksAsync(batch).get();

                    // Cache successful results and call callback on main thread
                    Bukkit.getScheduler().runTask(LagXpert.getInstance(), () -> {
                        batchResults.values().forEach(result -> {
                            if (result.isSuccess()) {
                                // Find the chunk by key to cache the result
                                for (Chunk chunk : batch) {
                                    if (chunk != null && generateChunkKey(chunk).equals(result.getChunkKey())) {
                                        ChunkDataCache.cacheData(chunk, result.getLivingEntities(),
                                                result.getBlockCounts(), result.getCustomCounts(), true);
                                        break;
                                    }
                                }
                            }
                        });

                        if (onBatchComplete != null) {
                            onBatchComplete.accept(batchResults);
                        }
                    });

                } catch (Exception e) {
                    if (ConfigManager.isDebugEnabled()) {
                        Bukkit.getScheduler().runTask(LagXpert.getInstance(), () -> {
                            LagXpert.getInstance().getLogger().warning("[AsyncChunkAnalyzer] Batch analysis failed: " + e.getMessage());
                        });
                    }
                }
            }
        }, executorService);
    }

    /**
     * Gets current statistics about the async analyzer performance.
     *
     * @return Map containing performance statistics
     */
    public static Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("thread_pool_size", THREAD_POOL_SIZE);
        stats.put("completed_analyses", completedAnalyses.get());
        stats.put("queued_analyses", queuedAnalyses.get());
        stats.put("failed_analyses", failedAnalyses.get());
        stats.put("active_threads", Thread.activeCount());

        int totalAnalyses = completedAnalyses.get() + failedAnalyses.get();
        double successRate = totalAnalyses > 0 ? (double) completedAnalyses.get() / totalAnalyses * 100 : 100.0;
        stats.put("success_rate_percent", successRate);

        return stats;
    }

    /**
     * Resets the statistics counters.
     */
    public static void resetStatistics() {
        completedAnalyses.set(0);
        queuedAnalyses.set(0);
        failedAnalyses.set(0);
    }

    /**
     * Generates a unique key for a chunk.
     */
    private static String generateChunkKey(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) {
            return "invalid_chunk";
        }
        return chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
    }

    /**
     * Shuts down the executor service gracefully.
     * Should be called when the plugin is disabled.
     */
    public static void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    /**
     * Checks if the async analyzer is currently processing any chunks.
     *
     * @return true if there are active analysis operations
     */
    public static boolean isActive() {
        return queuedAnalyses.get() > completedAnalyses.get() + failedAnalyses.get();
    }
}