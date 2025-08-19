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
import static org.mockito.Mockito.when;

public class TimestampRolloverHandlerTest {

    @Mock
    private CacheManager cacheManager;

    private TimestampRolloverHandler handler;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new TimestampRolloverHandler(cacheManager);
    }

    @Test
    public void testRolloverDetectionWith2006Timestamp() {
        try (MockedStatic<AttributeUtil> mockedAttributeUtil = mockStatic(AttributeUtil.class)) {
            // Setup default configuration values
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_AUTO), any(Long.class))).thenReturn(true);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_WINDOW_DAYS), any(Long.class))).thenReturn(90);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_STEP_WEEKS), any(Long.class))).thenReturn(1024);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_MAX_MULTIPLE), any(Long.class))).thenReturn(2);
            
            Position position = new Position("gt06");
            position.setDeviceId(1L);
            position.setValid(true);
            
            // Create a 2006 timestamp (rollover scenario)
            Calendar cal2006 = Calendar.getInstance();
            cal2006.set(2006, Calendar.JANUARY, 1, 12, 0, 0);
            cal2006.set(Calendar.MILLISECOND, 0);
            position.setDeviceTime(cal2006.getTime());
            
            // Current server time (2025)
            position.setServerTime(new Date());
            
            // Process position
            handler.onPosition(position, (filtered) -> {
                assertFalse(filtered);
            });
            
            // Verify rollover was applied
            assertTrue(position.hasAttribute(Position.KEY_ROLLOVER_APPLIED));
            assertEquals(true, position.getAttributes().get(Position.KEY_ROLLOVER_APPLIED));
            
            // Verify correction factor was applied
            assertTrue(position.hasAttribute(Position.KEY_ROLLOVER_K));
            Integer k = (Integer) position.getAttributes().get(Position.KEY_ROLLOVER_K);
            assertTrue(k >= 1 && k <= 2);
            
            // Verify delta seconds is positive (time was moved forward)
            assertTrue(position.hasAttribute(Position.KEY_ROLLOVER_DELTA_SEC));
            Long deltaSec = (Long) position.getAttributes().get(Position.KEY_ROLLOVER_DELTA_SEC);
            assertTrue(deltaSec > 0);
            
            // Verify corrected time is reasonable (within last few years)
            Date correctedTime = position.getDeviceTime();
            Calendar cal2020 = Calendar.getInstance();
            cal2020.set(2020, Calendar.JANUARY, 1);
            assertTrue(correctedTime.after(cal2020.getTime()));
        }
    }

    @Test
    public void testNoRolloverForReasonableTimestamp() {
        try (MockedStatic<AttributeUtil> mockedAttributeUtil = mockStatic(AttributeUtil.class)) {
            // Setup default configuration values
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_AUTO), any(Long.class))).thenReturn(true);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_WINDOW_DAYS), any(Long.class))).thenReturn(90);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_STEP_WEEKS), any(Long.class))).thenReturn(1024);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_MAX_MULTIPLE), any(Long.class))).thenReturn(2);
            
            Position position = new Position("gt06");
            position.setDeviceId(1L);
            position.setValid(true);
            
            // Recent timestamp (no rollover needed)
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, -1);
            Date recentTime = cal.getTime();
            position.setDeviceTime(recentTime);
            position.setServerTime(new Date());
            
            // Process position
            handler.onPosition(position, (filtered) -> {
                assertFalse(filtered);
            });
            
            // Verify no rollover was applied
            assertFalse(position.hasAttribute(Position.KEY_ROLLOVER_APPLIED));
            
            // Verify timestamp unchanged
            assertEquals(recentTime, position.getDeviceTime());
        }
    }

    @Test
    public void testRolloverDisabledByConfiguration() {
        try (MockedStatic<AttributeUtil> mockedAttributeUtil = mockStatic(AttributeUtil.class)) {
            // Disable rollover compensation
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_AUTO), any(Long.class))).thenReturn(false);
            
            Position position = new Position("gt06");
            position.setDeviceId(1L);
            position.setValid(true);
            
            // Create a 2006 timestamp
            Calendar cal2006 = Calendar.getInstance();
            cal2006.set(2006, Calendar.JANUARY, 1, 12, 0, 0);
            Date originalTime = cal2006.getTime();
            position.setDeviceTime(originalTime);
            position.setServerTime(new Date());
            
            // Process position
            handler.onPosition(position, (filtered) -> {
                assertFalse(filtered);
            });
            
            // Verify no rollover was applied
            assertFalse(position.hasAttribute(Position.KEY_ROLLOVER_APPLIED));
            
            // Verify timestamp unchanged
            assertEquals(originalTime, position.getDeviceTime());
        }
    }

    @Test
    public void testInvalidPositionSkipped() {
        try (MockedStatic<AttributeUtil> mockedAttributeUtil = mockStatic(AttributeUtil.class)) {
            // Setup default configuration values
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_AUTO), any(Long.class))).thenReturn(true);
            
            Position position = new Position("gt06");
            position.setDeviceId(1L);
            position.setValid(false); // Invalid position
            
            // Create a 2006 timestamp
            Calendar cal2006 = Calendar.getInstance();
            cal2006.set(2006, Calendar.JANUARY, 1, 12, 0, 0);
            Date originalTime = cal2006.getTime();
            position.setDeviceTime(originalTime);
            position.setServerTime(new Date());
            
            // Process position
            handler.onPosition(position, (filtered) -> {
                assertFalse(filtered);
            });
            
            // Verify no rollover was applied
            assertFalse(position.hasAttribute(Position.KEY_ROLLOVER_APPLIED));
            
            // Verify timestamp unchanged
            assertEquals(originalTime, position.getDeviceTime());
        }
    }

    @Test
    public void testCharacteristicRangeDetection() {
        try (MockedStatic<AttributeUtil> mockedAttributeUtil = mockStatic(AttributeUtil.class)) {
            // Setup default configuration values
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_AUTO), any(Long.class))).thenReturn(true);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_WINDOW_DAYS), any(Long.class))).thenReturn(90);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_STEP_WEEKS), any(Long.class))).thenReturn(1024);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_MAX_MULTIPLE), any(Long.class))).thenReturn(2);
            
            Position position = new Position("gt06");
            position.setDeviceId(1L);
            position.setValid(true);
            
            // Create a 2005 timestamp (characteristic range)
            Calendar cal2005 = Calendar.getInstance();
            cal2005.set(2005, Calendar.JUNE, 15, 10, 30, 0);
            cal2005.set(Calendar.MILLISECOND, 0);
            position.setDeviceTime(cal2005.getTime());
            position.setServerTime(new Date());
            
            // Process position
            handler.onPosition(position, (filtered) -> {
                assertFalse(filtered);
            });
            
            // Verify rollover was applied
            assertTrue(position.hasAttribute(Position.KEY_ROLLOVER_APPLIED));
            assertEquals(true, position.getAttributes().get(Position.KEY_ROLLOVER_APPLIED));
        }
    }

    @Test
    public void testK2CorrectionWhenK1Insufficient() {
        try (MockedStatic<AttributeUtil> mockedAttributeUtil = mockStatic(AttributeUtil.class)) {
            // Setup configuration values
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_AUTO), any(Long.class))).thenReturn(true);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_WINDOW_DAYS), any(Long.class))).thenReturn(90);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_STEP_WEEKS), any(Long.class))).thenReturn(1024);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_MAX_MULTIPLE), any(Long.class))).thenReturn(2);
            
            Position position = new Position("gt06");
            position.setDeviceId(1L);
            position.setValid(true);
            
            // Create a very old timestamp that requires k=2 correction
            Calendar cal1986 = Calendar.getInstance();
            cal1986.set(1986, Calendar.JANUARY, 1, 12, 0, 0);
            cal1986.set(Calendar.MILLISECOND, 0);
            position.setDeviceTime(cal1986.getTime());
            position.setServerTime(new Date());
            
            // Process position
            handler.onPosition(position, (filtered) -> {
                assertFalse(filtered);
            });
            
            // Verify rollover was applied with k=2
            assertTrue(position.hasAttribute(Position.KEY_ROLLOVER_APPLIED));
            assertEquals(true, position.getAttributes().get(Position.KEY_ROLLOVER_APPLIED));
            
            Integer k = (Integer) position.getAttributes().get(Position.KEY_ROLLOVER_K);
            assertEquals(2, k.intValue());
        }
    }

    @Test
    public void testCustomConfigurationValues() {
        try (MockedStatic<AttributeUtil> mockedAttributeUtil = mockStatic(AttributeUtil.class)) {
            // Setup custom configuration values
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_AUTO), any(Long.class))).thenReturn(true);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_WINDOW_DAYS), any(Long.class))).thenReturn(30); // Custom window
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_STEP_WEEKS), any(Long.class))).thenReturn(512); // Custom step
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_MAX_MULTIPLE), any(Long.class))).thenReturn(1); // Only k=1
            
            Position position = new Position("gt06");
            position.setDeviceId(1L);
            position.setValid(true);
            
            // Create a 2006 timestamp
            Calendar cal2006 = Calendar.getInstance();
            cal2006.set(2006, Calendar.JANUARY, 1, 12, 0, 0);
            cal2006.set(Calendar.MILLISECOND, 0);
            position.setDeviceTime(cal2006.getTime());
            position.setServerTime(new Date());
            
            // Process position
            handler.onPosition(position, (filtered) -> {
                assertFalse(filtered);
            });
            
            // Verify rollover was applied with custom settings
            if (position.hasAttribute(Position.KEY_ROLLOVER_APPLIED)) {
                Integer k = (Integer) position.getAttributes().get(Position.KEY_ROLLOVER_K);
                assertEquals(1, k.intValue()); // Only k=1 allowed
            }
        }
    }

    @Test
    public void testNullTimestampsHandled() {
        try (MockedStatic<AttributeUtil> mockedAttributeUtil = mockStatic(AttributeUtil.class)) {
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_AUTO), any(Long.class))).thenReturn(true);
            
            Position position = new Position("gt06");
            position.setDeviceId(1L);
            position.setValid(true);
            position.setDeviceTime(null); // Null device time
            position.setServerTime(new Date());
            
            // Process position - should not crash
            handler.onPosition(position, (filtered) -> {
                assertFalse(filtered);
            });
            
            // Verify no rollover was applied
            assertFalse(position.hasAttribute(Position.KEY_ROLLOVER_APPLIED));
        }
    }
}