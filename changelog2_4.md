# LagXpert v2.4 - Action Bar Notifications Update

## 🎯 New Features

### Action Bar Messages
- **Restriction messages now appear in the action bar** instead of chat spam
- Clean, non-intrusive notifications when you try to place blocks above limits
- Messages automatically disappear after a few seconds

### Enhanced User Experience
- **Shorter cooldowns** for action bar messages (5 seconds instead of 15)
- **Compact message format** optimized for action bar display
- **Fallback system** - if action bar fails, messages still appear in chat

## ⚙️ Configuration Options

### New Settings in `messages.yml`
```yaml
delivery:
  restrictions:
    method: "actionbar"  # Options: "chat", "actionbar", "both"
    cooldown: 5          # Seconds between messages
```

**Available Methods:**
- `"chat"` - Traditional chat messages (old behavior)
- `"actionbar"` - Action bar only (new default)
- `"both"` - Send to both chat and action bar

### Message Customization
- All restriction messages now have short versions for action bar
- You can still customize both long and short message formats
- No configuration changes required - works with existing setups

## 🔧 What This Means for You

### For Players
- **Less chat spam** when building near limits
- **Cleaner interface** with subtle notifications
- **Same functionality** - all limits work exactly the same

### For Server Admins
- **Reduced chat noise** in logs and screenshots
- **Better user experience** for builders
- **Fully configurable** - switch back to chat if preferred

## 🐛 Bug Fixes

### Block Limit Issues Resolved
- **Fixed off-by-one error** - Players can now place the full configured amount of blocks
  - Previously: Limit of 10 only allowed 9 blocks
  - Now: Limit of 10 correctly allows 10 blocks
- **Fixed colored shulker boxes** - All 16 colored shulker box variants are now properly limited
  - Previously: Only purple shulker boxes were detected
  - Now: All colored shulker boxes count toward the same limit

### Affected Blocks
- **TNT, Hoppers, Pistons** - No longer stop 1 block short of limit
- **All Shulker Boxes** - White, Orange, Magenta, Light Blue, Yellow, Lime, Pink, Gray, Light Gray, Cyan, Purple, Blue, Brown, Green, Red, Black

## 🔒 Privacy & Permissions

### Alert Privacy System
- **Alert filtering by permissions** - Only players with permission receive limit alerts
- **No more base location reveals** - Prevents accidental discovery of hidden builds
- **Configurable alert access** - Admins can control who sees what alerts

### Custom Limit Permissions
- **Per-player custom limits** - Set different limits for different players/ranks
- **Permission-based limits** - Use permissions like `lagxpert.limits.hoppers.25`
- **Priority system** - Bypass > Custom limit > Default limit

### New Permission Examples
```
lagxpert.alerts.receive       - Receive all alerts
lagxpert.alerts.blocks        - Receive block limit alerts only
lagxpert.limits.hoppers.15    - Allow 15 hoppers instead of default
lagxpert.limits.mobs.50       - Allow 50 mobs instead of default
```

## 🛠️ Technical Notes

- **Backward Compatible** - All existing configurations work unchanged
- **Automatic Migration** - No manual config updates needed
- **Spigot 1.16.5+** - Compatible with all supported Minecraft versions

---

*This update improves the user experience while maintaining all existing functionality. Players will notice cleaner, less intrusive notifications when working with block limits, and limits now work exactly as configured.*