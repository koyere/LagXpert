# LagXpert Refactor Plan

> **From Monitoring Panel to Autonomous Optimizer**  
> Target: v2.7 → v3.0 | Estimated: 4 phases | Fallback-first approach

---

## Executive Summary

### Current State
LagXpert v2.6.1 is a well-architected Java/Bukkit plugin with 52 classes, 17 YAML configs, multi-platform support (Folia/Paper/Spigot), and Bedrock compatibility. However, **~65% of its code is dedicated to detection, alerting, and reporting, while only ~35% performs corrective actions**. The plugin excels at telling you there's a problem but rarely solves it autonomously.

### Target State
Transform LagXpert into a **closed-loop optimization system** where every detection feeds into a corrective action, with graduated responses, configurable automation levels, and comprehensive audit trails — all with safe fallbacks.

### Key Metric
```
Current:  Detect ──► Alert ──► Human must act
Target:   Detect ──► Analyze ──► Act automatically ──► Verify ──► Report
```

### Risk Mitigation Philosophy
Every automated action follows this pattern:
1. **Check preconditions** — is the action safe right now?
2. **Execute with limits** — never exceed per-tick/per-cycle caps
3. **Verify result** — did it help? Did it hurt?
4. **Fallback** — if verification fails, roll back and alert
5. **Audit** — log what was done, when, why, and result

---

## Architecture Target Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    EMERGENCY CONTROLLER                      │
│              (replaces inert LagShield)                      │
│   State: NORMAL │ WARNING │ CRITICAL │ EMERGENCY            │
│   Graduated responses per state                              │
└──────────┬────────────────────────────────────┬──────────────┘
           │                                    │
    ┌──────▼──────┐                      ┌──────▼──────┐
    │  DETECTION   │                     │   ACTION     │
    │  LAYER       │                     │   LAYER      │
    │              │                     │              │
    │ TPSMonitor   │──── triggers ──────►│ SmartMobMgr  │
    │ PerfTracker  │                     │ EntityClean  │
    │ ChunkScanner │                     │ ItemCleaner  │
    │ CktTracker   │                     │ ChunkUnloader│
    │ Listeners    │                     │ ExplosionCtrl│
    └──────┬───────┘                     │ MobAIOpt     │
           │                             │ RedstoneCut  │
           │                             │ CapReducer   │
           │                             └──────────────┘
           │
    ┌──────▼──────┐
    │  UNIFIED    │
    │  ALERT      │    Single cooldown manager
    │  PIPELINE   │    Rate-limited, batched
    │             │    Console + Player + ActionLog
    └─────────────┘
```

---

## Phase 0 — Safety Net & Foundation (v2.7)

**Goal:** Build the infrastructure that makes automation safe. Nothing changes for end users yet.

### 0.1 — EmergencyController (replaces LagShield)

**Current problem:** `LagShield` has `activateShield()`/`deactivateShield()` that set boolean flags, log messages, and broadcast. But `getMobCapMultiplier()` and `shouldBlockNaturalSpawns()` are **never called by any other class**. The shield is a hollow shell.

**Implementation:**

```
NEW: system/EmergencyController.java (singleton)
```

#### State Machine
```java
public enum ServerState {
    NORMAL,      // TPS >= recovery threshold && RAM < recovery threshold
    WARNING,     // TPS < warning threshold || RAM > warning threshold  
    CRITICAL,    // TPS < critical threshold || RAM > critical threshold
    EMERGENCY    // TPS < critical for > 30s consecutively
}
```

#### Graduated Actions Per State

| Action | NORMAL | WARNING | CRITICAL | EMERGENCY |
|--------|--------|---------|----------|-----------|
| Mob cap multiplier | 1.0x | 0.75x | 0.5x | 0.25x |
| Block natural spawns | No | No | Yes | Yes |
| Force item cleanup | No | No | Yes | Yes |
| Aggressive chunk unload | No | No | Yes | Yes |
| Pause chunk preloader | No | Yes | Yes | Yes |
| Reduce view-distance | No | No | No | Yes (if API) |
| Disable redstone clocks | No | No | Yes | Yes |
| Freeze AI for far mobs | No | 48 blocks | 32 blocks | 16 blocks |
| Broadcast to players | No | No | Yes | Yes |
| Log to audit trail | Yes | Yes | Yes | Yes |

#### Integration Points
- `EntityListener.onCreatureSpawn()` → calls `EmergencyController.getEffectiveMobLimit(world)` instead of raw `ConfigManager.getMaxMobsPerChunk(world)`
- `SmartMobManager.processChunkMobs()` → same
- `EntityCleanupTask.run()` → checks `EmergencyController.shouldForceCleanup()` to trigger mid-cycle
- `ItemCleanerTask.run()` → checks `EmergencyController.shouldForceCleanup()` 
- `ChunkPreloader` → checks `EmergencyController.isPreloadingAllowed()`
- `InactiveChunkUnloader` → checks `EmergencyController.getAggressiveUnloadMultiplier()`
- `MobAIOptimizer` → checks `EmergencyController.getAIDistanceThreshold()`
- `RedstoneCircuitTracker` → checks `EmergencyController.shouldDisableClocks()`

#### Fallback & Safety
```java
// Every state transition validates:
public boolean canTransitionTo(ServerState target) {
    // 1. Minimum 10s between state changes (prevents flapping)
    // 2. Require 3 consecutive readings before ESCALATING
    // 3. Require 5 consecutive readings before DE-ESCALATING
    // 4. Never go EMERGENCY if < 5 players online (false positive risk)
    // 5. Log every transition with snapshot of all metrics
}

