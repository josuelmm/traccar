# Implementation Plan

- [x] 1. Set up configuration keys and constants


  - Add new configuration keys to Keys.java following Traccar patterns
  - Define constants for rollover calculations (GPS week seconds, thresholds)
  - Create comprehensive unit tests for configuration key behavior
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 2. Implement core TimestampRolloverHandler class


  - [x] 2.1 Create TimestampRolloverHandler extending BasePositionHandler

    - Implement constructor with CacheManager injection
    - Implement onPosition method with callback pattern
    - Add logging infrastructure and constants
    - _Requirements: 4.1, 4.2, 4.4_

  - [x] 2.2 Implement rollover detection logic

    - Create shouldApplyRolloverCompensation method with all validation checks
    - Implement time difference threshold detection (8 years)
    - Add characteristic range detection (2005-2007)
    - Include valid position and configuration checks
    - _Requirements: 1.1, 1.2, 3.1, 3.2, 3.3_

  - [x] 2.3 Implement correction calculation algorithm

    - Create calculateRolloverCorrection method with GPS week mathematics
    - Implement multiple correction factor testing (k=1, k=2)
    - Add best correction selection logic based on proximity to server time
    - Include window validation for corrected timestamps
    - _Requirements: 1.3, 1.4, 3.4_

  - [x] 2.4 Add position auditing and logging

    - Implement position attribute setting (rolloverApplied, rolloverK, rolloverDeltaSec)
    - Add DEBUG level logging for successful corrections
    - Include WARN logging for detection without suitable correction
    - Add ERROR logging for exceptions and configuration issues
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 1.5_

- [x] 3. Create comprehensive unit test suite


  - [x] 3.1 Test rollover detection functionality

    - Write tests for 2006 timestamp detection vs current time
    - Test 8-year threshold boundary conditions
    - Test characteristic range detection (2005-2007)
    - Verify valid position requirement enforcement
    - _Requirements: 6.1, 3.1, 3.2_

  - [x] 3.2 Test correction calculation accuracy

    - Test k=1 correction (1024 weeks = 619,315,200 seconds)
    - Test k=2 correction (2048 weeks)
    - Verify best correction factor selection logic
    - Test window validation with different day configurations
    - _Requirements: 6.2, 1.3, 1.4_

  - [x] 3.3 Test configuration inheritance and opt-out

    - Test attribute lookup from device → group → global hierarchy
    - Verify time.rollover.auto = false disables compensation
    - Test custom configuration values (window days, step weeks, max multiple)
    - Test default value behavior
    - _Requirements: 6.4, 6.5, 2.6_

  - [x] 3.4 Test edge cases and error handling

    - Test with invalid positions (valid = false)
    - Test with already reasonable timestamps (no correction needed)
    - Test with extreme timestamp values and boundary conditions
    - Test exception handling and graceful degradation
    - _Requirements: 6.3, 3.3, 3.4, 3.5_

- [x] 4. Integrate handler into position processing chain


  - [x] 4.1 Modify ProcessingHandler to include TimestampRolloverHandler

    - Add TimestampRolloverHandler to positionHandlers stream in ProcessingHandler
    - Position handler after ComputedAttributesHandler.Early and before TimeHandler
    - Ensure proper dependency injection configuration
    - _Requirements: 4.1, 4.2_

  - [x] 4.2 Create integration tests for handler chain

    - Test position flows correctly through entire handler chain
    - Verify interaction with TimeHandler doesn't conflict
    - Test performance impact on position processing pipeline
    - Ensure O(1) performance complexity maintained
    - _Requirements: 4.3, 4.4, 6.6_

- [x] 5. Add configuration documentation and examples


  - [x] 5.1 Update configuration documentation

    - Document all new configuration keys with descriptions and defaults
    - Provide examples of global, group, and device-level configuration
    - Include examples of enabling/disabling the feature
    - Add troubleshooting section for common issues
    - _Requirements: 7.1, 7.2, 7.3_

  - [x] 5.2 Create ChangeLog entry

    - Add entry to ChangeLog with version information
    - Reference GitHub Issue #5634 and PR #5635
    - Describe new feature capabilities and configuration options
    - Include migration notes if needed
    - _Requirements: 7.4, 7.5_

- [x] 6. Create comprehensive test scenarios with real data


  - [x] 6.1 Test GT06/H02 protocol integration

    - Create test cases with GT06 devices reporting 2006 timestamps
    - Verify correction to current year (~2025)
    - Test with H02 protocol decoder output
    - Ensure no interference with other protocols
    - _Requirements: 6.1, 6.2_

  - [x] 6.2 Performance and load testing

    - Measure position processing throughput with rollover handler enabled
    - Test with high-volume position streams
    - Verify no memory leaks during long-running operations
    - Ensure handler doesn't significantly impact overall performance
    - _Requirements: 6.6, 4.3, 4.4_

- [x] 7. Final validation and deployment preparation



  - [x] 7.1 End-to-end testing with sample payloads

    - Test complete flow from protocol decoder through rollover handler to database
    - Verify position attributes are correctly stored and retrievable
    - Test with before/after payload examples showing 2006 → 2025 correction
    - Validate audit trail functionality
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 5.1, 5.2, 5.3, 5.4_

  - [x] 7.2 Code review and optimization

    - Review code for adherence to Traccar patterns and conventions
    - Optimize performance-critical paths
    - Ensure proper error handling and logging
    - Validate security considerations and input validation
    - _Requirements: 4.4, 4.5_

  - [x] 7.3 Create deployment documentation

    - Document deployment steps and configuration requirements
    - Create rollback procedures if needed
    - Provide monitoring and troubleshooting guidelines
    - Include examples of successful rollover corrections in logs
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_