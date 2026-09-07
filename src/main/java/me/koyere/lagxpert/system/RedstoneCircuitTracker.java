package me.koyere.lagxpert.system;

import me.koyere.lagxpert.LagXpert;
import me.koyere.lagxpert.utils.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
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

    // Configuration constants (loaded from redstone.yml, defaults shown)
    private static long circuitTimeoutMs = 60000;
    private static long pulseMeasurementWindowMs = 10000;
    private static int maxPulsesPerWindow = 200;
    private static long cleanupIntervalTicks = 1200L;
    private static int maxCircuitSize = 200;
    private static int maxBreaksPerShutdown = 50;
    private static int escalateThreshold = 3;
    private static boolean floodFillEnabled = true;

    // Per-circuit-type durations and grace periods (milliseconds)
    private static long maxDurationContinuous = 300000L;
    private static long maxDurationComplex = 600000L;
    private static long maxDurationPulse = 60000L;
    private static long maxDurationDefault = 180000L;
    private static long gracePeriodClock = 10000L;
    private static long gracePeriodContinuous = 30000L;
    private static long gracePeriodComplex = 60000L;
    private static long gracePeriodDefault = 20000L;

    // Detection parameters
    private static int nearbyRepeaterRadius = 3;
    private static int complexPatternRadiusXZ = 2;
    private static int complexPatternRadiusY = 1;
    private static int complexPatternThreshold = 5;

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
                if (hasNearbyRepeaters(location, nearbyRepeaterRadius)) {
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
        for (int x = -complexPatternRadiusXZ; x <= complexPatternRadiusXZ; x++) {
            for (int y = -complexPatternRadiusY; y <= complexPatternRadiusY; y++) {
                for (int z = -complexPatternRadiusXZ; z <= complexPatternRadiusXZ; z++) {
                    Block block = center.clone().add(x, y, z).getBlock();
                    if (isRedstoneComponent(block.getType())) {
                        redstoneComponents++;
                    }
                }
            }
        }
        return redstoneComponents >= complexPatternThreshold;
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

        // EmergencyController: disable all clocks during emergency/critical
        if (EmergencyController.getInstance().shouldDisableRedstoneClocks()) {
            scheduleCircuitShutdown(circuit, "emergency_disable", 0);
            return;
        }

        long currentTime = System.currentTimeMillis();
        String locationKey = circuit.getCircuitId();

        // Redstone tolerance scales with server health: a struggling server puts up
        // with fewer pulses and shorter continuous activity before intervening.
        double redstoneMultiplier = AdaptiveThresholdEngine.getInstance()
                .getMultiplier(AdaptiveThresholdEngine.LimitCategory.REDSTONE);

        // Check pulse frequency
        AtomicInteger pulseCounter = pulseCounters.get(locationKey);
        if (pulseCounter != null) {
            // Reset counter and check if it exceeded limits
            int pulsesInWindow = pulseCounter.getAndSet(0);

            int effectiveMaxPulses = Math.max(1, (int) Math.floor(maxPulsesPerWindow * redstoneMultiplier));

            if (pulsesInWindow > effectiveMaxPulses) {
                scheduleCircuitShutdown(circuit, "high_frequency", getGracePeriod(circuit.getType()));
                return;
            }
        }

        // Check continuous activity duration
        long activeDuration = currentTime - circuit.getCreationTime();
        long maxDuration = (long) Math.max(1000L, getMaxDuration(circuit.getType()) * redstoneMultiplier);

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
     * Actually shuts down a circuit using flood-fill to find ALL connected
     * redstone components, then breaks the circuit at its weakest points.
     * If flood-fill fails (too large / errors), falls back to single-block break.
     */
    private static void shutdownCircuit(RedstoneCircuit circuit, String reason) {
        Location origin = circuit.getPrimaryLocation();
        if (origin.getWorld() == null ||
                !origin.getWorld().isChunkLoaded(origin.getBlockX() >> 4, origin.getBlockZ() >> 4)) {
            return;
        }

        int broken;
        if (floodFillEnabled) {
            try {
                // Attempt flood-fill to find all connected redstone components
                Set<Location> circuitBlocks = floodFillCircuit(origin);
                broken = breakCircuitBlocks(circuitBlocks, origin.getWorld());
            } catch (Exception e) {
                // Fallback: break just the origin block
                LagXpert.getInstance().getLogger().warning(
                        "[RedstoneCircuitTracker] Flood-fill failed, falling back to single-block break: " +
                                e.getMessage());
                broken = breakSingleBlock(origin);
            }
        } else {
            broken = breakSingleBlock(origin);
        }

        if (broken > 0) {
            ActionLogger.getInstance().log(
                    ActionLogger.ActionType.REDSTONE_CIRCUIT_BROKEN,
                    origin.getWorld() != null ? origin.getWorld().getName() : "-",
                    circuit.getCircuitId(),
                    "Reason: " + reason + ", Type: " + circuit.getType().name() + ", Blocks broken: " + broken,
                    broken, "auto", true, 0);
        }

        // Remove from tracking
        String locationKey = circuit.getCircuitId();
        activeCircuits.remove(locationKey);
        lastActivityTime.remove(locationKey);
        pulseCounters.remove(locationKey);
    }

    /**
     * Flood-fill algorithm: finds all connected redstone component blocks
     * starting from the origin location. Uses BFS with a safety cap.
     */
    private static Set<Location> floodFillCircuit(Location start) {
        Set<Location> visited = new HashSet<>();
        Queue<Location> queue = new LinkedList<>();
        queue.add(start.clone());

        while (!queue.isEmpty() && visited.size() < maxCircuitSize) {
            Location current = queue.poll();
            if (!visited.add(current)) continue;
            if (current.getWorld() == null) continue;

            // Check all 6 adjacent blocks (not diagonals — redstone connects face-to-face)
            int[][] offsets = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}};
            for (int[] offset : offsets) {
                Location neighbor = current.clone().add(offset[0], offset[1], offset[2]);
                if (visited.contains(neighbor)) continue;

                if (isRedstoneComponent(neighbor.getBlock().getType())) {
                    queue.add(neighbor);
                }
            }
        }

        return visited;
    }

    /**
     * Breaks redstone components in the identified circuit blocks.
     * Prioritizes redstone wire (safest to break), then repeaters/comparators.
     * Drops items naturally so players can recover materials.
     */
    private static int breakCircuitBlocks(Set<Location> circuitBlocks, org.bukkit.World world) {
        int broken = 0;

        // First pass: break redstone wires (safest, least destructive)
        for (Location loc : circuitBlocks) {
            if (broken >= maxBreaksPerShutdown) break;
            if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;

            org.bukkit.block.Block block = loc.getBlock();
            if (block.getType() == Material.REDSTONE_WIRE ||
                    block.getType() == Material.REDSTONE_TORCH ||
                    block.getType() == Material.REDSTONE_WALL_TORCH) {
                block.breakNaturally();
                broken++;
            }
        }

        // Second pass: if circuit still likely active, break repeaters/comparators
        if (broken < escalateThreshold) {
            for (Location loc : circuitBlocks) {
                if (broken >= maxBreaksPerShutdown) break;
                if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;

                org.bukkit.block.Block block = loc.getBlock();
                if (block.getType() == Material.REPEATER ||
                        block.getType() == Material.COMPARATOR) {
                    block.breakNaturally();
                    broken++;
                }
            }
        }

        // Third pass: if STILL no blocks broken, break any redstone component
        if (broken == 0) {
            for (Location loc : circuitBlocks) {
                if (broken >= maxBreaksPerShutdown) break;
                if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;

                org.bukkit.block.Block block = loc.getBlock();
                if (isRedstoneComponent(block.getType()) && block.getType() != Material.REDSTONE_BLOCK) {
                    block.breakNaturally();
                    broken++;
                }
            }
        }

        return broken;
    }

    /**
     * Fallback: breaks a single redstone wire block at the origin.
     */
    private static int breakSingleBlock(Location location) {
        if (location.getWorld() == null ||
                !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return 0;
        }
        org.bukkit.block.Block block = location.getBlock();
        if (block.getType() == Material.REDSTONE_WIRE) {
            block.breakNaturally();
            return 1;
        }
        return 0;
    }

    /**
     * Gets the maximum allowed duration for a circuit type.
     */
    private static long getMaxDuration(CircuitType type) {
        switch (type) {
            case CLOCK:
                return ConfigManager.getRedstoneActiveTicks() * 50L;
            case CONTINUOUS:
                return maxDurationContinuous;
            case COMPLEX:
                return maxDurationComplex;
            case PULSE:
                return maxDurationPulse;
            default:
                return maxDurationDefault;
        }
    }

    private static long getGracePeriod(CircuitType type) {
        switch (type) {
            case CLOCK:
                return gracePeriodClock;
            case CONTINUOUS:
                return gracePeriodContinuous;
            case COMPLEX:
                return gracePeriodComplex;
            default:
                return gracePeriodDefault;
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
     * Loads configuration from redstone.yml. Called on plugin startup and reload.
     */
    public static void loadConfig() {
        java.io.File file = new java.io.File(
                LagXpert.getInstance().getDataFolder(), "redstone.yml");
        if (!file.exists()) return;

        org.bukkit.configuration.file.FileConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        String path = "circuit-tracker.";

        // Detection
        nearbyRepeaterRadius = config.getInt(path + "detection.nearby-repeater-radius", 3);
        complexPatternRadiusXZ = config.getInt(path + "detection.complex-pattern-radius-xz", 2);
        complexPatternRadiusY = config.getInt(path + "detection.complex-pattern-radius-y", 1);
        complexPatternThreshold = config.getInt(path + "detection.complex-pattern-threshold", 5);

        // Pulses
        pulseMeasurementWindowMs = config.getLong(path + "pulses.measurement-window-ms", 10000);
        maxPulsesPerWindow = config.getInt(path + "pulses.max-pulses-per-window", 200);

        // Max durations
        maxDurationContinuous = config.getLong(path + "max-duration.continuous-circuit", 300000);
        maxDurationComplex = config.getLong(path + "max-duration.complex-circuit", 600000);
        maxDurationPulse = config.getLong(path + "max-duration.pulse-circuit", 60000);
        maxDurationDefault = config.getLong(path + "max-duration.unknown-circuit", 180000);

        // Grace periods
        gracePeriodClock = config.getLong(path + "grace-periods.clock-circuit", 10000);
        gracePeriodContinuous = config.getLong(path + "grace-periods.continuous-circuit", 30000);
        gracePeriodComplex = config.getLong(path + "grace-periods.complex-circuit", 60000);
        gracePeriodDefault = config.getLong(path + "grace-periods.unknown-circuit", 20000);

        // Shutdown
        floodFillEnabled = config.getBoolean(path + "shutdown.flood-fill-enabled", true);
        maxCircuitSize = config.getInt(path + "shutdown.max-circuit-size", 200);
        maxBreaksPerShutdown = config.getInt(path + "shutdown.max-breaks-per-shutdown", 50);
        escalateThreshold = config.getInt(path + "shutdown.escalate-threshold", 3);

        // Cleanup
        circuitTimeoutMs = config.getLong(path + "cleanup.inactivity-timeout-ms", 60000);
        cleanupIntervalTicks = config.getLong(path + "cleanup.interval-ticks", 1200);
    }

    /**
     * Starts the cleanup task that removes inactive circuits.
     */
    public static void startCleanupTask() {
        loadConfig(); // Load config before starting
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupInactiveCircuits();
            }
        }.runTaskTimer(LagXpert.getInstance(), cleanupIntervalTicks, cleanupIntervalTicks);
    }

    /**
     * Cleans up circuits that have been inactive for too long.
     */
    private static void cleanupInactiveCircuits() {
        long currentTime = System.currentTimeMillis();
        AtomicInteger cleanedUp = new AtomicInteger(0);

        activeCircuits.entrySet().removeIf(entry -> {
            RedstoneCircuit circuit = entry.getValue();
            if (currentTime - circuit.getLastActivityTime() > circuitTimeoutMs) {
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