// Manual override via command
// /lagxpert emergency override <on|off|status>
// /lagxpert emergency force-normal  (admin panic button)
```

#### Config: lagshield.yml → emergency-controller.yml
```yaml
emergency-controller:
  enabled: true
  
  # State transition hysteresis
  stability:
    min-state-duration-seconds: 10
    escalation-confirmation-readings: 3
    de-escalation-confirmation-readings: 5
    skip-when-no-players-online: true
    min-players-for-emergency: 5
  
  # Graduated responses per state
  responses:
    warning:
      mob-cap-multiplier: 0.75
      block-natural-spawns: false
      force-item-cleanup: false
      aggressive-chunk-unload: false
      pause-chunk-preloader: true
      reduce-view-distance: false
      disable-redstone-clocks: false
      ai-distance-threshold: 48
      broadcast-alert: false
    
    critical:
      mob-cap-multiplier: 0.5
      block-natural-spawns: true
      force-item-cleanup: true
      aggressive-chunk-unload: true
      unload-inactivity-minutes: 2         # override normal 15min
      pause-chunk-preloader: true
      reduce-view-distance: false
      disable-redstone-clocks: true
      ai-distance-threshold: 32
      broadcast-alert: true
      emergency-commands: []                # custom commands to run
    
    emergency:
      mob-cap-multiplier: 0.25
      block-natural-spawns: true
      force-item-cleanup: true
      aggressive-chunk-unload: true
      unload-inactivity-minutes: 1
      pause-chunk-preloader: true
      reduce-view-distance: true
      reduced-view-distance: 4
      disable-redstone-clocks: true
      disable-all-redstone: true            # nuclear option
      ai-distance-threshold: 16
      freeze-all-ai: true
      broadcast-alert: true
      emergency-commands:
        - "save-all"
        - "say &c[LagXpert] Emergency optimization active - expect reduced functionality"
  
  # Recovery confirmation
  recovery:
    auto-de-escalate: true
    broadcast-recovery: true
    recovery-message: "&aServer performance has recovered. All restrictions lifted."
  
  # Manual control
  manual:
    allow-force-normal: true
    force-normal-permission: "lagxpert.admin.emergency"
```

### 0.2 — Unified Alert Pipeline

**Current problem:** Three separate cooldown/alert systems exist:
1. `AlertCooldownManager` (used by listeners + AutoChunkScanTask)
2. Cooldown config in `alerts.yml` (per-type cooldowns)
3. Cooldown config in `monitoring.yml` (TPS/memory/lag-spike cooldowns)

**Implementation:**

```
REFACTOR: system/AlertCooldownManager.java → system/AlertPipeline.java
```

```java
public class AlertPipeline {
    
    public enum AlertLevel { DEBUG, INFO, WARNING, CRITICAL, EMERGENCY }
    
    public enum AlertTarget { CONSOLE, PLAYERS_WITH_PERM, AFFECTED_PLAYERS, ALL_PLAYERS }
    
    /**
     * Single entry point for ALL plugin alerts.
     * Handles: rate-limiting, deduplication, batching, formatting, delivery
     */
    public static void send(AlertContext ctx) {
        // 1. Check if alert type is enabled in config
        // 2. Check global rate limit (burst protection)
        // 3. Check per-player cooldown
        // 4. Check per-chunk/world cooldown
        // 5. Format message with all placeholders
        // 6. Deliver to configured targets
        // 7. Log to audit trail if configured
    }
    
    // Rate limiting:
    // - Global: max N alerts per second total
    // - Per-type: max N alerts per type per minute  
    // - Per-player: max N alerts per player per minute
    // - Per-chunk: 1 alert per chunk per cooldown period
    
    // Deduplication:
    // - Same alert type + same chunk + same player → suppress within cooldown
    // - Batches similar alerts into single message
    
    // Emergency mode:
    // - All rate limits relaxed during CRITICAL/EMERGENCY state
    // - Critical alerts always delivered regardless of cooldowns
}
```

#### Config Consolidation
Move ALL alert cooldown settings into a single `alerts.yml` section:
```yaml
alerts:
  global:
    max-alerts-per-second: 10
    consolidation-window-seconds: 5    # batch similar alerts
  
  cooldowns:
    default-seconds: 15
    per-type:
      tps-critical: 30
      tps-warning: 60
      memory-critical: 60
      memory-warning: 120
      lag-spike: 15
      mob-limit: 20
      near-limit: 30
      storage-limit: 20
      redstone-cut: 10
      chunk-overload: 15
```

### 0.3 — Action Audit Trail

**Current problem:** The plugin has extensive statistics tracking but no record of WHAT ACTIONS it took. Admins can't answer "what did LagXpert do in the last hour?"

**Implementation:**

```
NEW: system/ActionLogger.java (singleton)
```

```java
public class ActionLogger {
    
    public enum ActionType {
        // Corrective actions
        MOB_REMOVED, ENTITY_CLEANED, ITEM_CLEARED,
        REDSTONE_DISABLED, CHUNK_UNLOADED, EXPLOSION_LIMITED,
        SPAWN_BLOCKED, PLACEMENT_BLOCKED, VEHICLE_BLOCKED,
        
        // State changes
        STATE_TRANSITION, EMERGENCY_ACTIVATED, EMERGENCY_DEACTIVATED,
        
        // Manual actions
        MANUAL_OPTIMIZE, MANUAL_CLEAR, MANUAL_RELOAD,
        CONFIG_CHANGED
    }
    
    public static class ActionRecord {
        ActionType type;
        String world;
        String chunkKey;       // nullable
        String detail;         // human-readable
        long timestamp;
        int count;             // how many affected
        String triggeredBy;    // "auto", "emergency", "player:Name"
        boolean successful;
        long durationMs;       // how long the action took
    }
    
    // Circular buffer, last 10000 actions
    // Queryable by command: /lagxpert actions [page] [filter]
    // Auto-rotated: keeps last 7 days
    // Exportable: /lagxpert actions export → JSON file
    
    public static void log(ActionRecord record) { ... }
    public static List<ActionRecord> getRecent(int count) { ... }
    public static List<ActionRecord> query(ActionType type, String world, long since) { ... }
}
```

### 0.4 — Graceful Degradation Patterns

**Every corrective system** must implement:

```java
public interface ThrottledAction {
    
    /**
     * Returns true if this system is healthy enough to act.
     * Checks: is module enabled? Is system overloaded? Did last action fail?
     */
    boolean canAct();
    
    /**
     * Execute with safety limits.
     * @return ActionResult with success/failure and metrics
     */
    ActionResult execute(int maxOperations);
    
