# LagXpert Free — Changelog

All notable changes to this project are documented here. Detailed per-release
notes live in the `changelog2_*.md` files.

---

## [2.7] - 2026-09-06

### Lag Diagnostics (new)

- **`/lagxpert diagnose`** — scans every loaded chunk and produces a ranked report
  of what is causing lag and where. Scoring is relative to the limits that apply
  to each chunk, not raw counts, so a chunk with 12 hoppers against a limit of 8
  outranks a chunk with 40 mobs against a limit of 200.
- **Plain-language conclusions** rather than raw statistics: whether load is
  concentrated or spread, which violation type dominates, whether memory pressure
  is the real cause, which world carries the load, and when the cause lies
  outside LagXpert's scope.
- **Intervention history is a ranking signal** — chunks the plugin repeatedly has
  to clean up rank above chunks that are merely full right now.
- **Diagnostics GUI** with five screens: overview, paginated hotspot list,
  per-chunk drill-down with teleport, corrective-action audit trail, and
  historical trends.
- Scanning is two-phase: snapshots on the owning thread, scoring asynchronously.
  Reports are cached for 30 seconds; concurrent scans are rejected, not queued.

### Bedrock compatibility

- New shared `BedrockUI` layer that both the config GUI and the diagnostics GUI
  route through, so compatibility rules cannot drift between screens.
- Screen sizes come from the configured Bedrock safe size, and **every slot
  position is computed from the actual size** so nothing is placed where a
  Bedrock client cannot render it.
- Automatic substitution of materials Geyser translates badly (spawn eggs,
  command blocks, structure blocks, knowledge books, player heads).
- Lore capped at 10 lines; legacy `&` colour codes only.
- Full chat-text fallback if an inventory cannot be opened.
- Bedrock player cache is now evicted on disconnect (previously grew for the
  lifetime of the server).
- Floodgate UUIDs are identified structurally instead of by string prefix, and
  detection re-initialises if Geyser or Floodgate loaded after LagXpert.

### Fixed — configuration that was read but never acted upon

- **The EMERGENCY state was unreachable.** `computeTargetState()` could only
  return NORMAL, WARNING or CRITICAL, making the entire `responses.emergency`
  block dead: the 25% mob cap, 16-block AI distance, 1-minute unload threshold
  and custom command list never applied. Added `thresholds.tps.emergency` and
  `thresholds.ram.emergency`, plus escalation after sustained CRITICAL.
  Threshold ordering is now validated at startup.
- **Adaptive thresholds scaled nothing.** The engine computed multipliers that no
  code consumed; toggling the feature only changed numbers in `/lagxpert status`.
  It is now the single authority for effective limits and is wired into storage
  placement, mob caps, redstone tolerance, the chunk scanner and entity cleanup.
- **`profiles.yml` had no code behind it.** No reader, no `profile` subcommand.
  Now fully implemented with snapshot-based revert and auto-revert safety.
- **`block-natural-spawns`, `force-item-cleanup` and `freeze-all-ai`** were
  configurable and reported by `/lagxpert emergency` but never executed. A new
  response coordinator implements all three.
- **`entity-cleanup.advanced.max-entities-per-chunk`** was read into a field and
  never used. Now enforced, respecting every existing entity protection rule.
- **`MobAIOptimizer` ignored its own config.** It read a getter that returns a
  hardcoded 64 when the emergency controller is disabled, so the configured
  distance was discarded. The controller may now only tighten it, never widen it.
- **`performance-history.snapshot-interval-seconds` and `max-history-days`** were
  documented but overridden by hardcoded constants. Both are now honored, and
  stored history loads during startup instead of after the first interval.

### Fixed — commands and permissions

- **`/lagxpert reload` now reloads every subsystem.** It previously refreshed only
  `ConfigManager` and `AbyssManager`; a dozen subsystems read their own YAML at
  construction and kept startup values until a full restart. Per-subsystem
  failure isolation means one bad section cannot abort the reload.
- **`plugin.yml` corrected.** Added `folia-supported: true`, without which Folia
  refuses to load the plugin regardless of code. Declared Geyser and Floodgate as
  soft dependencies so Bedrock detection works on first join. Declared the
  `lagxpert.admin.*` nodes, which were checked but undeclared.
- **Permission checks unified.** Help output, tab completion and execution now
  consult one method, ending the case where a command was advertised in help and
  then refused on use.
