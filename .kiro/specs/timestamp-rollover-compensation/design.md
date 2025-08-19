# Design Document

## Overview

This design implements automatic timestamp rollover compensation for GPS tracking devices that experience internal counter rollovers, specifically targeting GT06N devices with MTK2503D processors that reset to 2006 dates. The solution provides a generic, configurable approach that integrates seamlessly into Traccar's existing position processing pipeline without breaking current functionality.

The compensation mechanism detects timestamps that fall outside reasonable ranges compared to the server receive time and applies corrections using GPS week rollover mathematics (multiples of 1024 weeks ≈ 619,315,200 seconds).

## Architecture

### Integration Point

The rollover compensation will be implemented as a new position handler (`TimestampRolloverHandler`) that integrates into Traccar's existing position processing chain. Based on the analysis of `ProcessingHandler.java`, the handler will be positioned early in the chain, after `ComputedAttributesHandler.Early` but before `TimeHandler`, to ensure rollover correction occurs before any time-based processing.

```
Position Processing Chain:
ComputedAttributesHandler.Early → TimestampRolloverHandler → OutdatedHandler → TimeHandler → ...
```

### Configuration System

The solution leverages Traccar's existing configuration hierarchy (device → group → global) using the `AttributeUtil.lookup` pattern. New configuration keys will be added to `Keys.java` following established patterns.

### Handler Architecture

The `TimestampRolloverHandler` extends `BasePositionHandler` and implements the standard `onPosition(Position position, Callback callback)` pattern used throughout Traccar's handler system.

## Components and Interfaces

### 1. Configuration Keys (Keys.java)

New configuration keys following Traccar's established patterns:

```java
/**
 * Enable automatic timestamp rollover compensation for devices with GPS week rollover issues.
 * Default value is true.
 */
public static final ConfigKey<Boolean> TIME_ROLLOVER_AUTO = new BooleanConfigKey(
        "time.rollover.auto",
        List.of(KeyType.CONFIG, KeyType.DEVICE),
        true);

/**
 * Time window in days for considering a timestamp valid after rollover correction.
 * Default value is 90 days.
 */
public static final ConfigKey<Integer> TIME_ROLLOVER_WINDOW_DAYS = new IntegerConfigKey(
        "time.rollover.window.days",
        List.of(KeyType.CONFIG, KeyType.DEVICE),
        90);

/**
 * GPS week rollover step in weeks. Default is 1024 weeks (GPS week rollover period).
 */
public static final ConfigKey<Integer> TIME_ROLLOVER_STEP_WEEKS = new IntegerConfigKey(
        "time.rollover.step.weeks",
        List.of(KeyType.CONFIG, KeyType.DEVICE),
        1024);

/**
 * Maximum rollover correction multiple to attempt. Default is 2 (k=1, k=2).
 */
public static final ConfigKey<Integer> TIME_ROLLOVER_MAX_MULTIPLE = new IntegerConfigKey(
        "time.rollover.max.multiple",
        List.of(KeyType.CONFIG, KeyType.DEVICE),
        2);
```

### 2. TimestampRolloverHandler

Core handler implementing the rollover detection and correction logic:

```java
public class TimestampRolloverHandler extends BasePositionHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TimestampRolloverHandler.class);
    private static final long SECONDS_PER_WEEK = 7 * 24 * 60 * 60;
    private static final long ROLLOVER_THRESHOLD_YEARS = 8;
    private static final long ROLLOVER_THRESHOLD_MS = ROLLOVER_THRESHOLD_YEARS * 365 * 24 * 60 * 60 * 1000L;
    
    private final CacheManager cacheManager;
    
    @Inject
    public TimestampRolloverHandler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
    
    @Override
    public void onPosition(Position position, Callback callback) {
        // Implementation details in next section
    }
    
    private boolean shouldApplyRolloverCompensation(Position position, long deviceId);
    private Date calculateRolloverCorrection(Date deviceTime, Date serverTime, long deviceId);
    private boolean isTimestampInValidRange(Date timestamp, Date reference, int windowDays);
}
```

### 3. Rollover Detection Algorithm

The detection algorithm uses multiple criteria to identify potential rollovers:

1. **Time Difference Check**: If `|deviceTime - serverTime| > 8 years`
2. **Characteristic Range Check**: If deviceTime falls in 2005-2007 range
3. **Validity Check**: Only process if `position.getValid() == true`
4. **Configuration Check**: Only process if `time.rollover.auto == true`

### 4. Correction Calculation Algorithm

The correction algorithm applies GPS week rollover mathematics:

```java
private Date calculateRolloverCorrection(Date deviceTime, Date serverTime, long deviceId) {
    long stepWeeks = AttributeUtil.lookup(cacheManager, Keys.TIME_ROLLOVER_STEP_WEEKS, deviceId);
    int maxMultiple = AttributeUtil.lookup(cacheManager, Keys.TIME_ROLLOVER_MAX_MULTIPLE, deviceId);
    int windowDays = AttributeUtil.lookup(cacheManager, Keys.TIME_ROLLOVER_WINDOW_DAYS, deviceId);
    
    long stepSeconds = stepWeeks * SECONDS_PER_WEEK;
    long deviceTimeMs = deviceTime.getTime();
    long serverTimeMs = serverTime.getTime();
    
    Date bestCorrection = null;
    long bestDifference = Long.MAX_VALUE;
    int bestK = 0;
    
    // Try correction factors k = 1, 2, ..., maxMultiple
    for (int k = 1; k <= maxMultiple; k++) {
        long correctedTimeMs = deviceTimeMs + (k * stepSeconds * 1000);
        Date correctedTime = new Date(correctedTimeMs);
        
        if (isTimestampInValidRange(correctedTime, serverTime, windowDays)) {
            long difference = Math.abs(correctedTimeMs - serverTimeMs);
            if (difference < bestDifference) {
                bestDifference = difference;
                bestCorrection = correctedTime;
                bestK = k;
            }
        }
    }
    
    if (bestCorrection != null) {
        // Add audit attributes
        position.set("rolloverApplied", true);
        position.set("rolloverK", bestK);
        position.set("rolloverDeltaSec", (bestCorrection.getTime() - deviceTime.getTime()) / 1000);
        
        LOGGER.debug("Applied rollover compensation: device={}, original={}, corrected={}, k={}", 
                     deviceId, deviceTime, bestCorrection, bestK);
    }
    
    return bestCorrection;
}
```

## Data Models

### Position Attributes

New attributes added to Position when rollover compensation is applied:

- `rolloverApplied` (Boolean): Indicates rollover compensation was applied
- `rolloverK` (Integer): The correction factor used (1, 2, etc.)
- `rolloverDeltaSec` (Long): Time difference in seconds between original and corrected timestamp

### Configuration Attributes

Device/Group/Global attributes for configuration:

- `time.rollover.auto`: Enable/disable automatic compensation
- `time.rollover.window.days`: Valid time window after correction
- `time.rollover.step.weeks`: GPS week rollover step size
- `time.rollover.max.multiple`: Maximum correction attempts

## Error Handling

### False Positive Prevention

1. **Valid Position Check**: Only process positions with `valid = true`
2. **Range Validation**: Ensure corrected timestamp falls within configured window
3. **Protocol Exclusion**: Skip protocols that already provide correct UTC time
4. **Reasonable Bounds**: Only apply corrections that result in timestamps within ±90 days of server time

### Graceful Degradation

1. **Configuration Disabled**: When `time.rollover.auto = false`, pass through unchanged
2. **No Suitable Correction**: If no correction factor produces valid result, leave original timestamp
3. **Exception Handling**: Catch and log any exceptions, continue with original timestamp

### Logging Strategy

- **DEBUG Level**: Log successful corrections with details
- **WARN Level**: Log when rollover detection triggers but no suitable correction found
- **ERROR Level**: Log configuration errors or unexpected exceptions

## Testing Strategy

### Unit Tests

1. **Rollover Detection Tests**
   - Test detection of 2006 timestamps vs current time
   - Test detection threshold boundaries (8 years)
   - Test characteristic range detection (2005-2007)

2. **Correction Calculation Tests**
   - Test k=1 correction (1024 weeks)
   - Test k=2 correction (2048 weeks)
   - Test selection of best correction factor
   - Test window validation

3. **Configuration Tests**
   - Test attribute inheritance (device → group → global)
   - Test opt-out functionality
   - Test custom configuration values

4. **Edge Case Tests**
   - Test with invalid positions (`valid = false`)
   - Test with already reasonable timestamps
   - Test with extreme timestamp values
   - Test with missing configuration

### Integration Tests

1. **Handler Chain Integration**
   - Test position flows through handler chain correctly
   - Test interaction with TimeHandler
   - Test performance impact on position processing

2. **Protocol-Specific Tests**
   - Test with GT06 protocol decoder output
   - Test with H02 protocol decoder output
   - Test with other protocols (should pass through unchanged)

### Performance Tests

1. **Throughput Testing**
   - Measure position processing rate with/without rollover handler
   - Ensure O(1) performance complexity
   - Test with high-volume position streams

2. **Memory Usage**
   - Verify no memory leaks in handler
   - Test with long-running position processing

## Implementation Phases

### Phase 1: Core Handler Implementation
- Implement `TimestampRolloverHandler` class
- Add configuration keys to `Keys.java`
- Implement rollover detection logic
- Add basic unit tests

### Phase 2: Correction Algorithm
- Implement GPS week rollover mathematics
- Add correction factor selection logic
- Implement position attribute auditing
- Add comprehensive unit tests

### Phase 3: Integration
- Integrate handler into `ProcessingHandler` chain
- Add configuration documentation
- Implement integration tests
- Performance testing and optimization

### Phase 4: Validation and Documentation
- Test with real GT06N device data
- Create configuration examples
- Update ChangeLog
- Final validation and deployment preparation

## Security Considerations

1. **Input Validation**: Validate all timestamp inputs to prevent overflow/underflow
2. **Configuration Bounds**: Enforce reasonable limits on configuration values
3. **Resource Usage**: Ensure handler cannot be used for DoS attacks through excessive processing
4. **Audit Trail**: Maintain clear audit trail of all timestamp modifications

## Compatibility

### Backward Compatibility
- Handler is opt-in by default (`time.rollover.auto = true`)
- No changes to existing APIs or data structures
- Existing configurations remain unchanged

### Protocol Compatibility
- Generic implementation works with any protocol
- No protocol-specific modifications required
- Existing protocol decoders unchanged

### Database Compatibility
- New position attributes use existing attribute storage mechanism
- No database schema changes required
- Existing position data unaffected