    /**
     * Verify the action helped. Called after execute().
     * If degradation detected, triggers fallback.
     */
    boolean verify(ActionResult result);
    
    /**
     * Undo or mitigate if verification failed.  
     */
    void fallback(ActionResult result);
}

class ActionResult {
    boolean success;
    int operationsPerformed;
    long durationMs;
    String errorDetail;
    Map<String, Object> beforeMetrics;   // snapshot before action
    Map<String, Object> afterMetrics;    // snapshot after action
}
```

---

## Phase 1 — Close the Loop (v2.7)

**Goal:** Every detection system feeds into at least one corrective action. Zero detections that only alert without acting.

### 1.1 — AutoChunkScanTask: From Informer to Enforcer

**Current behavior:** Scans chunks, finds overloads, sends chat messages. 356 lines of code with ZERO corrective actions.

**Target behavior:**

```java
@Override
public void run() {
    for (World world : Bukkit.getWorlds()) {
        for (Chunk chunk : getChunksNearPlayers(world)) {
            ChunkData data = analyzeChunk(chunk);
            
            for (OverloadIssue issue : data.getIssues()) {
                // 1. LOG the issue to audit trail
                ActionLogger.log(new ActionRecord(
                    ActionType.CHUNK_OVERLOAD_DETECTED,
                    world.getName(), chunkKey, 
                    issue.getDetail(), issue.getCount()
                ));
                
                // 2. TAKE CORRECTIVE ACTION based on issue type
                switch (issue.getType()) {
                    case MOBS_EXCEEDED:
                        // Delegate to SmartMobManager immediately
                        int removed = SmartMobManager.getInstance()
                            .processChunkImmediately(chunk);
                        ActionLogger.log(...);
                        break;
                        
                    case STORAGE_EXCEEDED:
                        // Log precise coordinates for admin review
                        // Don't break blocks automatically (too destructive)
                        // But DO increase alert severity
                        issue.logPreciseLocations();
                        break;
                        
                    case REDSTONE_OVERLOAD:
                        // Escalate to RedstoneCircuitTracker
                        RedstoneCircuitTracker.escalateCircuit(chunk);
                        break;
                }
                
                // 3. ALERT only if action wasn't sufficient
                if (!issue.isResolved()) {
                    AlertPipeline.send(new AlertContext(
                        AlertLevel.WARNING,
                        issue.getAffectedPlayers(),
                        issue.getResolvedMessage()
                    ));
                }
            }
            
            // 4. VERIFY: after 2 scan cycles, check if chunk improved
            scheduleVerification(chunk, data.snapshot());
        }
    }
}
```

#### Config Addition (task.yml)
```yaml
task:
  scan-interval-ticks: 600
  
  # NEW: What to do when overloads are detected
  auto-correction:
    enabled: true                    # Master toggle
    
    # Per-issue-type correction policies
    policies:
      mobs:
        action: remove-excess        # none | warn-only | remove-excess
        max-removed-per-scan: 50
      storage:
        action: warn-only            # none | warn-only (never auto-break)
      redstone:
        action: disable-clocks       # none | warn-only | disable-clocks
      tnt:
        action: warn-only            # none | warn-only | clear-primed
        max-cleared-per-scan: 20
    
    # Verification
    verify-after-cycles: 2
    if-no-improvement: escalate-to-admin
```

### 1.2 — TPSMonitor → EmergencyController Bridge

**Current:** TPSMonitor calculates TPS and calls `LagShield.onTick()` which does nothing.

**Target:** TPSMonitor feeds directly into EmergencyController's state machine.

```java
// In TPSMonitor.run():
EmergencyController.getInstance().evaluate(
    currentTPS,
    shortTermTPS,
    memoryUsagePercent,
    Bukkit.getOnlinePlayers().size(),
    getActivePlayerCount()  // players who moved recently
);
```

### 1.3 — PerformanceTracker → Graduated Response

**Current:** Detects memory/chunk/lag-spike issues and sends alerts.

**Target:** Triggers graduated responses before alerting.

```java
// In PerformanceTracker.evaluate():
if (memoryUsage > criticalMemoryThreshold) {
    // BEFORE alerting, try to free memory
    EmergencyController.getInstance().requestMemoryRecovery();
    // Memory recovery actions:
    // 1. Clear chunk data cache
    // 2. Suggest GC
    // 3. Aggressive chunk unloading
    // Then alert only if still critical after 30s
}

if (chunksLoaded > maxChunksWarning) {
    // BEFORE alerting, force unload cycle
    InactiveChunkUnloader.triggerForceCycle();
    // Then alert only if still excessive after next cycle
}
```

### 1.4 — EntityListener Spawn Prevention with Correction

**Current:** Blocks spawn. That's it.

**Target:** When blocking a spawn because limit is reached, also triggers SmartMobManager for that chunk to create room.

```java
if (livingEntitiesInChunk >= mobLimit) {
    event.setCancelled(true);
    
    // NEW: Proactively clean the chunk so next spawn can succeed
    SmartMobManager.getInstance().processChunkImmediately(chunk);
    
    // Only alert if cleaning didn't help
    ...
}
```

---

## Phase 2 — Complete Unfinished Systems (v2.8)

**Goal:** Fix every system that exists in code but doesn't actually work.

### 2.1 — VehicleManager: Actually Remove Abandoned Vehicles

**Current:** `runCleanupTask()` checks conditions for abandoned minecarts but **never removes them**. The removal block is empty.

**Fix:**

```java
private void runCleanupTask() {
    if (!enabled || !removeAbandonedLootCarts) return;
    
    int removed = 0;
    long now = System.currentTimeMillis();
    long abandonTimeoutMs = ConfigManager.getAbandonedVehicleTimeoutMs();
    
    for (World world : Bukkit.getWorlds()) {
        if (isDisabledWorld(world)) continue;
        
        for (Chunk chunk : world.getLoadedChunks()) {
            for (Entity entity : chunk.getEntities()) {
                if (!(entity instanceof Minecart)) continue;
                Minecart cart = (Minecart) entity;
                
                // Define "abandoned": no passengers, stopped, not recently interacted
                if (cart.getPassengers().isEmpty() 
                    && cart.getVelocity().lengthSquared() < 0.0001
                    && isAbandoned(cart, now, abandonTimeoutMs)) {
                    
                    // Safety: don't remove if it has items inside AND is near a player
                    if (cart instanceof StorageMinecart) {
                        StorageMinecart storageCart = (StorageMinecart) cart;
                        if (!storageCart.getInventory().isEmpty() 
                            && isPlayerNear(cart.getLocation(), 16)) {
                            continue; // Player might be using it
                        }
                    }
                    
                    cart.remove();
                    removed++;
                    ActionLogger.log(ActionType.VEHICLE_REMOVED, ...);
                    
                    // Rate limit
                    if (removed >= MAX_REMOVALS_PER_CYCLE) break;
                }
            }
        }
    }
}
```

#### New: Abandonment Tracking
```java
// Track the last time a vehicle was interacted with
Map<UUID, Long> vehicleLastInteraction = new ConcurrentHashMap<>();

