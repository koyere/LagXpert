# LagXpert v2.7 — Autonomous Optimizer & Lag Diagnostics

This release does two things: it makes the automatic optimization actually run, and it adds a diagnostics system that tells you *why* your server is lagging and *where*.

The honest framing matters here. Parts of v2.6 shipped configuration that was read, displayed in commands, and then never acted upon. This release closes those gaps and adds verification so they cannot silently reopen.

## 🔍 Lag Diagnostics (new)

The most common admin question is not "what is my TPS" — it is "which build is doing this to me". v2.7 answers that directly.

`/lagxpert diagnose` scans every loaded chunk and produces a ranked report:

- **Scoring is relative to limits, not raw counts.** A chunk with 40 mobs where the limit is 200 is fine. A chunk with 12 hoppers where the limit is 8 is a problem. Ranking by raw count surfaces the wrong chunks, so every metric contributes its percentage of the limit that actually applies to that world.
- **Intervention history counts.** A chunk LagXpert has had to clean up twenty times in the last hour ranks above one that is merely full right now.
- **Limits come from the live adaptive engine**, so the report reflects what is being enforced at that moment, matching what players experience.
- **Plain-language conclusions**, not just numbers. The report tells you whether load is concentrated in a few chunks or spread everywhere, which violation type dominates, whether memory pressure is the real cause, which world is carrying the load, and — importantly — when the cause is *outside* LagXpert's scope so you stop looking in the wrong place.

Scanning is two-phase: chunk snapshots are gathered on the owning thread, then all scoring happens asynchronously. Diagnosing lag must not cause lag. Reports are cached for 30 seconds so a command and a GUI opened together share one scan, and concurrent scans are rejected rather than queued.

### Diagnostics GUI

`/lagxpert diagnose` in-game opens an interactive interface with five screens:

- **Overview** — server state, TPS across three windows, memory, entity totals per world, the plain-language diagnosis, and the limits currently in force.
- **Hotspots** — paginated ranked chunk list, colour-coded by severity, each showing which metrics are over limit.
- **Chunk detail** — every measured contributor for one chunk, plus a teleport button to go look at it.
- **Corrective actions** — the audit trail, with timestamps, triggers and counts.
- **Trends** — worst hour of the day, peak players, entity growth direction and 24h projection.

### Bedrock compatibility

This was built Bedrock-first rather than retrofitted:

- Screen sizes come from `BedrockPlayerUtils.getSafeInventorySize`, and **every slot position is computed from the actual size**. Nothing is placed in a slot a Bedrock client will never render.
- Materials that Geyser translates badly (spawn eggs, command blocks, structure blocks, knowledge books) are substituted automatically.
- Lore is capped at 10 lines, past which Bedrock truncates tooltips unpredictably.
- Legacy `&` colour codes only.
- **If the inventory cannot be opened at all, the full report is sent as chat text.** A Bedrock admin is never left without a diagnosis.

The Bedrock rules now live in one shared helper (`BedrockUI`) that both the config GUI and the diagnostics GUI route through, so they cannot drift apart. The previous `BedrockCompatibleGUI` class — 574 lines that registered no listener and had no callers — has been removed rather than left as a maintenance trap.

## 🚨 Emergency Controller

A four-level state machine (NORMAL → WARNING → CRITICAL → EMERGENCY) with hysteresis: 3 consecutive bad readings to escalate, 5 good to de-escalate, minimum 10 seconds per state.

**The EMERGENCY tier is now reachable.** In the pre-release code `computeTargetState()` could only ever return NORMAL, WARNING or CRITICAL, which made the entire `responses.emergency` block — the 25% mob cap, the 16-block AI distance, the 1-minute unload threshold, and the custom command list — permanently dead. It now escalates via dedicated `thresholds.tps.emergency` / `thresholds.ram.emergency` values, and also on sustained CRITICAL: a server pinned at 12 TPS for two minutes is an emergency in practice even if it never crosses the raw threshold.

