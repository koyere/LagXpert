package me.koyere.lagxpert.system;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks item removal and recovery statistics related to the Abyss system
 * for bStats metrics. Data is intended to be polled and reset periodically.
 * Uses AtomicInteger to ensure thread-safe operations on counters.
 */
public class AbyssTracker {

    // Using AtomicInteger for thread-safe counting operations.
    private static final AtomicInteger itemsAddedToAbyss = new AtomicInteger(0);
    private static final AtomicInteger itemsRecoveredFromAbyss = new AtomicInteger(0);

    /**
     * Increments the counter for items added to the Abyss system.
     * This method is thread-safe.
     *
     * @param count The number of items added to the Abyss.
     */
    public static void itemAddedToAbyss(int count) {
        if (count > 0) {
            itemsAddedToAbyss.addAndGet(count);
        }
    }

    /**
     * Increments the counter for items successfully recovered from the Abyss by players.
     * This method is thread-safe.
     *
     * @param count The number of items recovered from the Abyss.
     */
    public static void itemRecoveredFromAbyss(int count) {
        if (count > 0) {
            itemsRecoveredFromAbyss.addAndGet(count);
        }
    }

    /**
     * Retrieves the current count of items added to the Abyss since the last poll
     * and then resets this counter to zero.
     * This method is thread-safe.
     *
     * @return The number of items added to the Abyss since the last call.
     */
    public static int pollItemsAddedToAbyss() {
        return itemsAddedToAbyss.getAndSet(0);
    }

    /**
     * Retrieves the current count of items recovered from the Abyss since the last poll
     * and then resets this counter to zero.
     * This method is thread-safe.
     *
     * @return The number of items recovered from the Abyss since the last call.
     */
    public static int pollItemsRecoveredFromAbyss() {
        return itemsRecoveredFromAbyss.getAndSet(0);
    }
}