@EventHandler
public void onVehicleEnter(VehicleEnterEvent event) {
    vehicleLastInteraction.put(event.getVehicle().getUniqueId(), System.currentTimeMillis());
}

@EventHandler  
public void onInventoryOpen(InventoryOpenEvent event) {
    if (event.getInventory().getHolder() instanceof Minecart) {
        vehicleLastInteraction.put(
            ((Minecart) event.getInventory().getHolder()).getUniqueId(), 
            System.currentTimeMillis()
        );
    }
}
```

### 2.2 — RedstoneCircuitTracker: Full Circuit Breaker

**Current:** When `shutdownCircuit()` is called, it breaks **one redstone wire block**. A complex circuit has many paths — breaking one wire doesn't stop it.

**Fix: Full Circuit Analysis & Shutdown**

```java
private static void shutdownCircuit(RedstoneCircuit circuit, String reason) {
    Location origin = circuit.getPrimaryLocation();
    
    // 1. FLOOD-FILL to find ALL connected redstone components
    Set<Location> circuitBlocks = floodFillCircuit(origin);
    
    // 2. Identify the "cutting points" — locations that if removed, break the circuit
    Set<Location> cutPoints = identifyCutPoints(circuitBlocks);
    
    // 3. If cut points found, break those (minimal destruction)
    //    If no clear cut points, break all redstone wires in the circuit
    Set<Location> toBreak = !cutPoints.isEmpty() ? cutPoints : 
        circuitBlocks.stream()
            .filter(loc -> isRedstoneWire(loc))
            .collect(Collectors.toSet());
    
    // 4. Execute with safety limits
    int broken = 0;
    for (Location loc : toBreak) {
        if (broken >= MAX_BREAKS_PER_SHUTDOWN) break;
        Block block = loc.getBlock();
        if (isBreakable(block)) {
            // Drop the item so player can recover it
            block.breakNaturally();
            broken++;
            ActionLogger.log(ActionType.REDSTONE_DISABLED, ...);
        }
    }
    
    // 5. Notify players near the circuit
    notifyCircuitPlayers(circuitBlocks, reason, broken);
    
    // 6. Schedule re-check: if circuit rebuilds within 60s, it's intentional
    scheduleCircuitRebuildCheck(circuit.getCircuitId(), circuitBlocks);
}
```

#### Flood Fill Algorithm
```java
private static Set<Location> floodFillCircuit(Location start) {
    Set<Location> visited = new HashSet<>();
    Queue<Location> queue = new LinkedList<>();
    queue.add(start);
    
    while (!queue.isEmpty() && visited.size() < MAX_CIRCUIT_SIZE) {
        Location current = queue.poll();
        if (!visited.add(current)) continue;
        
        // Check all 6 adjacent blocks
        for (Location neighbor : getAdjacent(current)) {
            if (isRedstoneComponent(neighbor.getBlock().getType()) 
                && !visited.contains(neighbor)) {
                queue.add(neighbor);
            }
        }
    }
    return visited;
}
```

### 2.3 — AbilityLimiter: Working Riptide Cooldown

**Current:** `PlayerRiptideEvent` is not cancellable in Spigot API. The cooldown logic exists but can't block the action.

**Fix: Multi-layered approach**

```java
@EventHandler(priority = EventPriority.HIGHEST)
public void onRiptide(PlayerRiptideEvent event) {
    if (!enabled) return;
    Player player = event.getPlayer();
    if (isDisabledWorld(player.getWorld())) return;
    if (player.hasPermission("lagxpert.bypass.abilities")) return;
    
    if (disableRiptide) {
        // Cannot cancel RiptideEvent, so cancel the velocity instead
        Bukkit.getScheduler().runTask(LagXpert.getInstance(), () -> {
            player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        });
        ActionLogger.log(ActionType.ABILITY_BLOCKED, ...);
        return;
    }
    
    long now = System.currentTimeMillis();
    Long lastUse = riptideCooldowns.get(player.getUniqueId());
    
    if (lastUse != null && (now - lastUse) < riptideCooldownMs) {
        // On cooldown: cancel velocity next tick
        Bukkit.getScheduler().runTask(LagXpert.getInstance(), () -> {
            player.setVelocity(player.getVelocity().multiply(-0.5)); // bounce back
            MessageManager.sendActionBar(player, 
                MessageManager.color("&cRiptide on cooldown! &7Wait " + 
                ((riptideCooldownMs - (now - lastUse)) / 1000) + "s"));
        });
    } else {
        riptideCooldowns.put(player.getUniqueId(), now);
    }
}