Threshold ordering is validated at startup. Misconfiguring the tiers so one becomes unreachable now logs a warning instead of failing silently.

### Responses that now actually execute

| Response | Status |
|---|---|
| Mob cap multiplier (75% / 50% / 25%) | working |
| Chunk preloader pause | working |
| Aggressive chunk unloading | working |
| AI distance reduction | working |
| Redstone clock shutdown | working |
| **Block natural spawns** | **now implemented** |
| **Forced item cleanup** | **now implemented** |
| **Server-wide AI freeze** | **now implemented** |
| **Emergency commands** | **now reachable** |

The last four were configurable and reported by `/lagxpert emergency` but had no code acting on them. A new response coordinator consumes them:

- **Natural spawn blocking** filters by `SpawnReason`. Only environmental pressure is suppressed — spawn eggs, breeding, plugin spawns and commands keep working, because blocking those makes a server look broken rather than busy. The list is operator-configurable, and players with `lagxpert.bypass.mobs` are exempt.
- **Forced cleanup** runs off-cycle with a CAS latch and a cooldown, so a flapping state cannot stack cleanup sweeps.
- **AI freeze** dispatches per chunk (correct under Folia), skips players and NPC-metadata entities, and on recovery respects your `mobs.yml` AI rules instead of blanket re-enabling everything. A shutdown mid-emergency lifts the freeze so mobs are never left permanently frozen.

## 📊 Adaptive Thresholds

Previously this computed multipliers correctly and **nothing consumed them** — turning the feature on or off only changed numbers in `/lagxpert status`. It is now the single authority for "what is the limit right now", and is wired into:

- `StorageListener` — hopper, chest, furnace, barrel, dropper, dispenser, shulker, TNT, piston and observer placement limits
- `EntityListener` and `SmartMobManager` — mob caps
- `RedstoneCircuitTracker` — pulse tolerance and maximum circuit duration
- `AutoChunkScanTask` — so a chunk flagged as overloaded matches what players are actually restricted to
- `EntityCleanupTask` — the per-chunk entity ceiling

Two independent inputs are combined: continuous server health (from TPS and memory) and the discrete emergency state. **The more restrictive of the two wins; they are never multiplied**, which would compound into unplayable values. Limits are only ever scaled *down* — your configured value is always the ceiling — and a configurable `minimum-multiplier` floor prevents collapse. Per-player limits granted through `lagxpert.limits.*` permissions are honored verbatim and never scaled.

The health factor now takes the **worse** of the TPS and memory components rather than averaging them, so healthy memory can no longer mask a genuinely bad TPS reading.

## 📋 Optimization Profiles

`profiles.yml` previously shipped with **zero Java code behind it**: no reader, no `profile` subcommand. `/lagxpert profile balanced` returned "unknown subcommand".

Now implemented:

```
/lagxpert profile list       show profiles and which is active
/lagxpert profile <name>     apply a profile
/lagxpert profile revert     restore the pre-profile configuration
```

Applying a profile writes into the real config files, saves them, and reloads every subsystem. Before the first write the replaced values are snapshotted, so `revert` restores **your** configuration rather than an assumed default — and applying a second profile over a first still reverts all the way back. `safety.auto-revert-minutes` guards against applying an aggressive profile during an incident and forgetting.

Profile keys that do not map to a real setting are reported in the console instead of being silently dropped. The unmappable `redstone.disable-clocks` key has been replaced with keys that exist.

## 🔧 Other fixes

