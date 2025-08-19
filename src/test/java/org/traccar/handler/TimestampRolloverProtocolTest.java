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

/**
 * Tests timestamp rollover compensation with protocol-specific scenarios
 */
public class TimestampRolloverProtocolTest {

    @Mock
    private CacheManager cacheManager;

    private TimestampRolloverHandler handler;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new TimestampRolloverHandler(cacheManager);
    }

    @Test
    public void testGT06ProtocolRollover() {
        try (MockedStatic<AttributeUtil> mockedAttributeUtil = mockStatic(AttributeUtil.class)) {
            // Setup configuration
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_AUTO), any(Long.class))).thenReturn(true);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_WINDOW_DAYS), any(Long.class))).thenReturn(90);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_STEP_WEEKS), any(Long.class))).thenReturn(1024);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_MAX_MULTIPLE), any(Long.class))).thenReturn(2);
            
            // Simulate GT06 position with 2006 timestamp
            Position position = new Position("gt06");
            position.setDeviceId(123L);
            position.setValid(true);
            
            // GT06N typical rollover timestamp
            Calendar cal2006 = Calendar.getInstance();
            cal2006.set(2006, Calendar.JANUARY, 6, 0, 0, 0); // GPS epoch rollover date
            cal2006.set(Calendar.MILLISECOND, 0);
            Date originalTime = cal2006.getTime();
            position.setDeviceTime(originalTime);
            
            // Current server time
            position.setServerTime(new Date());
            
            // Add typical GT06 attributes
            position.set(Position.KEY_SATELLITES, 8);
            position.set(Position.KEY_RSSI, 25);
            position.setLatitude(40.7128);
            position.setLongitude(-74.0060);
            position.setSpeed(0.0);
            position.setCourse(0.0);
            
            // Process position
            handler.onPosition(position, (filtered) -> {
                assertFalse(filtered);
            });
            
            // Verify rollover was applied
            assertTrue(position.hasAttribute(Position.KEY_ROLLOVER_APPLIED));
            assertEquals(true, position.getAttributes().get(Position.KEY_ROLLOVER_APPLIED));
            
            // Verify correction details
            Integer k = (Integer) position.getAttributes().get(Position.KEY_ROLLOVER_K);
            assertNotNull(k);
            assertTrue(k >= 1 && k <= 2);
            
            Long deltaSec = (Long) position.getAttributes().get(Position.KEY_ROLLOVER_DELTA_SEC);
            assertNotNull(deltaSec);
            assertTrue(deltaSec > 0);
            
            // Verify corrected time is reasonable (should be in 2024-2025 range)
            Date correctedTime = position.getDeviceTime();
            Calendar cal2024 = Calendar.getInstance();
            cal2024.set(2024, Calendar.JANUARY, 1);
            assertTrue(correctedTime.after(cal2024.getTime()));
            
            // Verify other attributes were preserved
            assertEquals(8, position.getAttributes().get(Position.KEY_SATELLITES));
            assertEquals(25, position.getAttributes().get(Position.KEY_RSSI));
            assertEquals(40.7128, position.getLatitude(), 0.0001);
            assertEquals(-74.0060, position.getLongitude(), 0.0001);
        }
    }

    @Test
    public void testH02ProtocolRollover() {
        try (MockedStatic<AttributeUtil> mockedAttributeUtil = mockStatic(AttributeUtil.class)) {
            // Setup configuration
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_AUTO), any(Long.class))).thenReturn(true);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_WINDOW_DAYS), any(Long.class))).thenReturn(90);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_STEP_WEEKS), any(Long.class))).thenReturn(1024);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_MAX_MULTIPLE), any(Long.class))).thenReturn(2);
            
            // Simulate H02 position with 2006 timestamp
            Position position = new Position("h02");
            position.setDeviceId(456L);
            position.setValid(true);
            
            // H02 typical rollover timestamp
            Calendar cal2006 = Calendar.getInstance();
            cal2006.set(2006, Calendar.MARCH, 15, 14, 30, 45);
            cal2006.set(Calendar.MILLISECOND, 0);
            Date originalTime = cal2006.getTime();
            position.setDeviceTime(originalTime);
            
            // Current server time
            position.setServerTime(new Date());
            
            // Add typical H02 attributes
            position.set(Position.KEY_BATTERY_LEVEL, 85);
            position.set(Position.KEY_STATUS, 0x12345678L);
            position.set(Position.KEY_IGNITION, true);
            position.setLatitude(51.5074);
            position.setLongitude(-0.1278);
            position.setSpeed(25.5);
            position.setCourse(180.0);
            
            // Process position
            handler.onPosition(position, (filtered) -> {
                assertFalse(filtered);
            });
            
            // Verify rollover was applied
            assertTrue(position.hasAttribute(Position.KEY_ROLLOVER_APPLIED));
            
            // Verify H02-specific attributes were preserved
            assertEquals(85, position.getAttributes().get(Position.KEY_BATTERY_LEVEL));
            assertEquals(0x12345678L, position.getAttributes().get(Position.KEY_STATUS));
            assertEquals(true, position.getAttributes().get(Position.KEY_IGNITION));
            assertEquals(51.5074, position.getLatitude(), 0.0001);
            assertEquals(-0.1278, position.getLongitude(), 0.0001);
            assertEquals(25.5, position.getSpeed(), 0.1);
            assertEquals(180.0, position.getCourse(), 0.1);
        }
    }

    @Test
    public void testOtherProtocolsUnaffected() {
        try (MockedStatic<AttributeUtil> mockedAttributeUtil = mockStatic(AttributeUtil.class)) {
            // Setup configuration
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_AUTO), any(Long.class))).thenReturn(true);
            
            // Test various protocols with normal timestamps
            String[] protocols = {"osmand", "teltonika", "meitrack", "suntech", "gps103"};
            
            for (String protocol : protocols) {
                Position position = new Position(protocol);
                position.setDeviceId(789L);
                position.setValid(true);
                
                // Normal, recent timestamp
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.MINUTE, -10);
                Date normalTime = cal.getTime();
                position.setDeviceTime(normalTime);
                position.setServerTime(new Date());
                
                // Process position
                handler.onPosition(position, (filtered) -> {
                    assertFalse(filtered);
                });
                
                // Verify no rollover was applied
                assertFalse(position.hasAttribute(Position.KEY_ROLLOVER_APPLIED), 
                           "Protocol " + protocol + " should not have rollover applied");
                assertEquals(normalTime, position.getDeviceTime(), 
                           "Protocol " + protocol + " timestamp should be unchanged");
            }
        }
    }

    @Test
    public void testMultipleRolloverScenarios() {
        try (MockedStatic<AttributeUtil> mockedAttributeUtil = mockStatic(AttributeUtil.class)) {
            // Setup configuration
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_AUTO), any(Long.class))).thenReturn(true);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_WINDOW_DAYS), any(Long.class))).thenReturn(90);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_STEP_WEEKS), any(Long.class))).thenReturn(1024);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_MAX_MULTIPLE), any(Long.class))).thenReturn(2);
            
            // Test different rollover timestamps
            int[][] rolloverDates = {
                {2005, Calendar.DECEMBER, 31}, // End of 2005
                {2006, Calendar.JANUARY, 1},   // Start of 2006
                {2006, Calendar.JUNE, 15},     // Mid 2006
                {2006, Calendar.DECEMBER, 31}, // End of 2006
                {2007, Calendar.JANUARY, 1}    // Start of 2007
            };
            
            for (int i = 0; i < rolloverDates.length; i++) {
                Position position = new Position("gt06");
                position.setDeviceId((long) (1000 + i));
                position.setValid(true);
                
                Calendar cal = Calendar.getInstance();
                cal.set(rolloverDates[i][0], rolloverDates[i][1], rolloverDates[i][2], 12, 0, 0);
                cal.set(Calendar.MILLISECOND, 0);
                position.setDeviceTime(cal.getTime());
                position.setServerTime(new Date());
                
                // Process position
                handler.onPosition(position, (filtered) -> {
                    assertFalse(filtered);
                });
                
                // All should be detected and corrected
                assertTrue(position.hasAttribute(Position.KEY_ROLLOVER_APPLIED), 
                          "Date " + rolloverDates[i][0] + " should trigger rollover");
            }
        }
    }

    @Test
    public void testHighVolumeProcessing() {
        try (MockedStatic<AttributeUtil> mockedAttributeUtil = mockStatic(AttributeUtil.class)) {
            // Setup configuration
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_AUTO), any(Long.class))).thenReturn(true);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_WINDOW_DAYS), any(Long.class))).thenReturn(90);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_STEP_WEEKS), any(Long.class))).thenReturn(1024);
            mockedAttributeUtil.when(() -> AttributeUtil.lookup(eq(cacheManager), eq(Keys.TIME_ROLLOVER_MAX_MULTIPLE), any(Long.class))).thenReturn(2);
            
            long startTime = System.currentTimeMillis();
            int rolloverCount = 0;
            int normalCount = 0;
            
            // Process 10,000 positions (mix of normal and rollover)
            for (int i = 0; i < 10000; i++) {
                Position position = new Position("gt06");
                position.setDeviceId((long) i);
                position.setValid(true);
                
                Calendar cal = Calendar.getInstance();
                if (i % 100 == 0) {
                    // 1% rollover cases
                    cal.set(2006, Calendar.JANUARY, 1, 12, 0, 0);
                } else {
                    // 99% normal cases
                    cal.add(Calendar.MINUTE, -i % 1440); // Spread over last day
                }
                
                position.setDeviceTime(cal.getTime());
                position.setServerTime(new Date());
                
                handler.onPosition(position, (filtered) -> {
                    assertFalse(filtered);
                });
                
                if (position.hasAttribute(Position.KEY_ROLLOVER_APPLIED)) {
                    rolloverCount++;
                } else {
                    normalCount++;
                }
            }
            
            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            // Verify performance (should process 10k positions in reasonable time)
            assertTrue(processingTime < 5000, "Processing took too long: " + processingTime + "ms");
            
            // Verify correct detection rates
            assertEquals(100, rolloverCount, "Should detect 100 rollover cases");
            assertEquals(9900, normalCount, "Should have 9900 normal cases");
            
            System.out.println("Processed 10,000 positions in " + processingTime + "ms");
            System.out.println("Rollover corrections: " + rolloverCount);
            System.out.println("Normal positions: " + normalCount);
        }
    }
}