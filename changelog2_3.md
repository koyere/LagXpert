# LagXpert v2.3 - Performance & Reliability Update

## 🚀 What's New

### Major Performance Improvements
- **Atomic Counter System**: Implemented high-performance atomic counters for TNT and block tracking, replacing expensive chunk scanning operations
- **Pre-Placement Validation**: Enhanced block placement validation to prevent race conditions and improve reliability
- **Smart Alert System**: Added intelligent alert filtering to reduce spam when no players are online

### Enhanced Item Cleaner System
- **Recently Broken Block Tracking**: New system tracks when players break blocks and provides grace periods for dropped items
- **Material-Specific Grace Periods**: Configurable grace periods for valuable materials (diamonds, emeralds, spawners, etc.)
- **Intelligent Item Protection**: Prevents cleanup of items from recently broken blocks, solving the "mining diamonds but inventory full" scenario

### Bug Fixes
- **Fixed TNT Limit Bypass**: Resolved critical race condition that allowed TNT placement after 3-4 attempts despite limits
- **Improved Thread Safety**: Enhanced concurrent access handling for block counters and cache operations
- **Alert Frequency Fix**: Resolved issue with alerts being sent every 2 minutes even when players are online

## 🔧 Technical Improvements

### Performance Optimizations
- **O(1) Block Counting**: TNT and piston counting now uses atomic operations instead of O(n) chunk scanning
- **Reduced Memory Usage**: Optimized cache invalidation and cleanup processes
- **Async Processing**: Enhanced asynchronous chunk analysis with better error handling

### Code Quality
- **Better Error Handling**: Improved exception handling throughout the codebase
- **Enhanced Logging**: Added detailed debug logging for troubleshooting
- **Thread-Safe Operations**: Strengthened concurrent access patterns

## ⚙️ Configuration Updates

### New Configuration Options

#### itemcleaner.yml
```yaml
broken-block-tracking:
  enabled: true
  default-grace-period-seconds: 180  # 3 minutes
  custom-grace-periods:
    DIAMOND_ORE: 300      # 5 minutes for valuable ores
    EMERALD_ORE: 300
    ANCIENT_DEBRIS: 300
    CHEST: 600           # 10 minutes for storage blocks
    SPAWNER: 900         # 15 minutes for spawners
```

#### monitoring.yml
```yaml
alerts:
  delivery:
    skip-when-no-players-online: true  # Reduces alert spam
```

## 🎯 User Experience Improvements

### For Server Administrators
- **Reduced Log Spam**: Fewer unnecessary alerts when server is empty
- **Better Performance Monitoring**: More accurate TPS and memory tracking
- **Improved Debugging**: Enhanced debug logs for troubleshooting

### For Players
- **Protected Valuable Items**: Mining diamonds and other valuables is now safer
- **Reduced Frustration**: Items from recently broken blocks won't be cleaned immediately
- **Better Performance**: Smoother gameplay due to optimized block limit checking

## 🛡️ Compatibility & Requirements

### Minecraft Compatibility
- **Tested Versions**: 1.16.5 - 1.21.7
- **Newly Supported**: 1.21.6, 1.21.7
- **Server Software**: Spigot, Paper, Purpur, and forks

### Java Requirements
- **Minimum**: Java 11
- **Recommended**: Java 17 or newer

## 📋 Migration Notes

### Automatic Configuration Migration
- Existing configurations will continue to work without changes
- New features are disabled by default to maintain current behavior
- Configuration files will be automatically updated with new options

### Breaking Changes
- None - this update is fully backward compatible

## 🔍 Developer Notes

### New API Features
- `ChunkDataCache.getAtomicCounter()` - High-performance block counting
- `RecentlyBrokenBlocksTracker` - Track recently broken blocks with grace periods
- Enhanced `ChunkOverloadEvent` with better context information

### Internal Changes
- Refactored `StorageListener` for better performance and reliability
- Added `ItemCleanerListener` for broken block tracking
- Improved `PerformanceTracker` with smarter alert logic

## 🐛 Known Issues

### Resolved
- ✅ TNT limit bypass exploit
- ✅ Alert spam when no players online
- ✅ Item cleaner removing freshly mined items
- ✅ Race conditions in block placement validation

### Monitoring
- Performance impact of new tracking systems (expected to be minimal)
- Memory usage with large numbers of recent block breaks (automatic cleanup implemented)

## 🙏 Credits

Thanks to the community for reporting these issues and providing valuable feedback that made this update possible.

## 📚 Documentation

For detailed configuration and usage instructions, please refer to the main README.md file.

---

**Download**: Available on GitHub Releases  
**Support**: Open an issue on GitHub for bug reports or feature requests  
**License**: GPL-3.0

*LagXpert v2.3 - Making Minecraft servers faster and more reliable, one optimization at a time.*