- **`/lagxpert reload` now reloads everything.** It previously refreshed only `ConfigManager` and `AbyssManager`; a dozen subsystems read their own YAML at construction and kept startup values until a full restart. All are now covered, with per-subsystem failure isolation so one bad section cannot abort the reload.
- **`entity-cleanup.advanced.max-entities-per-chunk` is now enforced.** It was read into a field and never used. A new pass trims chunks over the ceiling, respecting every existing protection rule (named, tamed, leashed, ridden, persistent, plugin-created, villagers with trades).
- **`MobAIOptimizer` honors its own config again.** It was reading the emergency controller's getter, which returns a hardcoded 64 when the controller is disabled — so your `mobs.yml` distance was ignored entirely. The controller may now only *tighten* the configured radius, never widen it.
- **`plugin.yml` corrected.** `folia-supported: true` added (without it Folia refuses to load the plugin regardless of the code). Geyser and Floodgate declared as soft dependencies so Bedrock detection works on first join rather than after a reconnect. The `lagxpert.admin.*` permission nodes are now declared — previously they were checked but undeclared, so an admin holding only `lagxpert.admin` saw commands advertised in help and then got "no permission".
- **Permission checks unified.** Help output, tab completion and command execution now consult one method, ending the mismatch between what was advertised and what could be run.
- **`/lagxpert optimize` reports honestly.** Chunk unloading now runs inline and returns a real count instead of dispatching asynchronously and always reporting zero. Before/after values are coloured by whether they actually improved rather than always green. The hardcoded cache-clear count of 1 is gone, and TPS is labelled as a rolling average instead of showing a meaningless same-tick delta.
- **`/lagxpert status` is fully translatable.** Its output was hardcoded English; it now uses `messages.yml` like the rest of the plugin, and additionally shows which emergency responses are in force and which profile is active.
- **Performance history honors its config.** `snapshot-interval-seconds` and `max-history-days` were documented but ignored in favour of hardcoded constants. Saved history is also loaded during startup now, rather than reappearing only after the first interval elapsed.
- **Bedrock player cache no longer leaks.** Entries are evicted on disconnect. Detection also identifies Floodgate UUIDs structurally instead of by string prefix, and re-initialises if Geyser or Floodgate finished loading after LagXpert.
- **`LagShield` removed.** It was a deprecated passthrough with no callers; `lagshield.yml` was shipped in the JAR but never written or read. Both are gone, along with the legacy message keys.

## ⚙️ Configuration

New in `emergency-controller.yml`:

```yaml
thresholds:
  tps:
    emergency: 10.0    # new tier
    critical: 15.0
    warning: 18.0
    recovery: 19.0

stability:
  # Escalate to EMERGENCY after this long stuck in CRITICAL, even if the
  # raw metrics never cross the emergency thresholds. 0 disables.
  sustained-critical-escalate-seconds: 120

actions:
  forced-item-cleanup:
    enabled: true
    cooldown-seconds: 60      # stops a flapping state stacking cleanups
  block-natural-spawns:
    spawn-reasons: [NATURAL, SPAWNER, CHUNK_GEN, ...]
  ai-freeze:
    enabled: true
```

New in `config.yml`:

```yaml
adaptive-thresholds:
  enabled: true
  sensitivity:
    mobs: 1.0
    storage: 0.7      # don't disrupt player builds
    entities: 1.0
    redstone: 1.0
  # Limits can never drop below this fraction of your configured value
  minimum-multiplier: 0.25
  health:
    healthy-tps: 20.0
    degraded-tps: 10.0
    healthy-memory-percent: 50.0
    degraded-memory-percent: 95.0
```

## 📦 Commands & Permissions

| Command | Permission | Purpose |
|---|---|---|
| `/lagxpert diagnose [chat\|refresh]` | `lagxpert.admin.diagnostics` | Find what is causing lag and where |
| `/lagxpert status` | `lagxpert.admin.status` | Live dashboard |
| `/lagxpert optimize` | `lagxpert.admin.optimize` | Full optimization pass |
| `/lagxpert emergency [status\|force-normal]` | `lagxpert.admin.emergency` | Emergency state control |
| `/lagxpert profile [list\|<name>\|revert]` | `lagxpert.admin.profile` | Apply optimization profiles |
| `/lagxpertgui diagnostics` | `lagxpert.admin.diagnostics` | Diagnostics GUI directly |

