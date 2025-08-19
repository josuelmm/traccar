package org.traccar.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.traccar.config.Keys;
import org.traccar.helper.model.AttributeUtil;
import org.traccar.model.Position;
import org.traccar.session.cache.CacheManager;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

public class TimestampRolloverIntegrationTest {

    @Mock
    private CacheManager cacheManager;

    private TimestampRolloverHandler rolloverHandler;
    private TimeHandler timeHandler;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        rolloverHandler = new TimestampRolloverHandler(cacheManager);
        timeHandler = new TimeHandler(null); // TimeHandler doesn't need config for this test
    }

    @Test
    public void testHandlerChainIntegration() {
        try (MockedStatic<AttributeUtil> mockedAttributeUtil = mockStatic(AttributeUtil.class)) {
            // Setup configuration
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_AUTO), any(Long.class))).thenReturn(true);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_WINDOW_DAYS), any(Long.class))).thenReturn(90);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_STEP_WEEKS), any(Long.class))).thenReturn(1024);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_MAX_MULTIPLE), any(Long.class))).thenReturn(2);
            
            Position position = new Position("gt06");
            position.setDeviceId(1L);
            position.setValid(true);
            
            // Create a 2006 timestamp
            Calendar cal2006 = Calendar.getInstance();
            cal2006.set(2006, Calendar.JANUARY, 1, 12, 0, 0);
            cal2006.set(Calendar.MILLISECOND, 0);
            Date originalDeviceTime = cal2006.getTime();
            position.setDeviceTime(originalDeviceTime);
            position.setServerTime(new Date());
            
            // First, apply rollover handler
            rolloverHandler.onPosition(position, (filtered) -> {
                assertFalse(filtered);
            });
            
            // Verify rollover was applied
            assertTrue(position.hasAttribute(Position.KEY_ROLLOVER_APPLIED));
            assertNotEquals(originalDeviceTime, position.getDeviceTime());
            
            // Store corrected time for comparison
            Date correctedDeviceTime = position.getDeviceTime();
            Date correctedFixTime = position.getFixTime();
            
            // Then apply TimeHandler (should not interfere)
            timeHandler.onPosition(position, (filtered) -> {
                assertFalse(filtered);
            });
            
            // Verify TimeHandler didn't undo the rollover correction
            assertEquals(correctedDeviceTime, position.getDeviceTime());
            assertEquals(correctedFixTime, position.getFixTime());
            
            // Verify audit attributes are still present
            assertTrue(position.hasAttribute(Position.KEY_ROLLOVER_APPLIED));
            assertTrue(position.hasAttribute(Position.KEY_ROLLOVER_K));
            assertTrue(position.hasAttribute(Position.KEY_ROLLOVER_DELTA_SEC));
        }
    }

    @Test
    public void testPerformanceImpact() {
        try (MockedStatic<AttributeUtil> mockedAttributeUtil = mockStatic(AttributeUtil.class)) {
            // Setup configuration
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_AUTO), any(Long.class))).thenReturn(true);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_WINDOW_DAYS), any(Long.class))).thenReturn(90);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_STEP_WEEKS), any(Long.class))).thenReturn(1024);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_MAX_MULTIPLE), any(Long.class))).thenReturn(2);
            
            // Test processing many positions to ensure performance is acceptable
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < 1000; i++) {
                Position position = new Position("gt06");
                position.setDeviceId((long) i);
                position.setValid(true);
                
                // Mix of normal and rollover timestamps
                Calendar cal = Calendar.getInstance();
                if (i % 10 == 0) {
                    // 10% rollover cases
                    cal.set(2006, Calendar.JANUARY, 1, 12, 0, 0);
                } else {
                    // 90% normal cases
                    cal.add(Calendar.MINUTE, -i);
                }
                
                position.setDeviceTime(cal.getTime());
                position.setServerTime(new Date());
                
                rolloverHandler.onPosition(position, (filtered) -> {
                    assertFalse(filtered);
                });
            }
            
            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            // Should process 1000 positions in reasonable time (less than 1 second)
            assertTrue(processingTime < 1000, "Processing took too long: " + processingTime + "ms");
        }
    }

    @Test
    public void testNoInterferenceWithValidPositions() {
        try (MockedStatic<AttributeUtil> mockedAttributeUtil = mockStatic(AttributeUtil.class)) {
            // Setup configuration
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_AUTO), any(Long.class))).thenReturn(true);
            
            Position position = new Position("gt06");
            position.setDeviceId(1L);
            position.setValid(true);
            
            // Recent, valid timestamp
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, -5);
            Date originalTime = cal.getTime();
            position.setDeviceTime(originalTime);
            position.setServerTime(new Date());
            
            // Process through rollover handler
            rolloverHandler.onPosition(position, (filtered) -> {
                assertFalse(filtered);
            });
            
            // Verify no changes were made
            assertEquals(originalTime, position.getDeviceTime());
            assertFalse(position.hasAttribute(Position.KEY_ROLLOVER_APPLIED));
        }
    }
}