- **`/lagxpert optimize` reports honestly.** Chunk unloading runs inline and
  returns a real count instead of dispatching asynchronously and always reporting
  zero. Before/after values are coloured by whether they improved rather than
  always green. The hardcoded cache-clear count is gone, and TPS is labelled as a
  rolling average instead of showing a meaningless same-tick delta.
- **`/lagxpert status` is fully translatable** and now shows the active profile
  and which emergency responses are in force.

### Removed

- `LagShield` — a deprecated passthrough with no callers.
- `lagshield.yml` — shipped in the JAR but never written or read.
- `BedrockCompatibleGUI` — 574 lines that registered no listener and had no
  callers; its constraints now live in the shared `BedrockUI` helper.
- Legacy `alerts.messages.lagshield.*` message keys.

### New commands

| Command | Purpose |
|---|---|
| `/lagxpert diagnose [chat\|refresh]` | Find what is causing lag and where |
| `/lagxpert profile [list\|<name>\|revert]` | Apply optimization profiles |
| `/lagxpertgui diagnostics` | Open the diagnostics GUI directly |

### Fixed — per-world overrides that were silently ignored

- **`max-entities-per-chunk`** had no world-aware resolution at all, so the
  nether template's `150` was discarded in favour of the global `200`.
- **`tnt-per-chunk`, `pistons-per-chunk` and `observers-per-chunk`** had
  world-aware resolvers in `WorldConfigManager` that nothing ever called, because
  `ConfigManager` exposed no `World`-argument delegate for them and the placement
  listener and chunk scanner both used the global value.

All four now resolve per world in the placement listener, the chunk scanner, the
entity cleanup pass and the diagnostics engine. Confirmed: all 18 world-aware
paths resolve with a valid global fallback, and the shipped nether and end
templates apply 6 active overrides each.

- **Corrected a semantic trap in the nether template.** It shipped
  `tnt-per-chunk: 0` commented as "No TNT in nether by default", but `0` means
  *no limit* throughout the plugin. Once per-world TNT resolution started
  working, that would have granted the nether unlimited TNT while claiming the
  opposite. Now `1`, with the `0` semantics documented in all three places the
  template is generated.

### Fixed — Folia correctness of cleanup sweeps

Folia gives each region its own ticking thread, and an entity may only be touched
by the thread owning its chunk. There is no thread permitted to sweep a whole
world. The cleanup tasks previously did exactly that via `world.getEntities()`
and `world.getEntitiesByClass()`.

New `RegionizedSweeper` utility enumerates loaded chunks and performs the work on
the owning thread. Converted to it:

- item cleaner (scheduled, manual, per-world and emergency-forced)
- entity cleanup, including the per-chunk entity ceiling pass
- vehicle cleanup
- entity counting for performance snapshots and the optimize report

On Spigot and Paper the same dispatch processes chunks in batches across ticks,
turning one stalling tick into several short ones — a real improvement given
these sweeps run when the server is already struggling. Per-cycle removal budgets
are shared across the whole sweep via atomic counters.

Because sweeps now complete asynchronously, `/clearitems` and
`/lagxpert optimize` report totals on completion. The optimize phases are chained
so each begins only after the previous finishes, keeping counts attributable and
preventing three concurrent sweeps from competing for the same budgets.

Verified: no whole-world entity access remains outside comments, and all four
entity removal sites sit inside chunk-scoped, sweeper-dispatched methods.

### Known limitations

- No automated test suite. Verification was performed against the built
  artifact, not as regression tests.
- `/lagxpert optimize` reports TPS as a rolling average rather than a
  before/after delta, because the average cannot move within the span of the
  command.

Full notes: `changelog2_7.md`

---

## [2.6.1] - Hotfix

### Fixed
- Limit messages showed raw YAML objects (`MemorySection[...]`) instead of the
  actual alert text. `messages.yml` now uses a structured `full` / `short` format
  per message.
- `IllegalArgumentException: Specified map is empty` spam from
  `AsyncChunkAnalyzer` when analyzing chunks with no tile entities.
- `Missing message: lagshield.activated` in console — wrong message path.
- Chests and Trapped Chests appeared as two separate entries in scan statistics
  despite sharing a limit. Same fix for Pistons and Sticky Pistons.
- Entity cleanup broadcast showed raw colour codes instead of colours.

