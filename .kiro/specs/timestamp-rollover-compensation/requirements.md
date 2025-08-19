# Requirements Document

## Introduction

This feature implements automatic timestamp rollover compensation for GPS tracking devices (specifically GT06N with MTK2503D processor) that experience internal counter rollovers, causing them to report incorrect timestamps (typically resetting to 2006). The solution must be generic, configurable, and maintain compatibility with existing Traccar functionality without breaking current workflows.

## Requirements

### Requirement 1

**User Story:** As a Traccar administrator, I want the system to automatically detect and correct timestamp rollovers from GPS devices, so that position data is stored with accurate timestamps without manual intervention.

#### Acceptance Criteria

1. WHEN a position timestamp differs from receiveTime by more than 8 years THEN the system SHALL detect this as a potential rollover
2. WHEN a position timestamp falls within characteristic rollover ranges (2005-2007) THEN the system SHALL evaluate it for rollover correction
3. WHEN rollover is detected THEN the system SHALL calculate correction using multiples of 1024 weeks (≈ 619315200 seconds)
4. WHEN multiple correction factors (k=1, k=2) are possible THEN the system SHALL choose the value that places the timestamp closest to receiveTime within a reasonable window (±90 days)
5. WHEN rollover correction is applied THEN the system SHALL log the adjustment at DEBUG level

### Requirement 2

**User Story:** As a Traccar administrator, I want to configure rollover compensation behavior at global, group, and device levels, so that I can control when and how the compensation is applied.

#### Acceptance Criteria

1. WHEN configuring rollover compensation THEN the system SHALL support `time.rollover.auto` attribute with default value `true`
2. WHEN configuring rollover parameters THEN the system SHALL support `time.rollover.window.days` attribute with default value `90`
3. WHEN configuring rollover parameters THEN the system SHALL support `time.rollover.step.weeks` attribute with default value `1024`
4. WHEN configuring rollover parameters THEN the system SHALL support `time.rollover.max.multiple` attribute with default value `2`
5. WHEN `time.rollover.auto` is set to `false` THEN the system SHALL NOT apply any rollover compensation
6. WHEN configuration is inherited THEN the system SHALL follow Traccar's standard attribute inheritance pattern (device > group > global)

### Requirement 3

**User Story:** As a Traccar developer, I want rollover compensation to avoid false positives and maintain data integrity, so that valid timestamps are never incorrectly modified.

#### Acceptance Criteria

1. WHEN a position timestamp is already within reasonable range THEN the system SHALL NOT apply rollover compensation
2. WHEN a position has `valid = false` THEN the system SHALL NOT apply rollover compensation
3. WHEN a protocol already provides correct UTC timestamps THEN the system SHALL NOT apply rollover compensation
4. WHEN rollover compensation would result in a timestamp outside the configured window THEN the system SHALL NOT apply the compensation
5. WHEN no suitable correction factor can be found THEN the system SHALL leave the original timestamp unchanged

### Requirement 4

**User Story:** As a Traccar developer, I want rollover compensation implemented in a centralized handler, so that the logic is not duplicated across multiple protocol decoders.

#### Acceptance Criteria

1. WHEN implementing rollover compensation THEN the system SHALL use a central handler (TimeHandler or similar)
2. WHEN processing positions THEN the rollover compensation SHALL be applied before final position storage
3. WHEN rollover compensation is applied THEN the implementation SHALL have O(1) performance complexity
4. WHEN rollover compensation runs THEN it SHALL NOT require additional I/O operations
5. WHEN rollover compensation is implemented THEN it SHALL follow existing Traccar patterns (AttributeUtil.lookup, Keys.java)

### Requirement 5

**User Story:** As a Traccar user, I want to see when rollover compensation has been applied to position data, so that I can understand and audit the timestamp corrections.

#### Acceptance Criteria

1. WHEN rollover compensation is applied THEN the system SHALL add `rolloverApplied = true` to Position.attributes
2. WHEN rollover compensation is applied THEN the system SHALL add `rolloverK` with the correction factor used to Position.attributes
3. WHEN rollover compensation is applied THEN the system SHALL add `rolloverDeltaSec` with the time difference in seconds to Position.attributes
4. WHEN rollover compensation is applied THEN the system SHALL log the correction with original and corrected timestamps at DEBUG level

### Requirement 6

**User Story:** As a Traccar administrator, I want comprehensive testing to ensure rollover compensation works correctly, so that I can trust the feature in production environments.

#### Acceptance Criteria

1. WHEN testing GT06/H02 devices with 2006 timestamps THEN the system SHALL correct them to current year (~2025)
2. WHEN testing with reasonable timestamps THEN the system SHALL NOT apply any correction
3. WHEN testing edge cases THEN the system SHALL correctly handle k=2 corrections when k=1 is insufficient
4. WHEN testing configuration inheritance THEN the system SHALL properly inherit attributes from device > group > global
5. WHEN testing opt-out functionality THEN the system SHALL respect `time.rollover.auto = false` setting
6. WHEN performance testing THEN the rollover compensation SHALL not significantly impact position processing speed

### Requirement 7

**User Story:** As a Traccar administrator, I want proper documentation for the rollover compensation feature, so that I can configure and use it effectively.

#### Acceptance Criteria

1. WHEN documentation is provided THEN it SHALL include all new configuration keys and their default values
2. WHEN documentation is provided THEN it SHALL include examples of how to enable/disable the feature
3. WHEN documentation is provided THEN it SHALL include examples of configuration at different levels (global, group, device)
4. WHEN documentation is provided THEN it SHALL be added to the ChangeLog with version information
5. WHEN documentation is provided THEN it SHALL reference GitHub Issue #5634 and PR #5635