`lagxpert.admin` implies all of the above. The finer nodes can be granted individually — useful for giving a moderator read-only diagnostics without the ability to rewrite configs or remove entities. Teleporting to a hotspot requires `lagxpert.admin.diagnostics.teleport`.

## Installation

### Fresh install
1. Drop `LagXpert-2.7.jar` into `plugins/`.
2. Start the server to generate configuration files.
3. Run `/lagxpert diagnose` to see your server's current state.

### Upgrade from v2.6.x
1. Stop the server.
2. Replace the old jar with `LagXpert-2.7.jar`.
3. **Restart** to generate `emergency-controller.yml` and `profiles.yml`.
4. Your existing configs remain compatible. New keys use safe defaults.
5. `lagshield.yml` is no longer used and can be deleted.

After upgrading, `/lagxpert reload` applies config edits without a restart — including `emergency-controller.yml`, which previously required one.

### Compatibility
- **Minecraft**: 1.16.x – 1.21.x
- **Platforms**: Spigot, Paper, Purpur, Folia
- **Java**: 11 or higher
- **Bedrock**: Geyser & Floodgate

## A note on verification

Because the recurring problem in v2.6 was configuration that looked live but was not, this release was checked against that specific failure mode:

- Every previously-dead method was confirmed to have real production call sites.
- All 21 shipped YAML files were parsed to confirm they load.
- All 21 profile key mappings were confirmed to resolve to real config paths.
- The emergency state machine was exercised to confirm all four states are reachable from realistic metrics via both the TPS and memory paths.
- All 84 message keys referenced in code were confirmed to exist in `messages.yml`.

### Per-world overrides that were silently ignored

- **`max-entities-per-chunk`** had no world-aware resolution, so the nether template's `150` was discarded in favour of the global `200`.
- **`tnt-per-chunk`, `pistons-per-chunk`, `observers-per-chunk`** had resolvers in `WorldConfigManager` that nothing ever called: `ConfigManager` exposed no `World`-argument delegate for them, and both the placement listener and the chunk scanner used the global value.

All four now resolve per world in the placement listener, the chunk scanner, the entity cleanup pass and the diagnostics engine. All 18 world-aware paths were confirmed to have a valid global fallback, and the shipped nether and end templates each apply 6 active overrides.

The nether template also shipped `tnt-per-chunk: 0` commented as "No TNT in nether by default", but `0` means *no limit* throughout the plugin. Once per-world TNT resolution started working, that would have granted the nether unlimited TNT while claiming the opposite. Corrected to `1` in all three places the template is generated, with the `0` semantics now documented.

### Folia correctness of cleanup sweeps

Folia gives each region its own ticking thread, and an entity may only be touched by the thread that owns its chunk. No thread is permitted to sweep an entire world. The cleanup tasks did exactly that, via `world.getEntities()` and `world.getEntitiesByClass()`.

A new `RegionizedSweeper` utility enumerates loaded chunks and performs the work on the owning thread. Converted to it:

- the item cleaner (scheduled, manual, per-world and emergency-forced)
- entity cleanup, including the per-chunk entity ceiling pass
- vehicle cleanup
- entity counting for performance snapshots and the optimize report

On Spigot and Paper the same dispatch processes chunks in batches across ticks, turning one stalling tick into several short ones — a genuine improvement, since these sweeps run precisely when the server is already struggling. Per-cycle removal budgets are shared across the whole sweep via atomic counters.

Because sweeps now complete asynchronously, `/clearitems` and `/lagxpert optimize` report their totals on completion. The optimize phases are chained so each begins only after the previous one finishes, which keeps counts attributable to the right phase and prevents three concurrent sweeps competing for the same budgets.

Verified: no whole-world entity access remains outside comments, and all four entity removal sites sit inside chunk-scoped, sweeper-dispatched methods.

### Known limitations
- There is no automated test suite. The verification above was performed against the built artifact, not as regression tests.
- `/lagxpert optimize` reports TPS as a rolling average rather than a before/after delta, because the average cannot move within the span of the command.
