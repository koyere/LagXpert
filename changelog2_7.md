# LagXpert v2.7 — Changelog

## Major Release: From Monitor to Autonomous Optimizer

### Phase 0 — Safety Net (Foundation)
- **EmergencyController**: New graduated state machine replacing inert LagShield. States: NORMAL → WARNING → CRITICAL → EMERGENCY with hysteresis and per-state configurable responses. Dynamically adjusts: mob caps, natural spawns, item cleanup, chunk unloading, preloader, redstone clocks, AI distance, emergency commands.
- **AlertPipeline**: Unified alert entry point replacing 3 separate cooldown systems. Global burst protection (configurable max/sec), per-player deduplication, console + player delivery, CRITICAL/EMERGENCY alerts bypass cooldowns.
- **ActionLogger**: Circular buffer audit trail (configurable up to 10K entries). Records every corrective action: mob removals, entity cleanups, item clears, spawn blocks, placement blocks, redstone cuts, explosion limits, state transitions, chunk unloads. Queryable via `/lagxpert status`.
- **ThrottledAction**: Safety interface pattern: `canAct() → execute(maxOps) → verify(result) → fallback(result)`. Template for all corrective systems.
- **LagShield**: Deprecated — now a passthrough to EmergencyController for backward compatibility.
- **emergency-controller.yml**: New config file with thresholds, stability settings, per-state graduated responses.

### Phase 1 — Close the Loop
- **AutoChunkScanTask**: No longer just alerts — delegates mob overloads to SmartMobManager for immediate cleanup. Logs corrective actions.
- **EntityListener**: Dynamic mob limits via EmergencyController multiplier. Triggers SmartMobManager on spawn block. Logs spawn blocks to ActionLogger.
- **SmartMobManager**: Uses EmergencyController dynamic limits. Logs all removals with trigger source (auto/emergency).
- **EntityCleanupTask**: Logs cleanup cycles to ActionLogger with trigger source.
- **ItemCleanerTask**: Logs cleanup cycles to ActionLogger.
- **ChunkPreloader**: Auto-pauses during WARNING/CRITICAL/EMERGENCY states to reduce I/O.
- **InactiveChunkUnloader**: Logs unloaded chunks to ActionLogger. ChunkManager uses EmergencyController aggressive inactivity threshold (2min/1min vs 15min normal).
- **MobAIOptimizer**: Dynamic distance threshold from EmergencyController (64→48→32→16 blocks per state).
- **RedstoneCircuitTracker**: Disables all clocks immediately during emergency states. Logs cuts to ActionLogger.
- **StorageListener**: Logs blocked placements to ActionLogger.
- **ExplosionController**: Logs limited explosions to ActionLogger.

### Phase 2 — Bug Fixes & Completion
- **VehicleManager**: Full rewrite — `runCleanupTask()` now actually removes abandoned minecarts/boats (was empty). Added vehicle interaction tracking (enter/exit events). Configurable timeout, max removals per cycle, player-nearby protection radius. Logs to ActionLogger.
- **RedstoneCircuitTracker**: Flood-fill BFS circuit breaker — finds ALL connected redstone components. 3-pass break strategy: wires/torches → repeaters/comparators → any component. Safety caps (max circuit size, max breaks). Falls back to single-block break on failure.
- **AbilityLimiter**: Riptide cooldown now works — uses velocity cancellation (PlayerRiptideEvent not cancellable). Added ProjectileLaunchEvent hook to block riptide tridents before launch. Elytra uses velocity magnitude instead of raw delta. Configurable slowdown/reversal factors.
- **ConsoleFilter**: Thread-safe rewrite — CopyOnWriteArrayList for patterns, AtomicBoolean for state, chain of responsibility (preserves existing filter), async forwarding via Bukkit scheduler. Hot-reload patterns without removing filter.
- **Config Audit**: 39 hardcoded values eliminated. Every threshold, multiplier, limit, and timeout now controlled via YAML. Config key mismatches in vehicles.yml fixed.

### Phase 3 — Proactive Intelligence
- **AdaptiveThresholdEngine**: Dynamic limit multipliers for ALL categories (mobs, storage, entities, redstone). Sensitivity configurable per category. Factors TPS + memory health.
- **PerformanceHistory**: 7-day circular buffer (2016 snapshots, 5-min intervals). CSV persistence to disk. Trend analysis: average TPS by hour, peak player count, peak lag hour, entity growth trends (hourly change, 24h projection, direction).
- **SmartScheduler**: Adaptive task scheduling. 5 priority levels (LOW/NORMAL/HIGH/CRITICAL/EMERGENCY). Auto-adjusts intervals: LOW pauses at WARNING+, NORMAL slows at WARNING, CRITICAL accelerates at EMERGENCY (4x), EMERGENCY always runs.
- **profiles.yml**: 4 optimization presets — relaxed (small servers), balanced (default), aggressive (>50 players), performance (maximum). Auto-reverts after 10min if admin disconnects. Configurable safety settings.
- **config.yml**: New sections: `adaptive-thresholds` (enabled toggle + per-category sensitivity), `performance-history` (enabled + snapshot interval + max days), `action-logger` (max entries).

### Phase 4 — Administration UX
- `/lagxpert optimize`: Full 5-phase optimization pass (Mob Removal → Entity Cleanup → Item Cleanup → Chunk Unload → Cache Clear). Shows before/after: TPS, memory, chunks, entities. Logs manual optimization to ActionLogger.
- `/lagxpert status`: Real-time dashboard. Shows server state, TPS, memory, adaptive multipliers, last 5 actions from audit trail, performance trends (peak players, peak lag hour, entity trend).
- `/lagxpert emergency [status|force-normal]`: Emergency controls. Status shows full EmergencyController state (time in state, multipliers, AI distance, preloader status, redstone status). Force-normal is the admin panic button.
- Help menu updated with new commands + tab completion.
- `EntityCleanupTask.runImmediate()`: Added static method for manual optimization triggering.

### Config Files (8 modified/new)
| File | Status |
|------|--------|
| `config.yml` | New sections: `action-logger`, `adaptive-thresholds`, `performance-history` |
| `emergency-controller.yml` | **NEW** |
| `redstone.yml` | Complete rewrite — 18 new config keys for circuit tracking |
| `vehicles.yml` | 6 new config keys, key mismatches fixed |
| `abilities.yml` | 2 new config keys (slowdown/reversal factors) |
| `alerts.yml` | New section: `global-rate-limit` |
| `profiles.yml` | **NEW** |
| `messages.yml` | 9 new i18n keys for EmergencyController + commands |

### File Count
- **Before**: 52 Java source files, 17 YAML configs
- **After**: 60 Java source files, 18 YAML configs (+8 classes, +1 config)
