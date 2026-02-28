# LagXpert v2.6.1 - Hotfix

## 🐛 Bug Fixes

### Fixed: Limit messages showing raw YAML objects
Players were seeing `MemorySection[path='limits.hopper', root='YamlConfiguration']` instead of actual alert messages when placing blocks near or at chunk limits.

**What changed:**
- `messages.yml` now uses a structured format with `full` and `short` sub-keys per message.
- ActionBar restriction messages now properly use the short variant.

**Action required:** Delete your existing `plugins/LagXpert/messages.yml` and restart the server to regenerate it, or manually update the `limits:` section to use the new `full`/`short` structure.

### Fixed: `IllegalArgumentException: Specified map is empty` in AsyncChunkAnalyzer
Server log was spamming this error when analyzing chunks with no tile entities (empty chunks, deserts, oceans, etc.).

**Action required:** None. Just update the JAR.

### Fixed: `Missing message: lagshield.activated` in console
LagShield was looking for messages at the wrong path in `messages.yml`.

**Action required:** None. Just update the JAR.

## ✨ Improvements

### Translatable block and entity names in statistics
All block and entity names shown in `/chunkstatus`, `/lagxpert inspect`, chunk scan alerts, and near-limit warnings are now fully customizable from `messages.yml`.

A new `translations:` section lets you rename items like "Hoppers", "Chests", "Mobs", etc. to match your server's language. The `/chunkstatus` display is also fully customizable with individual line formats.

**Action required:** Delete your existing `plugins/LagXpert/messages.yml` and restart the server to regenerate it with the new `translations:` and `chunkstatus:` sections. Then edit the names to your language.

### Fixed: Chests and Trapped Chests duplicated in scan statistics
Chests and Trapped Chests were showing as two separate entries in chunk overload alerts, even though they share the same limit. They are now combined into a single "Chests" entry. Same fix applied to Pistons/Sticky Pistons.

### Fixed: Entity cleanup message showing raw color codes
The entity cleanup broadcast message (`&a`, `&e`, etc.) was not being translated to actual colors, showing raw codes like `&e18&a` in chat.

## 📦 Upgrade
1. Replace `LagXpert-2.6.jar` with `LagXpert-2.6.1.jar`.
2. Delete `plugins/LagXpert/messages.yml` (will regenerate on startup with new translation options).
3. Restart the server.
4. Edit the `translations:` section in `messages.yml` to your language.
