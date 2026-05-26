# LagXpert Free v2.7 — Autonomous Optimizer

**LagXpert** is an advanced Minecraft server performance optimization plugin with multi-platform support (Folia/Paper/Spigot/Bukkit), Bedrock compatibility, and **closed-loop autonomous optimization**. Detects, analyzes, acts, verifies, and reports — all automatically.

![Version](https://img.shields.io/badge/version-2.7-blue)
![Platforms](https://img.shields.io/badge/platforms-Folia%20%7C%20Paper%20%7C%20Spigot%20%7C%20Bukkit-green)
![Java](https://img.shields.io/badge/java-11%2B-red)
![Bedrock](https://img.shields.io/badge/bedrock-compatible-orange)

---

## What's New in v2.7

### Autonomous Optimization Engine
- **Emergency Controller**: Graduated state machine (NORMAL → WARNING → CRITICAL → EMERGENCY) with per-state configurable responses. Dynamically adjusts mob caps, AI distance, chunk unloading, redstone control, and more based on real-time TPS/memory.
- **Closed-Loop Actions**: AutoChunkScan no longer just alerts — it triggers SmartMobManager to remove excess mobs immediately. All systems feed corrective actions, not just notifications.
- **Action Audit Trail**: Every corrective action logged (mob removals, entity cleanups, item clears, chunk unloads, spawn blocks, redstone cuts, explosion limits) — queryable via `/lagxpert status`.

### Adaptive Intelligence
- **Adaptive Threshold Engine**: All limits (mobs, storage, entities, redstone) scale dynamically with server health. When TPS drops, limits tighten proportionally.
- **Performance History**: 7-day circular buffer of 5-min snapshots with trend analysis — peak prediction, entity growth trends, lag hour detection. Persisted to disk.
- **Smart Scheduler**: Tasks auto-adjust intervals based on server state. Low-priority tasks pause during WARNING. Emergency tasks run 4x faster during CRISIS.
- **Optimization Profiles**: 4 presets (relaxed/balanced/aggressive/performance) switchable via `/lagxpert profile`.

### Complete & Fixed
- **VehicleManager**: Actually removes abandoned minecarts/boats now (was empty). Tracks vehicle interactions, configurable timeout.
- **Redstone Circuit Breaker**: Flood-fill BFS that breaks ALL connected components, not just 1 wire. 3-pass strategy: wires → repeaters → any component.
- **AbilityLimiter**: Working Riptide cooldown via velocity cancellation + ProjectileLaunchEvent hook. Elytra uses velocity magnitude.
- **ConsoleFilter**: Thread-safe (CopyOnWriteArrayList), chain of responsibility, async forwarding.
- **39 hardcoded values eliminated**: Everything is now configurable via YAML.

### New Commands
- `/lagxpert optimize` — Full 5-phase optimization with before/after metrics
- `/lagxpert status` — Dashboard: state, TPS, memory, adaptive multipliers, recent actions, trends
- `/lagxpert emergency [status|force-normal]` — Emergency controls with panic button

---

## Features

### Autonomous Optimization
- Emergency Controller with graduated state responses
- Adaptive thresholds for all limit types
- Smart scheduling with priority-based interval adjustment
- Closed-loop detection → action → verification
- Full audit trail of all corrective actions

### Core Features
- Chunk-level inspections with real-time optimization
- Hopper, chest, TNT, piston, observer, and mob limits per chunk
- Redstone circuit tracking with flood-fill circuit breaker
- Item cleaner with warning & Abyss recovery system
- Entity cleanup (invalid, duplicate, abandoned, out-of-bounds)
- Smart mob removal with entity protection (named, tamed, equipped)
- Mob AI optimizer (distance-based, per-type)
- Explosion radius control
- Vehicle limits and abandoned vehicle cleanup
- Elytra speed limit + Riptide cooldown
- Console log filter

### Administration
- Interactive GUI configuration (`/lagxpertgui`)
- Performance dashboard (`/lagxpert status`)
- One-click optimization (`/lagxpert optimize`)
- Emergency panic button (`/lagxpert emergency force-normal`)
- Per-world configuration overrides
- Optimization profiles (relaxed/balanced/aggressive/performance)
- bStats metrics integration

---

## Modules & Config Files

| Module | Config File | Description |
|--------|-------------|-------------|
| Mobs | `mobs.yml` | Mob limits, AI optimizer, smart removal |
| Storage | `storage.yml` | Block limits per chunk |
| Redstone | `redstone.yml` | Circuit tracking, flood-fill breaker |
| Alerts | `alerts.yml` | Notification toggles, cooldowns, rate limits |
| Task Scanner | `task.yml` | Auto-chunk scan interval |
| Item Cleaner | `itemcleaner.yml` | Ground item cleanup + Abyss recovery |
| Entity Cleanup | `entitycleanup.yml` | Entity removal targets |
| Monitoring | `monitoring.yml` | TPS, memory, chunk, lag spike thresholds |
| Chunks | `chunks.yml` | Chunk loading/unloading, preloading |
| Emergency Controller | `emergency-controller.yml` | State machine thresholds, graduated responses |
| Vehicles | `vehicles.yml` | Vehicle limits, abandoned cleanup |
| Abilities | `abilities.yml` | Elytra speed, Riptide cooldown |
| Explosions | `explosions.yml` | Explosion radius, chain reaction prevention |
| Console Filter | `console-filter.yml` | Console log regex filters |
| Profiles | `profiles.yml` | Optimization presets |
| Messages | `messages.yml` | All plugin messages (i18n) |

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/lagxpert` | Main command with help, reload, inspect | `lagxpert.use` |
| `/lagxpert optimize` | Full optimization pass with before/after | `lagxpert.admin.optimize` |
| `/lagxpert status` | Performance dashboard | `lagxpert.admin` |
| `/lagxpert emergency` | Emergency controller status + force-normal | `lagxpert.admin.emergency` |
| `/lagxpert reload` | Hot-reload all configurations | `lagxpert.admin` |
| `/lagxpert inspect <x> <z> [world]` | Inspect specific chunk | `lagxpert.admin` |
| `/chunkstatus` | Current chunk usage info | `lagxpert.use` |
| `/tps` | Server performance metrics | `lagxpert.tps` |
| `/abyss` | Recover recently cleared items | `lagxpert.abyss` |
| `/clearitems [all\|world]` | Manual item cleanup | `lagxpert.clearitems` |
| `/lagxpertgui` | Interactive GUI configuration | `lagxpert.gui` |

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `lagxpert.use` | true | Basic command access |
| `lagxpert.admin` | op | Admin commands (reload, inspect, status) |
| `lagxpert.admin.optimize` | op | Run optimization pass |
| `lagxpert.admin.emergency` | op | Emergency controls |
| `lagxpert.gui` | op | GUI access |
| `lagxpert.tps` | true | TPS command |
| `lagxpert.abyss` | true | Item recovery |
| `lagxpert.clearitems` | op | Manual cleanup |
| `lagxpert.emergency.notify` | op | Receive emergency broadcasts |
| `lagxpert.bypass.mobs` | false | Bypass mob limits |
| `lagxpert.bypass.redstone` | false | Bypass redstone control |
| `lagxpert.bypass.abilities` | false | Bypass ability limits |
| `lagxpert.bypass.*` | false | Bypass all limits |
| `lagxpert.limits.mobs.<N>` | false | Custom mob limit per player |
| `lagxpert.notifications.mob-removal` | false | Receive mob removal notifications |
| `lagxpert.monitoring.alerts` | false | Receive performance alerts |
| `lagxpert.console.view-filtered` | false | See filtered console messages |

---

## Installation

1. Drop `LagXpert-2.7.jar` into `plugins/`
2. Start server to generate configs
3. Adjust `.yml` files to your needs
4. `/lagxpert reload` to apply changes
5. Use `/lagxpert optimize` for a quick performance boost
6. Check `/lagxpert status` for real-time dashboard

---

## Requirements

- Java 11+
- Minecraft 1.16+ (Spigot/Paper/Purpur/Folia)
- No external dependencies

---

## Developer API

```java
// React to chunk overloads
@EventHandler
public void onChunkOverload(ChunkOverloadEvent event) { ... }

// Query LagXpert state
LagXpertAPI.getMobCount(chunk);
LagXpertAPI.getEffectiveLimit(chunk, Material.HOPPER);
EmergencyController.getInstance().getCurrentState();
ActionLogger.getInstance().getRecent(50);
PerformanceHistory.getInstance().getEntityTrend(6);
```

---

## Support

Discord: https://discord.gg/xKUjn3EJzR
bStats Plugin ID: `25746`
