# LagXpert v2.6 - Reactive Optimization & Modular Control

This update introduces advanced modules for proactive performance management, including AI optimization, lag shields, and specific controls for vehicles and explosions.

## 🚀 Highlights
- **MobAIOptimizer**: Intelligent system that disables AI and pathfinding for mobs in loaded chunks to drastically reduce CPU usage.
- **LagShield**: Emergency monitor that automatically activates severe restrictions when TPS drops below 16.0 or RAM exceeds 90%.
- **ExplosionController**: Full control over TNT, Creeper, and Wither explosions. Includes radius limits and prevention of massive chain reactions.
- **VehicleManager**: Configurable per-chunk limits for minecarts and boats, with automatic cleanup of abandoned vehicles.
- **AbilityLimiter & ConsoleFilter**: Speed restrictions for Elytras, cooldowns for Tridents, and Regex-based console spam filtering.

## ⚙️ Configuration Examples
**`explosions.yml`** - Prevent massive TNT chain reactions:
```yaml
settings:
  prevent-chain-reaction: true
  max-primed-tnt-per-chunk: 20
  disable-explosion-drops: false
```

**`vehicles.yml`** - Limit entities per chunk:
```yaml
limits:
  minecarts:
    per-chunk: 8
  boats:
    per-chunk: 5
```

## 🐛 Bug Fixes & Technical Improvements
- **Modular Refactoring**: Clean implementation of new systems (`me.koyere.lagxpert.system.*`) with independent configurations.
- **Compilation Fixes**: Resolved symbol error in `VehicleManager` and cleaned up imports in `AbilityLimiter`.
- **Secure Integration**: Verified build with Maven (`BUILD SUCCESS`) and ensured correct registration of listeners and commands.

## 📦 Installation & Compatibility
### Installation
1.  Stop your server.
2.  Delete any previous `LagXpert-*.jar` versions.
3.  Place the new `LagXpert-2.6.jar` in your `/plugins/` folder.
4.  **Restart the server.** This is required to automatically generate the new configuration files:
    - `mobs.yml`, `lagshield.yml`, `explosions.yml`, `vehicles.yml`, `abilities.yml`, `console-filter.yml`.
5.  *Note: While `/lagxpert reload` exists, a full restart is strongly recommended for this update to ensure all new modules initialize correctly.*

### Compatibility
- **Versions**: Minecraft 1.16.x - 1.21.x
- **Platforms**: Spigot, Paper, Purpur, Folia (Experimental support).
- **Java**: Java 17 or higher recommended.
