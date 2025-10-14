# LagXpert v2.6 - Competitive Edge Roadmap

## Vision
Bring LagXpert on par (or beyond) with competing optimisation suites by introducing proactive AI throttling, dynamic lag shielding and specialised vehicle/explosion limiters while preserving the plugin's modular design and localisation support.

## Planned Modules & Enhancements
- **MobAIOptimizer**
  - Disable or replace costly pathfinders per entity type.
  - Allow per-world and per-mob configuration with graceful fallbacks.
  - Expose configurable messages for state toggles and admin notifications.

- **LagShield**
  - React to TPS/memory spike thresholds.
  - Temporarily adjust configured limits (mobs, redstone, scans) and pause heavy tasks.
  - Broadcast recovery/activation alerts using `messages.yml` entries.

- **ExplosionController**
  - Clamp TNT/creeper/end crystal blast radius and chain reactions.
  - Clean residual drops spawned by chained explosions.
  - Provide detailed logging hooks and bypass permissions.

- **VehicleManager**
  - Optimise tick updates for boats/minecarts.
  - Auto-remove abandoned loot minecarts from mineshafts.
  - Introduce per-world vehicle caps and alerts.

- **AbilityLimiter**
  - Monitor Elytra flight speed and Trident usage.
  - Apply configurable slowdowns/cooldowns during TPS stress.
  - Support bypass permissions and player feedback via action bar/chat.

- **ConsoleFilter**
  - YAML-defined rules for suppressing or highlighting console lines.
  - Optional forwarding to in-game staff via messages.

## Infrastructure Updates
- Extend `messages.yml`, `config.yml` and per-module YAMLs with fully translatable strings.
- Add metrics counters for each new module (usage, interventions, time spent active).
- Update GUI and API surfaces to expose new toggles and status indicators.

## Migration Notes
- Preserve backward compatibility; default behaviour mirrors v2.5 until features are enabled.
- Provide automated config migration with backups for existing installs.

## QA Checklist
- Unit/perf tests (where feasible) for pathfinder replacement and lag shield transitions.
- In-game scenarios covering high-entity farms, TNT cannons, Elytra highways and vehicle spam.
- Console filtering regression suite to ensure critical errors are never suppressed by default.

