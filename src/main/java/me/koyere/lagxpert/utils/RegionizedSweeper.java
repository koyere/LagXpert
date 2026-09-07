package me.koyere.lagxpert.utils;

import me.koyere.lagxpert.LagXpert;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Runs a per-chunk operation across many worlds in a way that is correct on
 * Folia and gentler on the main thread everywhere else.
 *
 * <h3>Why this exists</h3>
 * The straightforward way to clean up a world is {@code world.getEntities()}
 * followed by {@code entity.remove()}. That is fine on Spigot and Paper, where
 * one thread owns everything, but it is wrong on Folia: there, each region owns
 * its own chunks and entities, and touching an entity from the wrong thread is
 * unsafe. Folia has no thread that may legally sweep an entire world at once.
 *
 * The only correct pattern is therefore: enumerate chunks, then perform the work
 * for each chunk on the thread that owns that chunk. This class encapsulates
 * that so callers do not have to think about threading.
 *
 * <h3>Behaviour per platform</h3>
 * <ul>
 *   <li><b>Folia</b> — each chunk is dispatched through the region scheduler, so
 *       every entity is touched by its owning thread.</li>
 *   <li><b>Spigot / Paper</b> — chunks are processed on the main thread in
 *       batches spread across ticks. This is not required for correctness, but it
 *       turns one long stalling tick into many short ones, which matters because
 *       these sweeps run precisely when the server is already struggling.</li>
 * </ul>
 *
 * <h3>Completion</h3>
 * Work is inherently asynchronous, so results arrive through a callback rather
 * than a return value. The callback runs once, on the main/global thread, after
 * every chunk has been visited. Callers that need a total therefore report it
 * when the sweep finishes instead of immediately.
 */
public final class RegionizedSweeper {

    /**
     * Default number of chunks handled per tick on non-Folia platforms.
     *
     * Chosen to keep a batch well under a tick's budget even when chunks are
     * entity-dense, while still finishing a few thousand chunks in seconds.
     */
    private static final int DEFAULT_BATCH_SIZE = 64;

    /**
     * Work performed on a single chunk.
     *
     * Implementations run on the thread that owns the chunk and may safely read
     * and mutate that chunk's entities. They must not touch other chunks.
     */
    public interface ChunkVisitor {
        /**
         * @param chunk a loaded chunk owned by the calling thread
         * @return how many items of interest were handled, for aggregation
         */
        int visit(Chunk chunk);
    }

    /** Aggregate outcome of a completed sweep. */
    public static class SweepResult {
        private final int total;
        private final int chunksVisited;
        private final int chunksSkipped;
        private final long durationMs;

        SweepResult(int total, int chunksVisited, int chunksSkipped, long durationMs) {
            this.total = total;
            this.chunksVisited = chunksVisited;
            this.chunksSkipped = chunksSkipped;
            this.durationMs = durationMs;
        }

        /** Sum of every visitor's return value. */
        public int getTotal() { return total; }

        public int getChunksVisited() { return chunksVisited; }

        /** Chunks that unloaded between enumeration and visiting. */
        public int getChunksSkipped() { return chunksSkipped; }

        public long getDurationMs() { return durationMs; }
    }

    private RegionizedSweeper() {
    }

    /**
     * Sweeps every loaded chunk of the given worlds.
     *
     * @param worlds     worlds to sweep; null or empty completes immediately
     * @param name       label used in debug logging
     * @param visitor    per-chunk work
     * @param onComplete receives the aggregate result on the main thread
     */
    public static void sweep(List<World> worlds, String name,
                             ChunkVisitor visitor, Consumer<SweepResult> onComplete) {
        sweep(worlds, name, DEFAULT_BATCH_SIZE, visitor, onComplete);
    }

    /**
     * Sweeps every loaded chunk of the given worlds with an explicit batch size.
     *
     * @param batchSize chunks per tick on non-Folia platforms; ignored on Folia
     */
    public static void sweep(List<World> worlds, String name, int batchSize,
                             ChunkVisitor visitor, Consumer<SweepResult> onComplete) {

        final long startedAt = System.currentTimeMillis();

        if (worlds == null || worlds.isEmpty()) {
            complete(onComplete, new SweepResult(0, 0, 0, 0L));
            return;
        }

        // Chunk enumeration itself must happen on a thread allowed to ask a world
        // for its loaded chunks, so hop to the main/global thread first.
        SchedulerWrapper.runTask(() -> {
            List<Chunk> chunks = new ArrayList<>();
            for (World world : worlds) {
                if (world == null) {
                    continue;
                }
                try {
                    Chunk[] loaded = world.getLoadedChunks();
                    for (Chunk chunk : loaded) {
                        chunks.add(chunk);
                    }
                } catch (Exception e) {
                    LagXpert.getInstance().getLogger().warning(
                            "[RegionizedSweeper] Could not enumerate chunks in world '" +
                                    world.getName() + "' for " + name + ": " + e.getMessage());
                }
            }

            if (chunks.isEmpty()) {
                complete(onComplete, new SweepResult(0, 0, 0,
                        System.currentTimeMillis() - startedAt));
                return;
            }

            if (PlatformDetector.isFolia()) {
                dispatchPerRegion(chunks, name, visitor, onComplete, startedAt);
            } else {
                dispatchInBatches(chunks, name, Math.max(1, batchSize), visitor, onComplete, startedAt);
            }
        });
    }

