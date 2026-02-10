# LagXpert v2.6.1 - Hotfix

## 🐛 Bug Fixes

### Fixed: Limit messages showing raw YAML objects
Players were seeing `MemorySection[path='limits.hopper', root='YamlConfiguration']` instead of actual alert messages when placing blocks near or at chunk limits.

**Cause:** The `.short` action bar variants in `messages.yml` created YAML key conflicts that turned message strings into configuration sections.

**What changed:**
- `messages.yml` now uses a structured format with `full` and `short` sub-keys per message:
  ```yaml
  limits:
    hopper:
      full: "&cHopper limit reached in this chunk!"
      short: "&cHopper limit reached!"
  ```
- `MessageManager` automatically resolves the correct variant — `full` for chat, `short` for action bar.
- ActionBar restriction messages now properly use the short variant.

**Action required:** Delete your existing `plugins/LagXpert/messages.yml` and restart the server to regenerate it, or manually update the `limits:` section to use the new `full`/`short` structure.

### Fixed: `IllegalArgumentException: Specified map is empty` in AsyncChunkAnalyzer
Server log was spamming this error when analyzing chunks with no tile entities (empty chunks, deserts, oceans, etc.).

**Cause:** `EnumMap` copy constructor fails on empty maps. Now handled with a safe fallback.

**Action required:** None. Just update the JAR.

## 📦 Upgrade
1. Replace `LagXpert-2.6.jar` with `LagXpert-2.6.1.jar`.
2. Delete `plugins/LagXpert/messages.yml` (will regenerate on startup).
3. Restart the server.