// ADDITIONAL: Also hook into ProjectileLaunchEvent for tridents
@EventHandler
public void onProjectileLaunch(ProjectileLaunchEvent event) {
    if (!enabled || !disableRiptide) return;
    if (!(event.getEntity() instanceof Trident)) return;
    if (!(event.getEntity().getShooter() instanceof Player)) return;
    
    Player player = (Player) event.getEntity().getShooter();
    if (player.hasPermission("lagxpert.bypass.abilities")) return;
    
    // Check if trident has Riptide enchantment
    Trident trident = (Trident) event.getEntity();
    ItemStack item = trident.getItem();
    if (item != null && item.containsEnchantment(Enchantment.RIPTIDE)) {
        event.setCancelled(true); // This IS cancellable
        ActionLogger.log(ActionType.ABILITY_BLOCKED, ...);
    }
}
```

### 2.4 — ConsoleFilter: Proper Resource Management

**Current:** Injects into root logger but has race conditions on reload.

**Fix:**

```java
public class ConsoleFilter implements Filter {
    private volatile boolean enabled;  // volatile for thread safety
    private final List<Pattern> patterns = new CopyOnWriteArrayList<>(); // thread-safe
    private final AtomicBoolean injected = new AtomicBoolean(false);
    
    private void injectFilter() {
        Logger rootLogger = Bukkit.getLogger();
        // Preserve existing filter with chain of responsibility
        Filter existingFilter = rootLogger.getFilter();
        
        if (existingFilter != null && existingFilter != this) {
            // Chain: our filter → existing filter
            this.chainedFilter = existingFilter;
        }
        
        rootLogger.setFilter(this);
        injected.set(true);
    }
    
    @Override
    public boolean isLoggable(LogRecord record) {
        if (!enabled) {
            // Pass through to chained filter if exists
            return chainedFilter == null || chainedFilter.isLoggable(record);
        }
        
        String message = record.getMessage();
        if (message == null) return true;
        
        for (Pattern pattern : patterns) {
            if (pattern.matcher(message).matches()) {
                if (forwardToAdmins) {
                    // Schedule async to avoid blocking logger thread
                    Bukkit.getScheduler().runTask(LagXpert.getInstance(), 
                        () -> forwardMessageToAdmins(message));
                }
                
                // Still pass through chained filter
                if (chainedFilter != null) {
                    return chainedFilter.isLoggable(record);
                }
                return false;
            }
        }
        return true;
    }
    
    // NEW: Hot-reload patterns without removing filter
    public void reloadPatterns(List<String> newPatterns) {
        patterns.clear();
        for (String regex : newPatterns) {
            try {
                patterns.add(Pattern.compile(regex));
            } catch (PatternSyntaxException e) {
                LagXpert.getInstance().getLogger()
                    .warning("[ConsoleFilter] Invalid regex: " + regex);
            }
        }
    }
}
```

---

## Phase 3 — Proactive Intelligence (v2.9)

**Goal:** Prevent problems before they impact TPS. Stop being reactive, start being predictive.

### 3.1 — Adaptive Thresholds

**Concept:** Static limits (mobs-per-chunk: 40) don't account for server load. When server is healthy, allow more. When struggling, restrict preemptively.

```java
NEW: system/AdaptiveThresholdEngine.java
```

```java
public class AdaptiveThresholdEngine {
    
    /**
     * Calculates dynamic limits based on server health.
     * 
     * Base limit × ServerHealthFactor × WorldFactor × TimeFactor
     */
    public static int getAdaptiveLimit(String limitType, World world) {
        int baseLimit = ConfigManager.getBaseLimit(limitType, world);
        double healthFactor = getServerHealthFactor();
        double worldFactor = getWorldLoadFactor(world);
        double timeFactor = getTimeOfDayFactor();
        
        int adaptiveLimit = (int) (baseLimit * healthFactor * worldFactor * timeFactor);
        
        // Never go below 25% of base limit (safety floor)
        return Math.max(adaptiveLimit, baseLimit / 4);
    }
    
    private static double getServerHealthFactor() {
        double tps = TPSMonitor.getShortTermTPS();
        if (tps >= 19.5) return 1.0;    // Full capacity
        if (tps >= 18.0) return 0.85;   // Slight reduction
        if (tps >= 15.0) return 0.65;   // Moderate reduction
        return 0.40;                     // Severe reduction
    }
    
    private static double getWorldLoadFactor(World world) {
        // Worlds with many loaded chunks get lower limits
        int loadedChunks = world.getLoadedChunks().length;
        int playerCount = world.getPlayers().size();
        
        double chunksPerPlayer = playerCount > 0 
            ? (double) loadedChunks / playerCount 
            : loadedChunks;
        
        if (chunksPerPlayer < 50) return 1.0;
        if (chunksPerPlayer < 100) return 0.9;
        if (chunksPerPlayer < 200) return 0.75;
        return 0.6;
    }
    
    private static double getTimeOfDayFactor() {
        // Optional: if server has peak hours, pre-reduce limits before peak
        // This requires historical data (see 3.2)
        return 1.0; // Default: no time-based adjustment
    }
}
```

#### Integration
All limit getters in `ConfigManager` route through adaptive engine:
```java
// OLD:
public static int getMaxMobsPerChunk(World world) {
    return config.getInt("limits.mobs-per-chunk", 40);
}

// NEW:
public static int getMaxMobsPerChunk(World world) {
    if (AdaptiveThresholdEngine.isEnabled()) {
        return AdaptiveThresholdEngine.getAdaptiveLimit("mobs", world);
    }
    return config.getInt("limits.mobs-per-chunk", 40);
}
```

### 3.2 — Performance History & Trend Analysis

```java
NEW: system/PerformanceHistory.java
```

Stores 7 days of 5-minute snapshots:
- TPS (short/medium/long)
- Memory usage
- Chunks loaded
- Entities total / per world
- Players online
- Actions taken (from ActionLogger)

Provides:
```java
// "At this time on weekdays, TPS typically drops to 17. Pre-reduce limits"
public static TimeSeriesPrediction predictNextHour();

// "This world's entity count grows 200/hour. Will hit limit in ~3 hours"
public static TrendAnalysis analyzeWorldTrend(World world);

// "The most common action taken is mob removal in world_nether"
public static ActionHeatmap getActionHeatmap(int days);
```

### 3.3 — Smart Scheduling

**Current:** All tasks run on fixed intervals regardless of server load.

**Target:** Tasks adapt their timing based on server state.

```java
NEW: system/SmartScheduler.java
```

```java
public class SmartScheduler {
    
