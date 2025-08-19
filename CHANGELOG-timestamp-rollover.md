# Timestamp Rollover Compensation - ChangeLog Entry

## Version 6.9.0 (TBD)

### New Features

#### Automatic Timestamp Rollover Compensation

Added automatic compensation for GPS devices experiencing timestamp rollover issues, specifically targeting GT06N devices with MTK2503D processors that reset to 2006 dates.

**Key Features:**
- Automatic detection of rollover timestamps (>8 years difference or characteristic 2005-2007 range)
- GPS week rollover mathematics using multiples of 1024 weeks
- Configurable compensation parameters at global, group, and device levels
- Comprehensive audit trail with position attributes
- Zero-configuration operation with sensible defaults

**Configuration Options:**
- `time.rollover.auto` (Boolean, default: true) - Enable/disable automatic compensation
- `time.rollover.window.days` (Integer, default: 90) - Valid time window for corrections
- `time.rollover.step.weeks` (Integer, default: 1024) - GPS week rollover period
- `time.rollover.max.multiple` (Integer, default: 2) - Maximum correction attempts

**Position Attributes Added:**
- `rolloverApplied` - Indicates when correction was applied
- `rolloverK` - Correction factor used (1, 2, etc.)
- `rolloverDeltaSec` - Time difference in seconds between original and corrected timestamp

**Benefits:**
- Fixes GT06N devices reporting 2006 timestamps
- Generic solution works with any protocol
- Maintains backward compatibility
- Provides complete audit trail
- Configurable per device/group/global

**References:**
- Addresses GitHub Issue #5634
- Related to PR #5635
- Affects devices with GPS week rollover issues

**Migration Notes:**
- Feature is enabled by default
- No database schema changes required
- Existing configurations remain unchanged
- Can be disabled by setting `time.rollover.auto=false`

**Example Before/After:**
```
Before: Device reports 2006-01-01 12:00:00
After:  Corrected to 2025-01-15 12:00:00 (with audit attributes)
```

This feature ensures accurate timestamp storage for affected devices while maintaining full transparency and configurability.