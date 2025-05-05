# 🔧 LagXpert Free

**LagXpert Free** is a modular and performance-oriented plugin designed to monitor and mitigate lag sources in Minecraft servers, with a focus on **chunk-based optimization** and **player education**.

![Version](https://img.shields.io/badge/version-1.0.0-blue)
![bStats](https://img.shields.io/badge/bStats-enabled-brightgreen)

---

## 📦 Features

- ✅ Chunk-level inspections
- ✅ Hopper, chest, and mob limits per chunk
- ✅ Redstone control system to prevent laggy loops
- ✅ Item cleaner with warning & recovery (Abyss system)
- ✅ Modular YAML configuration for each system
- ✅ Customizable messages in `messages.yml`
- ✅ Permissions for admin and player bypass
- ✅ Automatic scan task (configurable interval)
- ✅ bStats integration
- ✅ Public API: `ChunkOverloadEvent`

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
Join our Discord: https://discord.gg/YOURINVITE

## 📃 License
Free to use and modify, attribution required.

## 🧪 Want More?
- Check out LagXpert Pro for:

- Advanced analytics

- Per-entity limit control

- Redstone loop diagnostics

- Log exporting and admin dashboards


---

