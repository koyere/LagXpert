
## ✅ `CHANGELOG.md`

# 📋 LagXpert Free - Changelog

---

## [2.2] - 2025-01-19

### 🚀 Major Features Added
- **Smart Mob Management System**: Automatically removes excess mobs while protecting important entities
  - Protects named mobs, tamed animals, equipped entities, and plugin-created mobs
  - Intelligent priority system (farm animals removed before valuable entities)
  - Performance-optimized gradual removal (max 10 mobs per chunk per tick)
  - Chunk processing cooldowns to prevent lag spikes
  - Comprehensive notifications with detailed statistics

### 🌐 Multi-Platform Support
- **Platform Detection**: Automatically detects Folia, Paper, Spigot, or Bukkit
- **Folia Compatibility**: Native support with RegionScheduler and AsyncScheduler
- **Cross-Platform Scheduling**: Intelligent scheduler wrapper for optimal performance
- **Backward Compatibility**: Seamless fallback to Bukkit scheduler when needed

### 🎮 Bedrock Edition Support  
- **Player Detection**: Automatic identification of Bedrock players via Geyser/Floodgate
- **GUI Optimization**: Bedrock-specific inventory layouts and material handling
- **Cross-Platform GUIs**: Separate templates for Java and Bedrock players
- **Smart Fallbacks**: Chat-based alternatives when GUIs aren't compatible

### 🔧 Performance Enhancements
- **Zero-Lag Design**: All new systems designed to improve performance, not hinder it
- **Intelligent Caching**: Platform detection and player type caching for optimal performance
- **Batch Processing**: Operations grouped to minimize server load
- **Region-Based Processing**: Folia optimization for distributed chunk processing

### 📊 Enhanced Monitoring
- **Advanced Statistics**: Detailed tracking of mob removals, protections, and performance
- **Platform Reporting**: Comprehensive platform detection and capability reporting
- **Migration Tracking**: Automatic configuration version tracking and migration status

### ⚙️ Configuration System Improvements
- **Automatic Migration**: Seamless upgrade from v2.1.x with automatic backup creation
- **New Configuration Sections**: Platform detection, Bedrock compatibility, smart mob management
- **Enhanced Documentation**: Comprehensive inline documentation for all new features
- **Backward Compatibility**: Preserves all existing settings during migration

### 🛡️ Entity Protection Enhancements
- **Advanced Protection Logic**: Multiple layers of entity protection with priority systems
- **Plugin Integration**: Detects and protects entities created by other plugins
- **Metadata Awareness**: Respects custom entity metadata and flags
- **Configuration Flexibility**: Granular control over protection rules

### 🎯 User Experience Improvements
- **Intelligent Notifications**: Detailed feedback about optimization actions taken
- **Cross-Platform Commands**: All commands work seamlessly across Java and Bedrock
- **Enhanced Debugging**: Comprehensive logging and debugging options for troubleshooting
- **Progressive Enhancement**: Features gracefully degrade based on available platform capabilities

### 🔄 API Enhancements
- **New API Methods**: SmartMobManager integration for external plugins
- **Platform Detection API**: Allows other plugins to detect server platform
- **Bedrock Player API**: Utilities for detecting and handling Bedrock players
- **Enhanced Events**: Additional context and information in existing events

### 📋 Migration & Compatibility
- **Automatic Configuration Migration**: Seamless upgrade from any v2.1.x version
- **Backup Creation**: Automatic backup of old configurations with timestamps
- **Zero Downtime**: Migration occurs during plugin startup with no service interruption
- **Rollback Support**: Easy restoration from automatically created backups

---

## [1.0.0] - 2025-05-05

### ✨ Added
- Modular system: mobs.yml, redstone.yml, storage.yml, etc.
- Configurable chunk limits for mobs, hoppers, chests, furnaces
- Redstone activity monitor with block cut-off
- Chunk scan task for auto detection and alerting
- Item cleaner system with recovery (Abyss) and warnings
- Full message customization via messages.yml
- Reload command for configs and language
- bStats metrics integration (plugin ID: 25746)
- Basic API: `ChunkOverloadEvent`

### ✅ Commands
- `/lagxpert`
- `/chunkstatus`
- `/abyss`

### 🔐 Permissions
- `lagxpert.use`
- `lagxpert.admin`
- `lagxpert.abyss`
- `lagxpert.bypass.mobs`, `hoppers`, `chests`, `redstone`

---

## 🔜 Planned for Future Updates
- Console/admin alerts for redstone activity
- Per-entity mob limits
- Enhanced redstone logging
- Metrics breakdown per module