### Added
- Translatable block and entity names via a new `translations:` section, used by
  `/chunkstatus`, `/lagxpert inspect`, scan alerts and near-limit warnings.
- Fully customizable `/chunkstatus` display with per-line formats.

**Upgrade note:** delete `plugins/LagXpert/messages.yml` so it regenerates with
the new sections.

Full notes: `changelog2_6_1.md`

---

## [2.6] - Reactive Optimization & Modular Control

### Added
- **MobAIOptimizer** — disables AI and pathfinding for distant mobs to reduce CPU
  usage.
- **LagShield** — emergency monitor triggering restrictions below 16 TPS or above
  90% RAM. *(Replaced by the Emergency Controller in 2.7.)*
- **ExplosionController** — TNT, Creeper and Wither explosion control with radius
  limits and chain-reaction prevention.
- **VehicleManager** — per-chunk minecart and boat limits with abandoned vehicle
  cleanup.
- **AbilityLimiter** — Elytra speed restrictions and Trident riptide cooldowns.
- **ConsoleFilter** — regex-based console spam filtering.

### Changed
- Modular refactor into `me.koyere.lagxpert.system.*` with independent configs.

Full notes: `changelog2_6.md`

---

## [2.5] - World-Aware Enforcement & Activity Tracking

### Added
- Per-world enforcement for mob spawns, storage placement and automated scans,
  so creative hubs and survival worlds can differ.
- Config-driven grace periods for the item cleaner via `broken-block-tracking`.
- Chunk safeguards read the important-block list from `chunks.yml`.
- `ChunkActivityListener` and `ChunkActivityCleanupTask` populate chunk analytics
  from live block, entity and player events.
- Configurable alert skipping when no players are online.

Full notes: `changelog2_5.md`

---

## [2.4] - Action Bar Notifications

### Added
- Restriction messages delivered to the action bar instead of chat, configurable
  via `delivery.restrictions.method` (`chat`, `actionbar`, `both`).
- Per-player custom limits through permissions such as
  `lagxpert.limits.hoppers.25`.
- Alert filtering by permission, preventing accidental disclosure of hidden base
  locations.

### Fixed
- Off-by-one error in block limits: a limit of 10 only permitted 9 blocks.
- Only purple shulker boxes were counted; all 16 colours now count toward the
  shared limit.

Full notes: `changelog2_4.md`

---

## [2.3] - Performance & Reliability

### Added
- Atomic counter system for TNT and block tracking, replacing repeated chunk
  scans with O(1) lookups.
- Recently-broken-block tracking with material-specific grace periods, so items
  from freshly mined valuable ores are not cleaned up.
- Smart alert filtering to reduce spam when no players are online.

### Fixed
- Critical race condition that allowed TNT placement past the limit after several
  attempts.
- Alerts firing every two minutes even with players online.
- Thread safety of block counters and cache operations.

Full notes: `changelog2_3.md`

---

## [2.2] - 2025-01-19

### Added
- **Smart Mob Management** — removes excess mobs while protecting named, tamed,
  equipped and plugin-created entities, with a priority system and gradual
  removal to avoid lag spikes.
- **Platform detection** for Folia, Paper, Spigot and Bukkit, with a
  cross-platform scheduler wrapper and native Folia region scheduling.
- **Bedrock support** — player detection via Geyser/Floodgate, GUI adjustments
  and chat fallbacks.
- Automatic configuration migration from v2.1.x with timestamped backups.
- Platform Detection and Bedrock Player APIs for external plugins.

---

## [1.0.0] - 2025-05-05

### Added
- Modular configuration: `mobs.yml`, `redstone.yml`, `storage.yml` and others.
- Configurable per-chunk limits for mobs, hoppers, chests and furnaces.
- Redstone activity monitor with block cut-off.
- Chunk scan task for automatic detection and alerting.
- Item cleaner with the Abyss recovery system and warnings.
- Full message customization via `messages.yml`.
- Reload command for configurations.
- bStats metrics integration (plugin ID `25746`).
- `ChunkOverloadEvent` API.

### Commands
`/lagxpert`, `/chunkstatus`, `/abyss`

### Permissions
`lagxpert.use`, `lagxpert.admin`, `lagxpert.abyss`,
`lagxpert.bypass.{mobs,hoppers,chests,redstone}`
