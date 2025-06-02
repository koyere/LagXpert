package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Advanced redstone circuit tracking system that monitors redstone activity,
 * detects clock circuits, analyzes pulse frequencies, and manages automatic shutdowns
 * with configurable grace periods and whitelisting capabilities.
 */
public class RedstoneCircuitTracker {

    // Circuit tracking data structures
    private static final Map<String, RedstoneCircuit> activeCircuits = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastActivityTime = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> pulseCounters = new ConcurrentHashMap<>();
    private static final Set<String> whitelistedCircuits = ConcurrentHashMap.newKeySet();

    // Configuration constants
    private static final long CIRCUIT_TIMEOUT_MS = 60000; // 1 minute of inactivity = circuit considered dead
    private static final long PULSE_MEASUREMENT_WINDOW_MS = 10000; // Measure pulses over 10 seconds
    private static final int MAX_PULSES_PER_WINDOW = 200; // Maximum allowed pulses in measurement window
    private static final long CLEANUP_INTERVAL_TICKS = 1200L; // Cleanup every minute (20 ticks * 60)

    /**
     * Represents a tracked redstone circuit with its properties and activity data.
     */
    public static class RedstoneCircuit {
        private final String circuitId;
        private final Location primaryLocation;
        private final CircuitType type;
        private final long creationTime;
        private volatile long lastActivityTime;
        private volatile int totalPulses;
        private volatile boolean isWhitelisted;
        private volatile boolean isScheduledForShutdown;
        private volatile long graceEndTime;

        public RedstoneCircuit(String circuitId, Location primaryLocation, CircuitType type) {
            this.circuitId = circuitId;
            this.primaryLocation = primaryLocation.clone();
            this.type = type;
            this.creationTime = System.currentTimeMillis();
            this.lastActivityTime = this.creationTime;
            this.totalPulses = 0;
            this.isWhitelisted = false;
            this.isScheduledForShutdown = false;
            this.graceEndTime = 0;
        }

        // Getters
        public String getCircuitId() { return circuitId; }
        public Location getPrimaryLocation() { return primaryLocation.clone(); }
        public CircuitType getType() { return type; }
        public long getCreationTime() { return creationTime; }
        public long getLastActivityTime() { return lastActivityTime; }
        public int getTotalPulses() { return totalPulses; }
        public boolean isWhitelisted() { return isWhitelisted; }
        public boolean isScheduledForShutdown() { return isScheduledForShutdown; }
        public long getGraceEndTime() { return graceEndTime; }

        public void recordActivity() {
            this.lastActivityTime = System.currentTimeMillis();
            this.totalPulses++;
        }

        public void setWhitelisted(boolean whitelisted) {
            this.isWhitelisted = whitelisted;
        }

        public void scheduleShutdown(long graceTimeMs) {
            this.isScheduledForShutdown = true;
            this.graceEndTime = System.currentTimeMillis() + graceTimeMs;
        }

        public void cancelShutdown() {
            this.isScheduledForShutdown = false;
            this.graceEndTime = 0;
        }

        public boolean isGraceExpired() {
            return isScheduledForShutdown && System.currentTimeMillis() > graceEndTime;
        }

        public long getAgeMs() {
            return System.currentTimeMillis() - creationTime;
        }

        public double getPulsesPerSecond() {
            long ageMs = getAgeMs();
            if (ageMs <= 0) return 0.0;
            return (double) totalPulses / (ageMs / 1000.0);
        }
    }

    /**
     * Types of redstone circuits that can be detected and tracked.
     */
    public enum CircuitType {
        CLOCK,          // Repeating circuits (clocks)
        PULSE,          // Single pulse circuits
        CONTINUOUS,     // Continuously active circuits
        COMPLEX,        // Complex multi-component circuits
        UNKNOWN         // Unclassified circuits
    }

    /**
     * Records redstone activity at a specific location and analyzes circuit patterns.
     *
     * @param location The location where redstone activity occurred
     * @param material The type of redstone component that activated
     */
    public static void recordRedstoneActivity(Location location, Material material) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        String locationKey = generateLocationKey(location);
        long currentTime = System.currentTimeMillis();

        // Update last activity time
        lastActivityTime.put(locationKey, currentTime);

        // Increment pulse counter for frequency analysis
        pulseCounters.computeIfAbsent(locationKey, k -> new AtomicInteger(0)).incrementAndGet();