    public static void scheduleAdaptive(Runnable task, int baseInterval, TaskPriority priority) {
        // During NORMAL: run at baseInterval
        // During WARNING: run at baseInterval * 1.5 (less frequent non-critical tasks)
        // During CRITICAL: pause non-critical tasks, run critical at baseInterval / 2
        // During EMERGENCY: only run emergency tasks, pause all others
        
        EmergencyController.getInstance().addStateListener((oldState, newState) -> {
            switch (newState) {
                case NORMAL:
                    task.resume(baseInterval);
                    break;
                case WARNING:
                    if (priority == TaskPriority.LOW) task.pause();
                    else task.adjustInterval((int)(baseInterval * 1.3));
                    break;
                case CRITICAL:
                    if (priority != TaskPriority.CRITICAL) task.pause();
                    else task.adjustInterval(baseInterval / 2);
                    break;
                case EMERGENCY:
                    if (priority != TaskPriority.EMERGENCY) task.pause();
                    else task.runImmediately(); // don't wait for interval
                    break;
            }
        });
    }
    
    public enum TaskPriority {
        LOW,        // ChunkPreloader, ChunkActivityCleanup
        NORMAL,     // AutoChunkScan, EntityCleanup
        HIGH,       // ItemCleaner
        CRITICAL,   // SmartMobManager, EntityCleanup (during warning+)
        EMERGENCY   // EmergencyController responses
    }
}
```

### 3.4 — Preemptive Optimization Profiles

```java
NEW: system/OptimizationProfile.java
```

Pre-configured profiles that admins can switch between:

```yaml
# profiles.yml
profiles:
  # Good for small servers (< 20 players)
  relaxed:
    mobs-per-chunk: 50
    item-cleaner-interval: 18000    # 15 min
    chunk-unload-threshold: 30      # 30 min inactivity
    preload-radius: 5
    ai-optimizer: minimal
    
  # Default balanced profile
  balanced:
    mobs-per-chunk: 40
    item-cleaner-interval: 6000     # 5 min
    chunk-unload-threshold: 15
    preload-radius: 4
    ai-optimizer: standard
    
  # For large servers (> 50 players) or low-end hardware
  aggressive:
    mobs-per-chunk: 25
    item-cleaner-interval: 3000     # 2.5 min
    chunk-unload-threshold: 5       # 5 min inactivity
    preload-radius: 2
    ai-optimizer: aggressive
    adaptive-limits: true
    
  # Maximum optimization, may impact gameplay
  performance:
    mobs-per-chunk: 15
    item-cleaner-interval: 1200     # 1 min
    chunk-unload-threshold: 2
    preload-radius: 0               # disabled
    ai-optimizer: extreme
    adaptive-limits: true
    disable-redstone-clocks: true
```

Command: `/lagxpert profile <name>` — switches profile with confirmation and rollback timer (auto-revert after 5 min if admin disconnects).

---

## Phase 4 — Administration & UX (v3.0)

**Goal:** Server admins have complete visibility and control, without being overwhelmed.

### 4.1 — `/lagxpert optimize` One-Click Command

```java
// Performs a full optimization pass:
// 1. Force entity cleanup
// 2. Force item cleanup
// 3. Force smart mob removal
// 4. Force chunk unload of inactive chunks
// 5. Clear caches
// 6. Reset redstone circuit tracking
// 7. Dump performance before/after snapshot

// Usage:
// /lagxpert optimize           → full pass
// /lagxpert optimize mobs      → only mob-related
// /lagxpert optimize chunks    → only chunk-related
// /lagxpert optimize dry-run   → report what WOULD be done, don't do it
// /lagxpert optimize auto      → schedule automatic optimization hourly

// Permission: lagxpert.admin.optimize
```

```java
public class OptimizeCommand {
    
    public static OptimizationResult runFullOptimization(CommandSender sender) {
        OptimizationResult result = new OptimizationResult();
        long startTime = System.currentTimeMillis();
        
        // Snapshot BEFORE
        result.beforeSnapshot = captureSystemSnapshot();
        
        // Phase 1: Entities
        result.entityCleanup = EntityCleanupTask.runImmediate();
        sender.sendMessage(formatPhase("Entity Cleanup", result.entityCleanup));
        
        // Phase 2: Mobs  
        result.mobRemoval = SmartMobManager.getInstance().runFullPass();
        sender.sendMessage(formatPhase("Mob Removal", result.mobRemoval));
        
        // Phase 3: Items
        result.itemCleanup = ItemCleanerTask.runImmediate();
        sender.sendMessage(formatPhase("Item Cleanup", result.itemCleanup));
        
        // Phase 4: Chunks
        result.chunksUnloaded = InactiveChunkUnloader.triggerForceCycle();
        sender.sendMessage(formatPhase("Chunk Unloading", result.chunksUnloaded));
        
        // Phase 5: Cache
        result.cacheCleared = ChunkUtils.clearAllCache();
        sender.sendMessage(formatPhase("Cache Clear", result.cacheCleared));
        
        // Snapshot AFTER
        result.afterSnapshot = captureSystemSnapshot();
        result.durationMs = System.currentTimeMillis() - startTime;
        
        // Show summary
        sender.sendMessage(result.getSummary());
        
        // Log to audit
        ActionLogger.log(ActionType.MANUAL_OPTIMIZE, result);
        
        return result;
    }
}
```

### 4.2 — Action Dashboard (GUI Enhancement)

Add a new GUI section: **"Recent Actions"**

```
┌──────────────────────────────────┐
│   LagXpert - Action Dashboard    │
├──────────────────────────────────┤
│ Server State: NORMAL             │
│ Last Action: 45s ago             │
│                                  │
│ [Last 10 Minutes]                │
│  Mob Cleanup:     243 removed    │
│  Items Cleared:   1,240 items    │
│  Chunks Unloaded: 18 chunks      │
│  Spawns Blocked:  56 blocked     │
│  Redstone Cuts:   2 disabled     │
│                                  │
│ [Performance Impact]             │
│  TPS Before: 18.2 → After: 19.5 │
│  Memory Freed:  ~120MB           │
│                                  │
│ [Optimization Profiles]          │
│  Current: Aggressive             │
│  [Switch to Balanced]            │
│                                  │
│ [Emergency Controls]             │
│  [Force Optimize Now]            │
│  [Activate Emergency Mode]       │
│  [Reset All Counters]            │
└──────────────────────────────────┘
```

### 4.3 — Scheduled Reports

Configurable daily/weekly summary broadcast or file export:

```yaml
# monitoring.yml addition
analytics:
  reports:
    daily-reports: true
    daily-report-time: "06:00"
    weekly-summaries: true
    
    # NEW
    include-actions-taken: true     # list what LagXpert DID
    include-efficiency-metrics: true # before/after comparisons
    recipients:
      console: true
      permission: "lagxpert.reports.receive"
      export-file: true
      export-format: json            # json | csv | text
      max-export-files: 30           # auto-delete oldest
