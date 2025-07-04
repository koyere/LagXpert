# LagXpert Plugin Documentation

## Table of Contents
1. [Overview](#overview)
2. [Installation and Setup](#installation-and-setup)
3. [Plugin Architecture](#plugin-architecture)
4. [Configuration System](#configuration-system)
5. [Commands and Permissions](#commands-and-permissions)
6. [Core Features](#core-features)
7. [Performance Monitoring](#performance-monitoring)
8. [Best Practices](#best-practices)
9. [API Documentation](#api-documentation)
10. [Troubleshooting](#troubleshooting)

## Overview

LagXpert is a comprehensive Minecraft server performance optimization plugin designed to monitor, analyze, and mitigate lag sources through intelligent chunk-based optimization. Built with a modular architecture, it provides both automated and manual tools for server administrators to maintain optimal server performance.

### Key Features
- **Chunk-based Performance Management**: Intelligent tracking and optimization of chunk activity
- **Multi-Platform Support**: Compatible with Bukkit, Spigot, Paper, Purpur, and Folia
- **Cross-Platform Compatibility**: Bedrock Edition support through Geyser/Floodgate integration
- **Real-time Performance Monitoring**: TPS tracking, lag spike detection, and memory monitoring
- **Smart Management Systems**: Automated mob removal, item cleanup, and chunk management
- **Interactive GUI Configuration**: User-friendly configuration interface
- **Comprehensive API**: External plugin integration capabilities

### System Requirements
- **Java**: 11 or higher
- **Minecraft**: 1.16+ (Spigot API 1.16.5 minimum)
- **Platforms**: Bukkit, Spigot, Paper, Purpur, Folia
- **Memory**: Minimum 512MB allocated to server

## Installation and Setup

### Basic Installation
1. Download the latest LagXpert JAR file
2. Place it in your server's `plugins/` directory
3. Restart your server or use a plugin manager to load it
4. Configure the plugin using the generated configuration files

### First-Time Setup
1. **Initial Configuration**: The plugin will generate default configuration files on first startup
2. **World Setup**: World-specific configurations will be created automatically
3. **Permission Setup**: Configure permissions according to your server's needs
4. **Testing**: Use `/lagxpert help` to verify installation

### Quick Start Commands
```bash
# Check plugin status
/lagxpert help

# View current chunk information
/chunkstatus

# Monitor server performance
/tps

# Open GUI configuration (for operators)
/lagxpertgui
```

## Plugin Architecture

### Modular Design
LagXpert follows a modular singleton architecture with a two-phase initialization system:

- **Phase 0**: Platform detection and core setup
- **Phase 1**: Core systems (cache, async processing, redstone tracking)
- **Phase 2**: Advanced systems (monitoring, chunk management, GUI)

### Core Components

#### System Managers
- **ConfigManager**: Centralized configuration management
- **ChunkManager**: Smart chunk activity tracking
- **WorldConfigManager**: Per-world configuration overrides
- **GUIManager**: Interactive configuration interface

#### Performance Systems
- **TPSMonitor**: Real-time TPS calculation and lag spike detection
- **PerformanceTracker**: Memory monitoring and performance analytics
- **ChunkDataCache**: Performance-optimized chunk data caching
- **AsyncChunkAnalyzer**: Non-blocking chunk analysis

#### Task Systems
- **AutoChunkScanTask**: Periodic chunk analysis
- **EntityCleanupTask**: Automated entity cleanup
- **ItemCleanerTask**: Ground item cleanup with recovery
- **InactiveChunkUnloader**: Smart chunk unloading
- **ChunkPreloader**: Predictive chunk loading

### Cross-Platform Architecture
- **PlatformDetector**: Runtime platform detection
- **SchedulerWrapper**: Cross-platform task scheduling
- **BedrockPlayerUtils**: Bedrock Edition compatibility

## Configuration System

### Configuration Hierarchy
1. **Master Configuration** (`config.yml`) - Global module control
2. **Module Configurations** - Individual feature settings
3. **World Overrides** - Per-world configuration customization
4. **Runtime Updates** - GUI-based configuration changes

### Master Configuration (`config.yml`)

#### Module Control
```yaml
modules:
  mobs: true                    # Enable mob limiting
  storage: true                 # Enable storage block limiting
  redstone: true                # Enable redstone control
  alerts: true                  # Enable alert system
  auto-chunk-scan: true         # Enable periodic chunk scanning
  item-cleaner: true            # Enable item cleanup
  entity-cleanup: true          # Enable entity cleanup
  monitoring: true              # Enable performance monitoring
  chunk-management: true        # Enable smart chunk management
  auto-mob-removal: true        # Enable smart mob removal
  platform-detection: true     # Enable multi-platform support
  bedrock-compatibility: true  # Enable Bedrock Edition support
```

#### Performance Settings
```yaml
performance:
  cache:
    enabled: true
    size: 1000
    expire-after-seconds: 30
  async:
    enabled: true
    thread-pool-size: 4
  advanced-redstone-control: true
```

#### Debug Options
```yaml
debug:
  enabled: false
  log-level: "INFO"
  verbose-scanning: false
  performance-logging: false
```

### Module Configurations

#### Mob Management (`mobs.yml`)
```yaml
limits:
  mobs-per-chunk: 40

smart-management:
  enabled: true
  scan-interval-ticks: 200
  max-mobs-per-tick-removal: 10
  protection:
    named-mobs: true
    tamed-animals: true
    equipped-mobs: true
    plugin-created-mobs: true
  removal-priority:
    farm-animals: 1
    neutral-mobs: 2
    hostile-mobs: 3
    other-mobs: 4
```

#### Storage Management (`storage.yml`)
```yaml
limits:
  chests-per-chunk: 20
  hoppers-per-chunk: 8
  furnaces-per-chunk: 10
  blast-furnaces-per-chunk: 6
  smokers-per-chunk: 6
  barrels-per-chunk: 15
  droppers-per-chunk: 8
  dispensers-per-chunk: 8
  shulker-boxes-per-chunk: 5
  tnt-per-chunk: 6
  pistons-per-chunk: 12
  observers-per-chunk: 10
```

#### Redstone Control (`redstone.yml`)
```yaml
control:
  enabled: true
  max-activity-duration-ticks: 100
  disable-on-excessive-activity: true
  notify-players-on-disable: true
  
advanced-tracking:
  enabled: true
  frequency-analysis: true
  circuit-mapping: true
  intelligent-shutdown: true
```

#### Performance Monitoring (`monitoring.yml`)
```yaml
monitoring:
  tps:
    enabled: true
    calculation-interval-ticks: 20
    alert-thresholds:
      critical: 15.0
      warning: 18.0
      good: 19.5
  
  memory:
    enabled: true
    monitoring-interval-ticks: 100
    gc-monitoring: true
    
  lag-detection:
    enabled: true
    tick-threshold-ms: 100
    consecutive-spikes-threshold: 3
    max-tracked-spikes: 100
    auto-analyze: true

alerts:
  enabled: true
  delivery:
    console: true
    players: true
    discord: false
  cooldown:
    lag-spike-alerts: 30
    memory-alerts: 60
    tps-alerts: 45
```

#### Item Cleanup (`itemcleaner.yml`)
```yaml
cleanup:
  enabled: true
  interval-ticks: 6000
  worlds:
    - "world"
    - "world_nether"
    - "world_the_end"
  
  exclusions:
    - "DIAMOND"
    - "EMERALD"
    - "NETHERITE_INGOT"
    
  abyss:
    enabled: true
    retention-seconds: 120
    max-items-per-player: 30
    
  warnings:
    enabled: true
    warning-time-seconds: 30
    countdown-messages: true
```

#### Chunk Management (`chunks.yml`)
```yaml
management:
  auto-unload:
    enabled: true
    inactivity-threshold-minutes: 15
    activity-radius-chunks: 8
    
  preloading:
    enabled: true
    preload-radius-chunks: 4
    directional-preloading: true
    
  activity-tracking:
    player-visits: true
    block-changes: true
    entity-spawning: true
    redstone-activity: true
    
  protection:
    important-blocks: true
    active-redstone: true
    named-entities: true
    player-structures: true
```

### World-Specific Configurations

#### World Override Structure
```yaml
# world_nether.yml
limits:
  mobs-per-chunk: 25          # Reduced from global 40
  tnt-per-chunk: 0            # No TNT allowed in nether
  
monitoring:
  tps:
    alert-thresholds:
      warning: 17.0           # Different threshold for nether
      
cleanup:
  item-cleaner:
    interval-ticks: 3000      # More frequent cleanup
```

#### Configuration Examples

**High-Performance Server**:
```yaml
# Reduced limits for maximum stability
limits:
  mobs-per-chunk: 30
  hoppers-per-chunk: 6
  
monitoring:
  tps:
    alert-thresholds:
      warning: 19.0
      critical: 17.0
      
performance:
  cache:
    size: 2000
    expire-after-seconds: 60
```

**Creative Server**:
```yaml
# Higher limits for building
limits:
  chests-per-chunk: 50
  hoppers-per-chunk: 20
  
modules:
  redstone: false            # Disable redstone restrictions
  auto-mob-removal: false    # Disable mob removal
```

**Survival Server**:
```yaml
# Balanced settings
smart-management:
  enabled: true
  protection:
    named-mobs: true
    tamed-animals: true
    
item-cleaner:
  abyss:
    retention-seconds: 300    # 5 minutes recovery time
```

## Commands and Permissions

### Command Overview

#### `/lagxpert` - Main Command
**Aliases**: `lx`, `lagx`  
**Permission**: `lagxpert.use` (default: true)

**Subcommands**:
- `help` - Show contextual help
- `reload` - Reload configurations (requires `lagxpert.admin`)
- `inspect <x> <z> [world]` - Inspect chunk (requires `lagxpert.admin`)
- `chunkload` - Redirect to `/chunkstatus`

**Usage Examples**:
```bash
/lagxpert help
/lagxpert reload
/lagxpert inspect 10 20 world
/lagxpert inspect -5 15
```

#### `/lagxpertgui` - GUI Configuration
**Aliases**: `lxgui`, `lagxgui`  
**Permission**: `lagxpert.gui` (default: op)

**Subcommands**:
- `open` - Open configuration GUI
- `close` - Close GUI
- `reload` - Reload GUI configurations
- `sessions` - Show session statistics
- `help` - Show GUI help

**Usage Examples**:
```bash
/lagxpertgui
/lagxpertgui open
/lagxpertgui sessions
```

#### `/chunkstatus` - Chunk Information
**Aliases**: `cs`  
**Permission**: `lagxpert.chunkstatus` (default: true)

**Features**:
- Real-time chunk analysis
- Optimized block counting
- Color-coded display
- Shows only non-zero counts

**Usage**:
```bash
/chunkstatus
/cs
```

#### `/tps` - Performance Monitoring
**Aliases**: `lag`, `performance`  
**Permission**: `lagxpert.tps` (default: true)

**Subcommands**:
- `summary` - Basic TPS summary (default)
- `detailed` - Detailed performance information
- `memory` - Memory usage details
- `chunks` - Chunk loading information
- `lagspikes` - Recent lag spikes
- `history` - Performance history
- `reset` - Reset statistics (requires `lagxpert.admin`)

**Usage Examples**:
```bash
/tps
/tps detailed
/tps memory
/tps lagspikes
/tps history
```

#### `/abyss` - Item Recovery
**Permission**: `lagxpert.abyss` (default: true)

**Features**:
- Recover recently cleared items
- Automatic retention management
- Player-specific recovery

**Usage**:
```bash
/abyss
```

#### `/clearitems` - Manual Item Cleanup
**Aliases**: `ci`, `cleanitems`  
**Permission**: `lagxpert.clearitems` (default: op)

**Subcommands**:
- `all` - Clear from all worlds (requires `lagxpert.clearitems.all`)
- `<world>` - Clear from specific world (requires `lagxpert.clearitems.world`)

**Usage Examples**:
```bash
/clearitems
/clearitems all
/clearitems world
/clearitems world_nether
```

### Permission System

#### Basic User Permissions (default: true)
- `lagxpert.use` - Basic plugin access
- `lagxpert.chunkstatus` - View chunk information
- `lagxpert.abyss` - Recover cleared items
- `lagxpert.tps` - View server performance

#### Administrative Permissions (default: op)
- `lagxpert.admin` - Administrative functions
- `lagxpert.gui` - GUI system access
- `lagxpert.clearitems` - Item clearing commands
- `lagxpert.clearitems.all` - Global item clearing
- `lagxpert.clearitems.world` - World-specific clearing
- `lagxpert.monitoring.alerts` - Receive performance alerts

#### GUI Permissions (default: op)
- `lagxpert.gui.basic` - Basic GUI configuration
- `lagxpert.gui.advanced` - Advanced GUI options
- `lagxpert.gui.admin` - Administrative GUI functions

#### Bypass Permissions (default: false)
**Master Bypass**:
- `lagxpert.bypass.*` - All bypass permissions

**Individual Bypasses**:
- `lagxpert.bypass.mobs` - Bypass mob limits
- `lagxpert.bypass.redstone` - Bypass redstone controls
- `lagxpert.bypass.hoppers` - Bypass hopper limits
- `lagxpert.bypass.chests` - Bypass chest limits
- `lagxpert.bypass.furnaces` - Bypass furnace limits
- `lagxpert.bypass.blast_furnaces` - Bypass blast furnace limits
- `lagxpert.bypass.smokers` - Bypass smoker limits
- `lagxpert.bypass.barrels` - Bypass barrel limits
- `lagxpert.bypass.droppers` - Bypass dropper limits
- `lagxpert.bypass.dispensers` - Bypass dispenser limits
- `lagxpert.bypass.shulker_boxes` - Bypass shulker box limits
- `lagxpert.bypass.tnt` - Bypass TNT limits
- `lagxpert.bypass.pistons` - Bypass piston limits
- `lagxpert.bypass.observers` - Bypass observer limits

## Core Features

### Chunk-Based Performance Management

#### Smart Chunk Analysis
The plugin continuously monitors chunk activity through multiple metrics:
- **Player Activity**: Tracks player visits and time spent in chunks
- **Block Changes**: Monitors block placement, breaking, and modifications
- **Entity Activity**: Tracks entity spawning, movement, and interactions
- **Redstone Activity**: Monitors redstone circuit activation and frequency

#### Intelligent Chunk Unloading
```yaml
# Automatic chunk unloading based on inactivity
auto-unload:
  enabled: true
  inactivity-threshold-minutes: 15
  activity-radius-chunks: 8
  
# Protection rules prevent unloading important chunks
protection:
  important-blocks: true      # Spawners, beacons, etc.
  active-redstone: true       # Active redstone circuits
  named-entities: true        # Named mobs and pets
  player-structures: true     # Player-built structures
```

#### Predictive Chunk Loading
```yaml
# Intelligent chunk preloading
preloading:
  enabled: true
  preload-radius-chunks: 4
  directional-preloading: true  # Preload in movement direction
```

### Entity and Block Management

#### Smart Mob Management
The plugin provides intelligent mob management with comprehensive protection:

**Protection Rules**:
- Named mobs (custom name tags)
- Tamed animals (pets)
- Equipped mobs (wearing armor/items)
- Plugin-created mobs (spawned by other plugins)
- Leashed mobs
- Breeding animals

**Removal Priority System**:
```yaml
removal-priority:
  farm-animals: 1      # Lowest priority (protected)
  neutral-mobs: 2      # Medium priority
  hostile-mobs: 3      # High priority
  other-mobs: 4        # Highest priority
```

#### Storage Block Limits
Comprehensive limits prevent chunk overloading:
- **Tile Entities**: Chests, hoppers, furnaces, dispensers
- **Redstone Components**: Pistons, observers, repeaters
- **Special Blocks**: Shulker boxes, TNT, beacons

### Performance Monitoring

#### Real-Time TPS Monitoring
```yaml
tps:
  calculation-interval-ticks: 20
  alert-thresholds:
    critical: 15.0    # Red alert
    warning: 18.0     # Yellow alert
    good: 19.5        # Green status
```

#### Lag Spike Detection
```yaml
lag-detection:
  enabled: true
  tick-threshold-ms: 100              # 100ms threshold
  consecutive-spikes-threshold: 3     # 3 consecutive spikes
  auto-analyze: true                  # Automatic cause analysis
```

#### Memory Monitoring
```yaml
memory:
  enabled: true
  monitoring-interval-ticks: 100
  gc-monitoring: true
  alert-thresholds:
    warning: 80     # 80% memory usage
    critical: 90    # 90% memory usage
```

### Item Management System

#### Intelligent Item Cleanup
```yaml
cleanup:
  enabled: true
  interval-ticks: 6000        # 5 minutes
  
  exclusions:                 # Protected items
    - "DIAMOND"
    - "EMERALD"
    - "NETHERITE_INGOT"
    
  warnings:
    enabled: true
    warning-time-seconds: 30
    countdown-messages: true
```

#### Abyss Recovery System
Players can recover items cleared by the plugin:
```yaml
abyss:
  enabled: true
  retention-seconds: 120      # 2 minutes recovery window
  max-items-per-player: 30    # Maximum items stored per player
```

### Redstone Control System

#### Activity Monitoring
```yaml
control:
  enabled: true
  max-activity-duration-ticks: 100    # 5 seconds maximum
  disable-on-excessive-activity: true
  notify-players-on-disable: true
```

#### Advanced Circuit Analysis
```yaml
advanced-tracking:
  enabled: true
  frequency-analysis: true      # Analyze activation frequency
  circuit-mapping: true         # Map connected components
  intelligent-shutdown: true    # Smart shutdown decisions
```

### GUI Configuration System

#### Interactive Configuration
The GUI system provides user-friendly configuration management:
- **Module Toggle**: Enable/disable features
- **Limit Adjustment**: Real-time limit changes
- **Monitoring Settings**: Performance threshold configuration
- **World-Specific Settings**: Per-world customization

#### Session Management
```yaml
gui:
  session-timeout-minutes: 5
  max-concurrent-sessions: 10
  auto-save-changes: true
  confirmation-dialogs: true
```

## Performance Monitoring

### TPS (Ticks Per Second) Monitoring

#### Multi-Window TPS Calculation
The plugin calculates TPS across multiple time windows:
- **Short-term**: Last 10 seconds
- **Medium-term**: Last 60 seconds  
- **Long-term**: Last 300 seconds (5 minutes)

#### Color-Coded Display
- **Green (19.5+)**: Excellent performance
- **Yellow (18.0-19.5)**: Good performance with minor issues
- **Red (15.0-18.0)**: Performance issues requiring attention
- **Dark Red (<15.0)**: Critical performance problems

#### Performance State Tracking
```java
public enum PerformanceState {
    GOOD,       // TPS > 19.5
    WARNING,    // TPS 18.0-19.5
    CRITICAL    // TPS < 18.0
}
```

### Memory Monitoring

#### JVM Memory Tracking
- **Heap Memory**: Used and maximum heap memory
- **Non-Heap Memory**: Method area and compressed class space
- **Garbage Collection**: GC frequency and duration monitoring

#### Memory Alerts
```yaml
memory:
  alert-thresholds:
    warning: 80     # 80% memory usage warning
    critical: 90    # 90% memory usage critical alert
    
  gc-monitoring:
    enabled: true
    excessive-gc-threshold: 5    # 5 GC events per minute
```

### Lag Spike Detection

#### Intelligent Detection
The system uses sophisticated algorithms to detect legitimate lag spikes:
- **Tick Time Measurement**: Precise timing using `System.nanoTime()`
- **Consecutive Spike Tracking**: Requires multiple consecutive spikes
- **Automatic Cause Analysis**: Identifies potential lag sources

#### Cause Analysis
When lag spikes are detected, the system automatically analyzes:
- **Chunk Loading**: Excessive chunk generation or loading
- **Entity Counts**: High entity counts in specific chunks
- **Redstone Activity**: Overly active redstone circuits
- **Memory Pressure**: Low memory conditions

### Performance History

#### Data Collection
The plugin maintains performance history for analysis:
- **TPS History**: 24 hours of TPS data
- **Memory Usage**: Memory consumption trends
- **Lag Spike Events**: Detailed lag spike information
- **Chunk Activity**: Chunk loading and unloading patterns

#### Trend Analysis
```yaml
history:
  retention-hours: 24
  data-points-per-hour: 60
  trend-analysis: true
  performance-reports: true
```

### Alert System

#### Alert Types
- **TPS Alerts**: Server performance degradation
- **Memory Alerts**: High memory usage
- **Lag Spike Alerts**: Detected performance spikes
- **Chunk Overload Alerts**: Chunk limit violations

#### Alert Delivery
```yaml
alerts:
  delivery:
    console: true           # Server console
    players: true           # Online players with permission
    discord: false          # Discord webhook (if configured)
    
  cooldown:
    lag-spike-alerts: 30    # 30 second cooldown
    memory-alerts: 60       # 60 second cooldown
    tps-alerts: 45          # 45 second cooldown
```

## Best Practices

### Server Configuration

#### Recommended Settings for Different Server Types

**High-Performance Competitive Server**:
```yaml
# Strict limits for maximum stability
limits:
  mobs-per-chunk: 25
  hoppers-per-chunk: 6
  chests-per-chunk: 15
  
monitoring:
  tps:
    alert-thresholds:
      warning: 19.0
      critical: 17.0
      
smart-management:
  enabled: true
  max-mobs-per-tick-removal: 15
```

**Creative Building Server**:
```yaml
# Higher limits for building freedom
limits:
  chests-per-chunk: 50
  hoppers-per-chunk: 20
  pistons-per-chunk: 25
  
modules:
  redstone: false          # Allow complex redstone builds
  auto-mob-removal: false  # Preserve entity builds
```

**Survival/SMP Server**:
```yaml
# Balanced settings for gameplay
limits:
  mobs-per-chunk: 35
  hoppers-per-chunk: 10
  
item-cleaner:
  abyss:
    retention-seconds: 300  # 5 minutes recovery
    
smart-management:
  protection:
    named-mobs: true
    tamed-animals: true
```

### Performance Optimization

#### Monitoring Best Practices
1. **Regular Monitoring**: Check `/tps detailed` regularly
2. **Trend Analysis**: Use `/tps history` to identify patterns
3. **Proactive Alerts**: Configure appropriate alert thresholds
4. **Performance Baselines**: Establish normal performance ranges

#### Configuration Optimization
1. **Gradual Adjustments**: Make small incremental changes
2. **Testing**: Test changes during low-activity periods
3. **Documentation**: Document configuration changes
4. **Backup**: Always backup configurations before major changes

#### Chunk Management
1. **Activity Monitoring**: Monitor chunk activity patterns
2. **Preloading Strategy**: Configure preloading based on server layout
3. **Unloading Policy**: Set appropriate inactivity thresholds
4. **Protection Rules**: Protect important chunks from unloading

### Permission Management

#### Recommended Permission Structure
```yaml
# Basic players
permissions:
  - lagxpert.use
  - lagxpert.chunkstatus
  - lagxpert.abyss
  - lagxpert.tps

# Moderators
permissions:
  - lagxpert.use
  - lagxpert.chunkstatus
  - lagxpert.abyss
  - lagxpert.tps
  - lagxpert.clearitems.world
  - lagxpert.monitoring.alerts

# Administrators
permissions:
  - lagxpert.*
  - lagxpert.bypass.mobs     # Selective bypasses
  - lagxpert.bypass.storage  # Only for building
```

#### Bypass Permission Strategy
1. **Selective Granting**: Grant specific bypasses only when needed
2. **Time-Limited**: Consider temporary bypass permissions
3. **Monitoring**: Monitor bypass usage for potential abuse
4. **Documentation**: Document who has bypass permissions and why

### Maintenance and Updates

#### Regular Maintenance Tasks
1. **Configuration Review**: Weekly configuration review
2. **Performance Analysis**: Monthly performance reports
3. **Log Analysis**: Check logs for errors or warnings
4. **Update Checks**: Stay current with plugin updates

#### Backup Strategy
1. **Configuration Backup**: Regular backup of all YAML files
2. **Performance Data**: Backup performance history
3. **Version Control**: Consider using Git for configuration management
4. **Rollback Plan**: Have a rollback strategy for failed updates

## API Documentation

### LagXpertAPI Class

#### Entity Counting Methods
```java
// Count living entities in a chunk
public static int countLivingEntitiesInChunk(Chunk chunk)

// Count specific entity types
public static int countEntitiesOfType(Chunk chunk, EntityType entityType)

// Count entities with specific criteria
public static int countEntitiesWithCriteria(Chunk chunk, Predicate<Entity> criteria)
```

#### Block Counting Methods
```java
// Count tile entities (optimized for performance)
public static int countTileEntitiesInChunk(Chunk chunk, Material material)

// Count specific block types
public static int countBlocksInChunk(Chunk chunk, Material material)

// Count blocks matching criteria
public static int countBlocksWithCriteria(Chunk chunk, Predicate<Block> criteria)
```

#### Configuration Access
```java
// Get limit for specific material
public static int getLimitForMaterial(Material material)

// Get world-specific limit
public static int getWorldLimitForMaterial(World world, Material material)

// Check if material is limited
public static boolean isMaterialLimited(Material material)
```

#### Performance Data Access
```java
// Get current TPS
public static double getCurrentTPS()

// Get TPS for specific time window
public static double getTPS(TPSWindow window)

// Get memory usage information
public static MemoryInfo getMemoryInfo()

// Get performance state
public static PerformanceState getPerformanceState()
```

### Custom Events

#### ChunkOverloadEvent
```java
public class ChunkOverloadEvent extends Event {
    private final Chunk chunk;
    private final String cause;
    private final int currentCount;
    private final int limit;
    private final Material material;
    
    // Event is fired when chunk limits are exceeded
}
```

**Usage Example**:
```java
@EventHandler
public void onChunkOverload(ChunkOverloadEvent event) {
    Chunk chunk = event.getChunk();
    String cause = event.getCause();
    
    // Handle chunk overload
    Bukkit.getLogger().info("Chunk overload in " + chunk.getX() + "," + chunk.getZ() + 
                           " caused by: " + cause);
}
```

#### LagEvent
```java
public class LagEvent extends Event {
    private final double tps;
    private final long duration;
    private final LagSeverity severity;
    private final String potentialCause;
    
    // Event is fired when lag is detected
}
```

**Usage Example**:
```java
@EventHandler
public void onLag(LagEvent event) {
    double tps = event.getTPS();
    LagSeverity severity = event.getSeverity();
    
    if (severity == LagSeverity.CRITICAL) {
        // Handle critical lag
        broadcastLagAlert(tps);
    }
}
```

### Integration Examples

#### Basic Integration
```java
public class MyPlugin extends JavaPlugin {
    
    @Override
    public void onEnable() {
        // Check if LagXpert is available
        if (Bukkit.getPluginManager().getPlugin("LagXpert") != null) {
            // Register event listeners
            Bukkit.getPluginManager().registerEvents(new LagXpertListener(), this);
        }
    }
    
    private class LagXpertListener implements Listener {
        @EventHandler
        public void onChunkOverload(ChunkOverloadEvent event) {
            // Your custom handling
        }
    }
}
```

#### Advanced Integration
```java
public class AdvancedIntegration {
    
    public void checkChunkSafety(Chunk chunk) {
        // Check mob count
        int mobCount = LagXpertAPI.countLivingEntitiesInChunk(chunk);
        int mobLimit = LagXpertAPI.getLimitForMaterial(null); // Mob limit
        
        if (mobCount > mobLimit * 0.8) {
            // Warn about approaching limit
            notifyPlayersInChunk(chunk, "Chunk approaching mob limit!");
        }
        
        // Check storage blocks
        int hopperCount = LagXpertAPI.countTileEntitiesInChunk(chunk, Material.HOPPER);
        int hopperLimit = LagXpertAPI.getLimitForMaterial(Material.HOPPER);
        
        if (hopperCount >= hopperLimit) {
            // Prevent hopper placement
            cancelHopperPlacement(chunk);
        }
    }
    
    public void monitorPerformance() {
        double currentTPS = LagXpertAPI.getCurrentTPS();
        PerformanceState state = LagXpertAPI.getPerformanceState();
        
        if (state == PerformanceState.CRITICAL) {
            // Take emergency action
            emergencyLagMitigation();
        }
    }
}
```

## Troubleshooting

### Common Issues

#### "Lag spike alerts every 30 seconds"
**Symptoms**: Regular lag spike alerts even with no players online
**Cause**: Monitoring system detecting server pauses as lag spikes
**Solution**:
```yaml
# In monitoring.yml
monitoring:
  lag-detection:
    tick-threshold-ms: 200      # Increase from 100 to 200
    consecutive-spikes-threshold: 5  # Increase from 3 to 5
    
alerts:
  cooldown:
    lag-spike-alerts: 120       # Increase from 30 to 120 seconds
```

#### "GUI not working for Bedrock players"
**Symptoms**: Bedrock players cannot interact with GUI properly
**Cause**: Cross-platform compatibility issues
**Solution**:
```yaml
# In config.yml
modules:
  bedrock-compatibility: true
  
gui:
  bedrock-optimized: true
  simplified-interface: true
```

#### "Plugin not loading on Folia"
**Symptoms**: Plugin fails to load on Folia servers
**Cause**: Platform detection or scheduling issues
**Solution**:
```yaml
# In config.yml
modules:
  platform-detection: true
  
performance:
  folia-support: true
  region-based-tasks: true
```

#### "High memory usage"
**Symptoms**: Plugin consuming excessive memory
**Cause**: Cache not expiring properly or excessive data retention
**Solution**:
```yaml
# In config.yml
performance:
  cache:
    size: 500                   # Reduce cache size
    expire-after-seconds: 15    # Reduce expiry time
    
# In monitoring.yml
history:
  retention-hours: 12          # Reduce history retention
```

### Performance Issues

#### "TPS dropping after plugin installation"
**Diagnosis**:
1. Check `/tps detailed` for performance breakdown
2. Review configured limits - may be too strict
3. Check if async processing is enabled
4. Monitor chunk loading patterns

**Solutions**:
```yaml
# Optimize performance settings
performance:
  async:
    enabled: true
    thread-pool-size: 6        # Increase thread pool
    
  cache:
    enabled: true
    size: 2000                 # Increase cache size
    
# Reduce scan frequency
task:
  scan-interval-ticks: 1200    # Reduce from 600 to 1200
```

#### "Chunk loading lag"
**Diagnosis**:
1. Check chunk preloading settings
2. Monitor chunk activity patterns
3. Review unloading thresholds

**Solutions**:
```yaml
# Optimize chunk management
chunks:
  management:
    preloading:
      preload-radius-chunks: 2  # Reduce preload radius
      
    auto-unload:
      inactivity-threshold-minutes: 10  # Reduce threshold
```

### Configuration Issues

#### "World-specific settings not working"
**Diagnosis**:
1. Check world name spelling in configuration files
2. Verify world override files exist
3. Check configuration inheritance

**Solutions**:
1. Use exact world names from server
2. Create world override files in `/worlds/` directory
3. Use `/lagxpert reload` after configuration changes

#### "Bypasses not working"
**Diagnosis**:
1. Check permission node spelling
2. Verify permission plugin inheritance
3. Check if bypass permissions are granted

**Solutions**:
```yaml
# Grant specific bypasses
permissions:
  - lagxpert.bypass.mobs
  - lagxpert.bypass.storage
  
# Or grant all bypasses
permissions:
  - lagxpert.bypass.*
```

### Debugging Tools

#### Enable Debug Mode
```yaml
# In config.yml
debug:
  enabled: true
  log-level: "DEBUG"
  verbose-scanning: true
  performance-logging: true
```

#### Performance Logging
```yaml
# In monitoring.yml
monitoring:
  performance-logging:
    enabled: true
    log-interval-minutes: 10
    include-memory-stats: true
    include-chunk-stats: true
```

#### Check Plugin Status
```bash
# Basic plugin information
/lagxpert help

# Detailed performance information
/tps detailed

# Check current chunk status
/chunkstatus

# GUI session information
/lagxpertgui sessions
```

### Getting Help

#### Log Collection
When reporting issues, please include:
1. Server version and platform (Paper, Spigot, etc.)
2. Plugin version
3. Relevant configuration files
4. Server logs showing the issue
5. Performance data (`/tps detailed` output)

#### Support Channels
- **GitHub Issues**: For bug reports and feature requests
- **Discord**: For community support and discussions
- **Wiki**: For additional documentation and guides

#### Common Log Messages
```
[INFO] LagXpert v2.2 enabled successfully
[INFO] Platform detected: Paper
[INFO] Bedrock compatibility: Enabled
[WARN] High memory usage detected: 85%
[ERROR] Failed to load world configuration for: world_custom
```

This comprehensive documentation covers all aspects of the LagXpert plugin, from installation and configuration to advanced API usage and troubleshooting. Regular updates to this documentation ensure it remains current with plugin development and user needs.