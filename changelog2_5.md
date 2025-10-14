# LagXpert v2.5 - World-Aware Enforcement & Activity Tracking

## 🚀 Highlights
- **Per-world enforcement**: Mob spawns, storage placement checks and automated scans now honour world-specific overrides, keeping creative hubs relaxed and survival worlds strict.
- **Config-driven grace periods**: The item cleaner reads `broken-block-tracking` (default + material overrides) directly from `itemcleaner.yml`, making temporary drop protection fully customisable.
- **Chunk safeguards from config**: Smart chunk management now loads the important-block list from `chunks.yml`, so mission-critical structures stay loaded without code changes.

## ⚙️ Technical Improvements
- Hooked chunk activity tracking into block/entity/player events and scheduled periodic cleanup to keep telemetry fresh.
- Added Folia/Bukkit-safe activity cleanup task and listener to populate `ChunkManager`’s analytics in real time.
- Introduced world-aware limit providers across listeners, smart mob manager and the auto chunk scanner.
- Implemented configurable alert skipping when no players are online, aligned with `monitoring.yml`.

## 🛠 Developer Notes
- `ConfigManager` exposes per-world getters and broken block tracking settings; `RecentlyBrokenBlocksTracker.configure(...)` keeps runtime state in sync.
- Added `ChunkActivityListener` and `ChunkActivityCleanupTask` to the lifecycle; ensure they remain registered when extending the chunk management module.