        // Get or create circuit
        RedstoneCircuit circuit = activeCircuits.get(locationKey);
        if (circuit == null) {
            CircuitType detectedType = detectCircuitType(location, material);
            circuit = new RedstoneCircuit(locationKey, location, detectedType);
            activeCircuits.put(locationKey, circuit);

            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info(
                        "[RedstoneCircuitTracker] New " + detectedType + " circuit detected at " +
                                locationToString(location)
                );
            }
        }

        // Record activity in the circuit
        circuit.recordActivity();

        // Check if circuit should be flagged for shutdown
        analyzeCircuitForShutdown(circuit);
    }

    /**
     * Detects the type of redstone circuit based on the component and surrounding blocks.
     */
    private static CircuitType detectCircuitType(Location location, Material material) {
        // Simple heuristic-based detection
        switch (material) {
            case REPEATER:
            case COMPARATOR:
                // Check for clock patterns by looking at nearby repeaters
                if (hasNearbyRepeaters(location, 3)) {
                    return CircuitType.CLOCK;
                }
                return CircuitType.PULSE;

            case REDSTONE_WIRE:
                // Analyze surrounding redstone components
                if (hasComplexRedstonePattern(location)) {
                    return CircuitType.COMPLEX;
                }
                return CircuitType.CONTINUOUS;

            case REDSTONE_TORCH:
            case REDSTONE_WALL_TORCH:
                return CircuitType.CONTINUOUS;

            default:
                return CircuitType.UNKNOWN;
        }
    }

    /**
     * Checks if there are repeaters nearby that might indicate a clock circuit.
     */
    private static boolean hasNearbyRepeaters(Location center, int radius) {
        if (center.getWorld() == null) return false;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = center.clone().add(x, y, z).getBlock();
                    if (block.getType() == Material.REPEATER || block.getType() == Material.COMPARATOR) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Analyzes if the location has a complex redstone pattern.
     */
    private static boolean hasComplexRedstonePattern(Location center) {
        if (center.getWorld() == null) return false;

        int redstoneComponents = 0;
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block block = center.clone().add(x, y, z).getBlock();
                    if (isRedstoneComponent(block.getType())) {
                        redstoneComponents++;
                    }
                }
            }
        }
        return redstoneComponents >= 5; // 5+ redstone components = complex
    }

    /**
     * Checks if a material is a redstone component.
     */
    private static boolean isRedstoneComponent(Material material) {
        switch (material) {
            case REDSTONE_WIRE:
            case REPEATER:
            case COMPARATOR:
            case REDSTONE_TORCH:
            case REDSTONE_WALL_TORCH:
            case REDSTONE_BLOCK:
            case OBSERVER:
            case PISTON:
            case STICKY_PISTON:
                return true;
            default:
                return false;
        }
    }

    /**
     * Analyzes a circuit to determine if it should be scheduled for shutdown.
     */
    private static void analyzeCircuitForShutdown(RedstoneCircuit circuit) {
        if (circuit.isWhitelisted() || circuit.isScheduledForShutdown()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        String locationKey = circuit.getCircuitId();

        // Check pulse frequency
        AtomicInteger pulseCounter = pulseCounters.get(locationKey);
        if (pulseCounter != null) {
            // Reset counter and check if it exceeded limits
            int pulsesInWindow = pulseCounter.getAndSet(0);

            if (pulsesInWindow > MAX_PULSES_PER_WINDOW) {
                scheduleCircuitShutdown(circuit, "high_frequency", getGracePeriod(circuit.getType()));
                return;
            }
        }

        // Check continuous activity duration
        long activeDuration = currentTime - circuit.getCreationTime();
        long maxDuration = getMaxDuration(circuit.getType());

        if (activeDuration > maxDuration) {
            scheduleCircuitShutdown(circuit, "long_duration", getGracePeriod(circuit.getType()));
        }
    }

    /**
     * Schedules a circuit for shutdown with a grace period.
     */
    private static void scheduleCircuitShutdown(RedstoneCircuit circuit, String reason, long graceTimeMs) {
        circuit.scheduleShutdown(graceTimeMs);

        if (ConfigManager.isDebugEnabled()) {
            LagXpert.getInstance().getLogger().info(
                    "[RedstoneCircuitTracker] Circuit at " + locationToString(circuit.getPrimaryLocation()) +
                            " scheduled for shutdown due to: " + reason + ". Grace period: " + (graceTimeMs / 1000) + "s"
            );
        }

        // Schedule the actual shutdown
        new BukkitRunnable() {
            @Override
            public void run() {
                if (circuit.isGraceExpired()) {
                    shutdownCircuit(circuit, reason);
                }
            }
        }.runTaskLater(LagXpert.getInstance(), graceTimeMs / 50); // Convert ms to ticks
    }

    /**
     * Actually shuts down a circuit by breaking the redstone wire.
     */
    private static void shutdownCircuit(RedstoneCircuit circuit, String reason) {
        Location location = circuit.getPrimaryLocation();
        if (location.getWorld() == null || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }

        Block block = location.getBlock();
        if (block.getType() == Material.REDSTONE_WIRE) {
            block.setType(Material.AIR);

            if (ConfigManager.isDebugEnabled()) {
                LagXpert.getInstance().getLogger().info(
                        "[RedstoneCircuitTracker] Shutdown circuit at " + locationToString(location) +
                                " due to: " + reason
                );
            }
        }

        // Remove from tracking
        String locationKey = circuit.getCircuitId();
        activeCircuits.remove(locationKey);
        lastActivityTime.remove(locationKey);
        pulseCounters.remove(locationKey);
    }

    /**
     * Gets the maximum allowed duration for a circuit type.
     */
    private static long getMaxDuration(CircuitType type) {
        switch (type) {
            case CLOCK:
                return ConfigManager.getRedstoneActiveTicks() * 50L; // Convert ticks to ms
            case CONTINUOUS:
                return 300000L; // 5 minutes
            case COMPLEX:
                return 600000L; // 10 minutes
            case PULSE:
                return 60000L;  // 1 minute
            default:
                return 180000L; // 3 minutes
        }
    }

    /**
     * Gets the grace period before shutdown for a circuit type.
     */
    private static long getGracePeriod(CircuitType type) {
        switch (type) {
            case CLOCK:
                return 10000L; // 10 seconds
            case CONTINUOUS:
                return 30000L; // 30 seconds
            case COMPLEX:
                return 60000L; // 1 minute
            default:
                return 20000L; // 20 seconds
        }
    }

    /**
     * Adds a circuit location to the whitelist.
     */
    public static void whitelistCircuit(Location location) {
        String locationKey = generateLocationKey(location);
        whitelistedCircuits.add(locationKey);

        RedstoneCircuit circuit = activeCircuits.get(locationKey);
        if (circuit != null) {
            circuit.setWhitelisted(true);
            circuit.cancelShutdown();
        }
    }

    /**
     * Removes a circuit location from the whitelist.
     */
    public static void unwhitelistCircuit(Location location) {
        String locationKey = generateLocationKey(location);
        whitelistedCircuits.remove(locationKey);

        RedstoneCircuit circuit = activeCircuits.get(locationKey);
        if (circuit != null) {
            circuit.setWhitelisted(false);
        }
    }

    /**
     * Starts the cleanup task that removes inactive circuits.
     */
    public static void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupInactiveCircuits();
            }
        }.runTaskTimer(LagXpert.getInstance(), CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
    }

    /**
     * Cleans up circuits that have been inactive for too long.
     */
    private static void cleanupInactiveCircuits() {
        long currentTime = System.currentTimeMillis();
        AtomicInteger cleanedUp = new AtomicInteger(0);

        activeCircuits.entrySet().removeIf(entry -> {
            RedstoneCircuit circuit = entry.getValue();
            if (currentTime - circuit.getLastActivityTime() > CIRCUIT_TIMEOUT_MS) {
                String locationKey = entry.getKey();
                lastActivityTime.remove(locationKey);
                pulseCounters.remove(locationKey);
                cleanedUp.incrementAndGet();
                return true;
            }
            return false;
        });

        if (ConfigManager.isDebugEnabled() && cleanedUp.get() > 0) {
            LagXpert.getInstance().getLogger().info(
                    "[RedstoneCircuitTracker] Cleaned up " + cleanedUp.get() + " inactive circuits"
            );
        }
    }

    /**
     * Gets statistics about currently tracked circuits.
     */
    public static Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();

        stats.put("total_circuits", activeCircuits.size());
        stats.put("whitelisted_circuits", whitelistedCircuits.size());

        Map<CircuitType, Integer> typeCount = new ConcurrentHashMap<>();
        int scheduledForShutdown = 0;

        for (RedstoneCircuit circuit : activeCircuits.values()) {
            typeCount.merge(circuit.getType(), 1, Integer::sum);
            if (circuit.isScheduledForShutdown()) {
                scheduledForShutdown++;
            }
        }

        stats.put("circuits_by_type", typeCount);
        stats.put("circuits_scheduled_shutdown", scheduledForShutdown);

        return stats;
    }

    /**
     * Generates a unique location key for tracking.
     */
    private static String generateLocationKey(Location location) {
        if (location.getWorld() == null) {
            return "invalid_location";
        }
        return location.getWorld().getName() + "_" +
                location.getBlockX() + "_" +
                location.getBlockY() + "_" +
                location.getBlockZ();
    }

    /**
     * Converts a location to a readable string.
     */
    private static String locationToString(Location location) {
        if (location == null || location.getWorld() == null) {
            return "invalid_location";
        }
        return location.getWorld().getName() + " (" +
                location.getBlockX() + ", " +
                location.getBlockY() + ", " +
                location.getBlockZ() + ")";
    }

    /**
     * Clears all tracked data. Used during plugin reload.
     */
    public static void clearAll() {
        activeCircuits.clear();
        lastActivityTime.clear();
        pulseCounters.clear();
        whitelistedCircuits.clear();
    }
}