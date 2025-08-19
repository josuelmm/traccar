# Timestamp Rollover Compensation

## Overview

Traccar includes automatic timestamp rollover compensation to handle GPS devices that experience internal counter rollovers, causing them to report incorrect timestamps. This feature is specifically designed for devices like GT06N with MTK2503D processors that may reset to dates around 2006 due to GPS week rollover issues.

## How It Works

The compensation system:

1. **Detects Rollover**: Identifies timestamps that fall outside reasonable ranges (>8 years difference from server time or in characteristic ranges like 2005-2007)
2. **Calculates Correction**: Applies GPS week rollover mathematics using multiples of 1024 weeks (≈19.7 years)
3. **Validates Result**: Ensures corrected timestamps fall within a reasonable window relative to server time
4. **Audits Changes**: Records correction details in position attributes for transparency

## Configuration

### Global Configuration

Add to your `traccar.xml` configuration file:

```xml
<!-- Enable/disable automatic rollover compensation (default: true) -->
<entry key='time.rollover.auto'>true</entry>

<!-- Time window in days for valid corrections (default: 90) -->
<entry key='time.rollover.window.days'>90</entry>

<!-- GPS week rollover step in weeks (default: 1024) -->
<entry key='time.rollover.step.weeks'>1024</entry>

<!-- Maximum correction attempts (default: 2) -->
<entry key='time.rollover.max.multiple'>2</entry>
```

### Device-Level Configuration

You can configure rollover compensation for specific devices through the web interface:

1. Go to **Settings** → **Devices**
2. Select the device
3. Add attributes:
   - `time.rollover.auto`: `true` or `false`
   - `time.rollover.window.days`: Number of days (e.g., `90`)
   - `time.rollover.step.weeks`: Step size in weeks (e.g., `1024`)
   - `time.rollover.max.multiple`: Maximum attempts (e.g., `2`)

### Group-Level Configuration

Configure for device groups:

1. Go to **Settings** → **Groups**
2. Select the group
3. Add the same attributes as device-level configuration

## Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `time.rollover.auto` | Boolean | `true` | Enable/disable automatic rollover compensation |
| `time.rollover.window.days` | Integer | `90` | Time window in days for considering corrected timestamps valid |
| `time.rollover.step.weeks` | Integer | `1024` | GPS week rollover period in weeks |
| `time.rollover.max.multiple` | Integer | `2` | Maximum correction factors to attempt (k=1, k=2, etc.) |

## Examples

### Basic Setup for GT06N Devices

For GT06N devices experiencing 2006 rollover issues:

```xml
<entry key='time.rollover.auto'>true</entry>
<entry key='time.rollover.window.days'>90</entry>
```

### Conservative Configuration

For environments where false positives are a concern:

```xml
<entry key='time.rollover.auto'>true</entry>
<entry key='time.rollover.window.days'>30</entry>
<entry key='time.rollover.max.multiple'>1</entry>
```

### Disable for Specific Devices

To disable rollover compensation for devices that don't need it:

Device attribute: `time.rollover.auto` = `false`

## Monitoring and Troubleshooting

### Position Attributes

When rollover compensation is applied, the following attributes are added to positions:

- `rolloverApplied`: `true` when correction was applied
- `rolloverK`: Correction factor used (1, 2, etc.)
- `rolloverDeltaSec`: Time difference in seconds between original and corrected timestamp

### Logging

Enable DEBUG logging to monitor rollover corrections:

```xml
<entry key='logger.level'>debug</entry>
```

Look for log entries like:
```
DEBUG: Applied rollover compensation: device=123, original=2006-01-01 12:00:00, corrected=2025-01-15 12:00:00, k=1, delta=600000000s
```

### Common Issues

**No correction applied to 2006 timestamps:**
- Check that `time.rollover.auto` is `true`
- Verify position is marked as valid
- Ensure corrected timestamp would fall within the configured window

**Incorrect corrections:**
- Adjust `time.rollover.window.days` if corrections are too restrictive
- Increase `time.rollover.max.multiple` if k=1 correction is insufficient

**Performance impact:**
- The handler is designed for O(1) performance
- Monitor processing times if handling high-volume position streams

## Technical Details

### GPS Week Rollover

GPS week counters reset every 1024 weeks (approximately 19.7 years). The compensation algorithm:

1. Calculates correction as: `corrected_time = device_time + (k × 1024_weeks × seconds_per_week)`
2. Tests correction factors k=1, k=2, up to the configured maximum
3. Selects the correction that places the timestamp closest to server time within the valid window

### Detection Criteria

Rollover is detected when:
- Time difference between device and server time > 8 years, OR
- Device timestamp falls in characteristic rollover range (2005-2007)
- Position is marked as valid
- Rollover compensation is enabled

### Compatibility

- Works with all protocol decoders
- No database schema changes required
- Backward compatible with existing configurations
- Can be enabled/disabled without affecting other functionality

## Migration

### Upgrading from Previous Versions

No migration steps required. The feature is enabled by default and will automatically handle rollover cases.

### Disabling the Feature

To completely disable rollover compensation:

```xml
<entry key='time.rollover.auto'>false</entry>
```

## References

- GitHub Issue: [#5634](https://github.com/traccar/traccar/issues/5634)
- Related PR: [#5635](https://github.com/traccar/traccar/pull/5635)
- GPS Week Rollover: [Wikipedia](https://en.wikipedia.org/wiki/GPS_Week_Number_Rollover)