    /**
     * Folia path: one region task per chunk.
     *
     * A countdown tracks outstanding chunks so the completion callback fires
     * exactly once, after the last region task has run. The counter is
     * decremented in a finally block so a throwing visitor cannot strand the
     * sweep and leave the callback permanently unfired.
     */
    private static void dispatchPerRegion(List<Chunk> chunks, String name,
                                          ChunkVisitor visitor,
                                          Consumer<SweepResult> onComplete,
                                          long startedAt) {
        final AtomicInteger remaining = new AtomicInteger(chunks.size());
        final AtomicInteger total = new AtomicInteger(0);
        final AtomicInteger visited = new AtomicInteger(0);
        final AtomicInteger skipped = new AtomicInteger(0);
        final int expected = chunks.size();

        for (Chunk chunk : chunks) {
            SchedulerWrapper.runTaskForChunk(chunk, () -> {
                try {
                    if (!chunk.isLoaded()) {
                        skipped.incrementAndGet();
                        return;
                    }
                    int handled = visitor.visit(chunk);
                    if (handled > 0) {
                        total.addAndGet(handled);
                    }
                    visited.incrementAndGet();
                } catch (Exception e) {
                    skipped.incrementAndGet();
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().warning(
                                "[RegionizedSweeper] " + name + " failed on chunk " +
                                        chunk.getX() + "," + chunk.getZ() + ": " + e.getMessage());
                    }
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        finish(name, total.get(), visited.get(), skipped.get(),
                                expected, startedAt, onComplete);
                    }
                }
            });
        }
    }

    /**
     * Non-Folia path: process a fixed number of chunks per tick.
     *
     * Uses self-rescheduling delayed tasks rather than a repeating timer so the
     * sweep cannot outlive its own completion or overlap with itself.
     */
    private static void dispatchInBatches(List<Chunk> chunks, String name, int batchSize,
                                          ChunkVisitor visitor,
                                          Consumer<SweepResult> onComplete,
                                          long startedAt) {
        final int expected = chunks.size();
        final int[] cursor = {0};
        final int[] total = {0};
        final int[] visited = {0};
        final int[] skipped = {0};

        // Named holder so the runnable can reschedule itself.
        final Runnable[] step = new Runnable[1];

        step[0] = () -> {
            int end = Math.min(cursor[0] + batchSize, chunks.size());

            for (int i = cursor[0]; i < end; i++) {
                Chunk chunk = chunks.get(i);
                try {
                    if (!chunk.isLoaded()) {
                        skipped[0]++;
                        continue;
                    }
                    int handled = visitor.visit(chunk);
                    if (handled > 0) {
                        total[0] += handled;
                    }
                    visited[0]++;
                } catch (Exception e) {
                    skipped[0]++;
                    if (ConfigManager.isDebugEnabled()) {
                        LagXpert.getInstance().getLogger().warning(
                                "[RegionizedSweeper] " + name + " failed on chunk " +
                                        chunk.getX() + "," + chunk.getZ() + ": " + e.getMessage());
                    }
                }
            }

            cursor[0] = end;

            if (cursor[0] >= chunks.size()) {
                finish(name, total[0], visited[0], skipped[0], expected, startedAt, onComplete);
            } else {
                // One tick between batches keeps each tick short.
                SchedulerWrapper.runTaskLater(step[0], 1L);
            }
        };

        step[0].run();
    }

    private static void finish(String name, int total, int visited, int skipped,
                               int expected, long startedAt,
                               Consumer<SweepResult> onComplete) {
        long duration = System.currentTimeMillis() - startedAt;

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info(
                    "[RegionizedSweeper] " + name + " finished: " + total +
                            " handled across " + visited + "/" + expected + " chunk(s), " +
                            skipped + " skipped, " + duration + "ms");
        }

        complete(onComplete, new SweepResult(total, visited, skipped, duration));
    }

    /**
     * Invokes the completion callback on the main/global thread.
     *
     * On Folia the last region task runs on a region thread, so the hop is
     * required before the callback touches shared state or messages players.
     */
    /**
     * Counts entities across the given worlds without ever reading a chunk from
     * the wrong thread.
     *
     * {@code world.getEntities()} is the obvious way to do this and is fine on
     * Spigot and Paper, but on Folia it reaches across region boundaries. This
     * helper walks loaded chunks under the same dispatch rules as
     * {@link #sweep}, so the resulting figure is safe to obtain on every
     * platform. The count is delivered asynchronously as a consequence.
     *
     * @param worlds     worlds to count; null or empty yields 0
     * @param onComplete receives the total entity count on the main thread
     */
    public static void countEntities(List<World> worlds, Consumer<Integer> onComplete) {
        sweep(worlds, "entity-count", chunk -> chunk.getEntities().length,
                result -> onComplete.accept(result.getTotal()));
    }

    private static void complete(Consumer<SweepResult> onComplete, SweepResult result) {
        if (onComplete == null) {
            return;
        }
        SchedulerWrapper.runTask(() -> {
            try {
                onComplete.accept(result);
            } catch (Exception e) {
                LagXpert.getInstance().getLogger().warning(
                        "[RegionizedSweeper] Completion callback failed: " + e.getMessage());
            }
        });
    }
}
