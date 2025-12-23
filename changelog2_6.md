# LagXpert v2.6 - Competitive Edge Roadmap

## Vision
Bring LagXpert on par (or beyond) with competing optimisation suites by introducing proactive AI throttling, dynamic lag shielding and specialised vehicle/explosion limiters while preserving the plugin's modular design and localisation support.

## Planned Modules & Enhancements
- **MobAIOptimizer**
  - [COMPLETED] `src/main/resources/mobs.yml`: Agregar sección `ai-optimizer` para configurar desactivación de IA por tipo de entidad y mundo.
  - [COMPLETED] `src/main/java/me/koyere/lagxpert/system/MobAIOptimizer.java`: Implementar lógica para remover IA (`setAI(false)`) y pathfinders.
  - [COMPLETED] `src/main/java/me/koyere/lagxpert/listeners/EntityListener.java`: Integrar hooks en `CreatureSpawnEvent` y `EntityLoadEvent` para aplicar optimizaciones al instante.

- **LagShield**
  - [COMPLETED] `src/main/resources/lagshield.yml`: Configuración de umbrales críticos (TPS < 16, RAM > 90%) y acciones de emergencia (pausar spawns, kill drops).
  - [COMPLETED] `src/main/java/me/koyere/lagxpert/system/LagShield.java`: Sistema de monitoreo activo que se suscribe a `TPSMonitor`.
  - [COMPLETED] `src/main/java/me/koyere/lagxpert/monitoring/TPSMonitor.java`: Disparar eventos de alerta hacia `LagShield` cuando se cruzan los umbrales.
  - [COMPLETED] `src/main/resources/messages.yml`: Agregar mensajes de alerta y recuperación broadcast.

- **ExplosionController**
  - [COMPLETED] `src/main/resources/explosions.yml`: Definir radios máximos para TNT/Creeper y toggle para prevención de reacciones en cadena.
  - [COMPLETED] `src/main/java/me/koyere/lagxpert/system/ExplosionController.java`: Listeners para `EntityExplodeEvent` y `BlockExplodeEvent` que modifican `event.yield` y limpian items (`EntityItem`) generados en masa.

- **VehicleManager**
  - [COMPLETED] `src/main/resources/vehicles.yml`: Límites de vehículos por chunk/mundo y configuración de limpieza de minas abandonadas.
  - [COMPLETED] `src/main/java/me/koyere/lagxpert/system/VehicleManager.java`: Tarea periódica para escanear y eliminar vagonetas sin pasajero en chunks inactivos. Optimización de eventos de movimiento de vehículos.

- **AbilityLimiter**
  - [COMPLETED] `src/main/resources/abilities.yml`: Configuración de velocidad máxima de Elytra y cooldown de Tridentes.
  - [COMPLETED] `src/main/java/me/koyere/lagxpert/system/AbilityLimiter.java`: Monitoreo de `PlayerMoveEvent` para detectar exceso de velocidad en Elytra y corregir (rubberband suave). Monitoreo de `ProjectileLaunchEvent` para tridentes.

- **ConsoleFilter**
  - [COMPLETED] `src/main/resources/console-filter.yml`: Lista de expresiones regulares (Regex) para bloquear mensajes spam.
  - [COMPLETED] `src/main/java/me/koyere/lagxpert/system/ConsoleFilter.java`: Inyectar filtro en `java.util.logging.Logger` raíz del servidor.

## Infrastructure Updates
- [MODIFY] `src/main/java/me/koyere/lagxpert/LagXpert.java`: Registrar nuevos Managers y Listeners en `onEnable`.
- [MODIFY] `src/main/java/me/koyere/lagxpert/utils/ConfigManager.java` & `ConfigMigrator.java`: Soportar carga y migración de los nuevos archivos YAML.
- [MODIFY] `src/main/java/me/koyere/lagxpert/metrics/MetricsHandler.java`: Agregar gráficos bStats para los nuevos módulos.