```

### 4.4 — Configuration Wizard

First-run experience improvement:

```java
// On first install, instead of just dumping all configs:
// 1. Detect server hardware (cores, RAM)
// 2. Detect player count history (if available)
// 3. Recommend an optimization profile
// 4. Apply with confirmation

// /lagxpert setup wizard → guided setup
```

---

---

## Progress Status

### Phase 0 — Safety Net & Foundation ✅ COMPLETED (2026-05-26)
| # | Task | Status |
|---|------|--------|
| 0.1 | `EmergencyController` — state machine NORMAL→WARNING→CRITICAL→EMERGENCY | ✅ |
| 0.2 | `AlertPipeline` — unified alert entry point with rate limiting | ✅ |
| 0.3 | `ActionLogger` — circular buffer audit trail (10K entries) | ✅ |
| 0.4 | `ThrottledAction` — safety interface for corrective actions | ✅ |
| — | `LagShield` rewritten as `@Deprecated` passthrough to EmergencyController | ✅ |
| — | `TPSMonitor` wired directly to EmergencyController with player counts | ✅ |
| — | `PerformanceTracker` alerts delivered via AlertPipeline | ✅ |
| — | `emergency-controller.yml` config created | ✅ |
| — | `messages.yml` i18n keys added | ✅ |

### Phase 1 — Close the Loop ✅ COMPLETED (2026-05-26)
| # | Task | Status |
|---|------|--------|
| 1.1 | `EntityListener` → EmergencyController dynamic mob limits + SmartMobManager trigger + ActionLogger | ✅ |
| 1.2 | `SmartMobManager` → EmergencyController limits + ActionLogger | ✅ |
| 1.3 | `AutoChunkScanTask` → delegates mob removal to SmartMobManager instead of just alerting | ✅ |
| 1.4 | `EntityCleanupTask` → ActionLogger audit logging | ✅ |
| 1.5 | `ItemCleanerTask` → ActionLogger audit logging | ✅ |
| 1.6 | `ChunkPreloader` → pauses during WARNING/CRITICAL/EMERGENCY | ✅ |
| 1.7 | `InactiveChunkUnloader` → ActionLogger + ChunkManager uses EmergencyController threshold | ✅ |
| 1.8 | `MobAIOptimizer` → dynamic distance threshold from EmergencyController | ✅ |
| 1.9 | `RedstoneCircuitTracker` → disables clocks during emergency + ActionLogger | ✅ |
| 1.10 | `StorageListener` → ActionLogger for blocked placements | ✅ |
| 1.11 | `ExplosionController` → ActionLogger for limited explosions | ✅ |

### Phase 2 — Complete & Fix ✅ COMPLETED (2026-05-26)
| # | Task | Status |
|---|------|--------|
| 2.1 | `VehicleManager` — actually remove abandoned minecarts/boats + interaction tracking + ActionLogger | ✅ |
| 2.2 | `RedstoneCircuitTracker` — flood-fill full circuit breaker (BFS, 3-pass break: wires→repeaters→any) | ✅ |
| 2.3 | `AbilityLimiter` — working riptide cooldown via velocity cancel + ProjectileLaunchEvent hook | ✅ |
| 2.4 | `ConsoleFilter` — thread-safe CopyOnWriteArrayList, chain of responsibility, async forwarding | ✅ |
| 2.5 | **Config Audit** — 39 hardcodes eliminados, todo controlado por YAML | ✅ |

### Phase 4 — Administration & UX ✅ COMPLETED (2026-05-26)
| # | Task | Status |
|---|------|--------|
| 4.1 | `/lagxpert optimize` — full 5-phase optimization pass with before/after metrics | ✅ |
| 4.2 | `/lagxpert status` — text dashboard: state, TPS, memory, adaptive multipliers, recent actions, trends | ✅ |
| 4.3 | `/lagxpert emergency [status|force-normal]` — emergency controls with full status display | ✅ |
| 4.4 | Help menu updated with all new subcommands + tab completion | ✅ |
| — | `messages.yml` updated with help entries | ✅ |
| — | `EntityCleanupTask.runImmediate()` added for manual trigger support | ✅ |
| # | Task | Status |
|---|------|--------|
| 3.1 | `AdaptiveThresholdEngine` — dynamic limits for ALL categories (mobs/storage/entities/redstone) | ✅ |
| 3.2 | `PerformanceHistory` — 7-day circular buffer, trend analysis, peak prediction, CSV persistence | ✅ |
| 3.3 | `SmartScheduler` — adaptive task intervals (LOW/NORMAL/HIGH/CRITICAL/EMERGENCY) | ✅ |
| 3.4 | `profiles.yml` — 4 presets: relaxed/balanced/aggressive/performance | ✅ |
| — | `config.yml` — new sections: `adaptive-thresholds` + `performance-history` | ✅ |
| — | LagXpert.java lifecycle integration (init + shutdown) | ✅ |
### Phase 4 — Administration & UX ⬜ NOT STARTED

---

## Implementation Order & Dependencies

```
Phase 0 (Foundation)         Phase 1 (Close Loop)       Phase 2 (Fix Bugs)
─────────────────────       ─────────────────────       ─────────────────
0.1 EmergencyController ───► 1.1 AutoChunkScan fix ───► 2.1 VehicleManager
0.2 AlertPipeline                                            │
0.3 ActionLogger         ───► 1.2 TPS→Emergency bridge      │
0.4 ThrottledAction      ───► 1.3 PerfTracker→Response  ───► 2.2 Redstone
                              1.4 EntityListener fix    ───► 2.3 AbilityLim
                                                         ───► 2.4 ConsoleFilter

