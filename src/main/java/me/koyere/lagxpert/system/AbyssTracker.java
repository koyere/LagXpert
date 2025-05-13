package me.koyere.lagxpert.system;

/**
 * Tracks item removal and recovery stats for bStats metrics.
 * Data is polled and reset on each report.
 */
public class AbyssTracker {

    private static int removedItems = 0;
    private static int recoveredItems = 0;

    /**
     * Adds to the counter of removed ground items.
     * @param count Number of items removed
     */
    public static void addRemoved(int count) {
        removedItems += count;
    }

    /**
     * Adds to the counter of recovered items from Abyss.
     * @param count Number of items recovered
     */
    public static void addRecovered(int count) {
        recoveredItems += count;
    }

    /**
     * Returns the removed item count and resets the counter.
     */
    public static int pollRemoved() {
        int temp = removedItems;
        removedItems = 0;
        return temp;
    }

    /**
     * Returns the recovered item count and resets the counter.
     */
    public static int pollRecovered() {
        int temp = recoveredItems;
        recoveredItems = 0;
        return temp;
    }
}
