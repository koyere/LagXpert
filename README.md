# 🔧 LagXpert Free v2.5

**LagXpert Free** is an advanced lag prevention plugin with **multi-platform support** (Folia/Paper/Spigot/Bukkit), **Bedrock compatibility**, and **intelligent optimization systems**. Designed to actively prevent and optimize lag sources rather than just detect them.

![Version](https://img.shields.io/badge/version-2.5-blue)
![Platforms](https://img.shields.io/badge/platforms-Folia%20%7C%20Paper%20%7C%20Spigot%20%7C%20Bukkit-green)
![Bedrock](https://img.shields.io/badge/bedrock-compatible-orange)
![bStats](https://img.shields.io/badge/bStats-enabled-brightgreen)

---

## 📦 Features

### 🚀 **NEW in v2.5: Smarter World-Aware Control**
- ✅ **Per-world limits** - Mob and storage limits respect world-specific overrides automatically
- ✅ **Configurable grace periods** - Item cleaner broken-block tracking fully driven by YAML, including material overrides
- ✅ **Chunk safeguards** - Important block list and activity tracking configurable without restarts
- ✅ **Performance-first design** - Zero lag added, maximum optimization gained

### 🎯 **Core Features**
- ✅ Chunk-level inspections with real-time optimization
- ✅ Hopper, chest, and mob limits per chunk with automatic enforcement
- ✅ Redstone control system to prevent laggy loops
- ✅ Item cleaner with warning & recovery (Abyss system)
- ✅ Modular YAML configuration for each system
- ✅ Cross-platform GUI optimization (Java & Bedrock)
- ✅ Customizable messages with platform detection
- ✅ Permissions for admin and player bypass
- ✅ Automatic scan task with intelligent scheduling
- ✅ bStats integration with enhanced metrics
- ✅ Public API: `ChunkOverloadEvent` and smart management hooks

---

## 🧩 Modules

| Module        | Config File      | Description                            |
|---------------|------------------|----------------------------------------|
| Mobs          | `mobs.yml`       | Limits total living entities per chunk |
| Storage       | `storage.yml`    | Limits hoppers, chests, furnaces       |
| Redstone      | `redstone.yml`   | Detects overactive redstone components |
| Alerts        | `alerts.yml`     | Enables player notifications           |
| Task Scanner  | `task.yml`       | Periodically checks loaded chunks      |
| ItemCleaner   | `itemcleaner.yml`| Clears ground items & warns players    |

---

## 📜 Commands

| Command             | Description                                      | Permission               |
|---------------------|--------------------------------------------------|--------------------------|
| `/lagxpert`         | Main command with help, reload, inspect info     | `lagxpert.use` / `admin` |
| `/chunkstatus`      | Shows current chunk usage                        | `lagxpert.use`           |
| `/abyss`            | Recovers recently cleared items                  | `lagxpert.abyss`         |

---

## 🔐 Permissions

- `lagxpert.use`
- `lagxpert.admin`
- `lagxpert.abyss`
- `lagxpert.bypass.*` (per system: mobs, hoppers, redstone, etc.)

---

## 💾 Installation

1. Drop the compiled JAR into your `plugins/` folder.
2. Start your server once to generate configs.
3. Adjust the `.yml` files to suit your limits.
4. Use `/lagxpert reload` to apply changes.

---

## 💡 Notes

- No external dependencies required.
- Compatible with **Spigot, Paper, Purpur** (1.16+).
- Fully offline support — no metrics are mandatory.

---

## 📊 Metrics

This plugin uses [bStats](https://bstats.org/) to collect anonymous usage statistics. Plugin ID: `25746`.

---

## 🛠 Developer API

### Event:
```java
ChunkOverloadEvent event;
```
Let other plugins react to performance overloads detected by LagXpert.

## 📬 Support
Join our Discord: https://discord.gg/xKUjn3EJzR

## 📃 License
Free to use and modify, attribution required.

## 🧪 Want More?
- Check out LagXpert Pro for:

- Advanced analytics

- Per-entity limit control

- Redstone loop diagnostics

- Log exporting and admin dashboards


---