Phase 3 (Intelligence)         Phase 4 (UX)
─────────────────────         ────────────
3.1 AdaptiveThresholds ───►   4.1 /lagxpert optimize
3.2 PerformanceHistory  ───►  4.2 Action Dashboard
3.3 SmartScheduler      ───►  4.3 Scheduled Reports
3.4 OptimizationProfile ───►  4.4 Config Wizard
```

**Phase 0 is BLOCKING** — all other phases depend on EmergencyController, AlertPipeline, and ActionLogger.

---

## Fallback Strategy Per Component

| Component | Failure Mode | Fallback | Recovery |
|-----------|-------------|----------|----------|
| EmergencyController | Null state / corrupted | Fallback to NORMAL state, all multipliers = 1.0 | `/lagxpert emergency force-normal` |
| AlertPipeline | Exception during send | Log to console directly, skip alert | Auto-retry next cycle |
| ActionLogger | Buffer full / disk full | Drop oldest entries, log warning | Auto-truncate at 10000 entries |
| AutoChunkScan corrective | Exception during corrective action | Skip correction, fall back to alert-only | Next scan cycle retries |
| AdaptiveThresholds | Calculation error | Fall back to static ConfigManager limits | `/lagxpert reload` resets |
| SmartScheduler | Task stuck / NPE | Fall back to fixed-interval scheduling | Task watchdog restarts after 2x interval |
| VehicleManager cleanup | ConcurrentModification | Skip problematic chunk, log error | Next cycle retries |
| Redstone flood-fill | Infinite loop / too large | Hard cap at MAX_CIRCUIT_SIZE (200 blocks) | Circuit marked as "complex", scheduled for later |
| Emergency commands | Command syntax error | Log error, continue with next command | Admin notified in console |

---

## Testing & Verification Strategy

### Per-Phase Verification

**Phase 0:**
- Unit test: `EmergencyController` state transitions with mock TPS/Memory data
- Unit test: `AlertPipeline` deduplication and rate limiting
- Unit test: `ActionLogger` circular buffer overflow behavior
- Manual: Deploy on test server, trigger `/lagxpert reload`, verify no NPEs

**Phase 1:**
- Manual: Create mob overload → verify AutoChunkScan removes mobs AND logs action
- Manual: Simulate TPS drop → verify EmergencyController escalates and ALL integrated systems respond
- Manual: Fill chunk with entities → verify EntityListener blocks spawn AND triggers cleanup

**Phase 2:**
- Manual: Place minecart, leave it 5 min → verify VehicleManager removes it
- Manual: Build redstone clock, wait → verify full circuit breaker (not just 1 wire)
- Manual: Spam riptide → verify cooldown prevents usage

**Phase 3:**
- Unit test: `AdaptiveThresholdEngine` calculation with various inputs
- Manual: Switch between optimization profiles → verify all limits change
- Manual: Run for 24h → verify PerformanceHistory trends are accurate

**Phase 4:**
- Manual: `/lagxpert optimize` → verify all phases execute and report
- Manual: Open GUI → verify Action Dashboard shows recent actions
- Manual: Configure daily report → verify it generates at scheduled time

### Regression Prevention
- Every system that previously worked must continue to work with automation disabled (`auto-correction.enabled: false`)
- All existing config keys must be backward compatible
- `/lagxpert reload` must correctly reload all new configs

---

## Config Migration Map

```
OLD FILE              OLD KEY                        NEW FILE                    NEW KEY
─────────────────────────────────────────────────────────────────────────────────────────
lagshield.yml         (entire file)          →       emergency-controller.yml    (entire file)
alerts.yml            alert-cooldown.*       →       alerts.yml                 cooldowns.*
monitoring.yml        alerts.*               →       alerts.yml                 cooldowns.per-type.*
task.yml              (no auto-action)       →       task.yml                   auto-correction.*
config.yml            modules.lagshield      →       config.yml                 modules.emergency-controller
(no file)             —                      →       profiles.yml               (entire file)
(no file)             —                      →       emergency-controller.yml   responses.*
```

Migration is handled by existing `ConfigMigrator` class. Old configs are backed up before migration.

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| EmergencyController over-escalates | Medium | High — gameplay disruption | Hysteresis, manual override, skip-when-empty |
| Auto-breaking redstone angers players | High | Medium — player complaints | Configurable per-circuit-type, grace periods, item drops |
| Adaptive thresholds too aggressive | Medium | Medium — mobs disappear | 25% floor, player bypass permissions |
| Memory leak in ActionLogger | Low | High — OOM crash | Circular buffer, auto-truncate |
| SmartScheduler pauses critical tasks | Low | High — optimization stops | Watchdog, fallback to fixed intervals |
| Race condition in concurrent state changes | Low | High — undefined behavior | Synchronized blocks, AtomicReference for state |

---

## Success Metrics

After full implementation (v3.0), measure against baseline:

| Metric | Current (v2.6.1) | Target (v3.0) |
|--------|------------------|---------------|
| Detection→Action ratio | 65%/35% | 10%/90% |
| Avg time from lag spike to corrective action | ∞ (manual) | < 30 seconds |
| Actions taken per hour (auto) | ~5 (cleanup tasks only) | ~50+ (proactive + reactive) |
| Alerts requiring admin attention | Most | Only for unresolvable issues |
| Actions logged & auditable | 0% | 100% |
| Config profiles available | 0 | 4 presets + custom |
| Broken/inert systems | 4 (LagShield, Vehicle, Riptide, Redstone) | 0 |
| Avg TPS improvement after auto-optimize | N/A | ≥ 2 TPS within 30s |

---

*Document version: 1.0 | Last updated: 2026-05-25 | Target: LagXpert v3.0*
