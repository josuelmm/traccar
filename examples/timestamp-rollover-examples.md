# Timestamp Rollover Compensation Examples

## Real Payload Examples

### GT06N Device with Rollover Issue

#### Before Compensation (Raw Device Data)
```json
{
  "deviceId": 123456,
  "protocol": "gt06",
  "serverTime": "2025-01-18T15:30:00.000Z",
  "deviceTime": "2006-01-06T00:00:00.000Z",
  "fixTime": "2006-01-06T00:00:00.000Z",
  "valid": true,
  "latitude": 40.7128,
  "longitude": -74.0060,
  "altitude": 10.0,
  "speed": 0.0,
  "course": 0.0,
  "accuracy": 5.0,
  "attributes": {
    "sat": 8,
    "rssi": 25,
    "ignition": true,
    "power": 12.5,
    "battery": 4.1
  }
}
```

#### After Compensation (Corrected)
```json
{
  "deviceId": 123456,
  "protocol": "gt06",
  "serverTime": "2025-01-18T15:30:00.000Z",
  "deviceTime": "2025-01-18T15:29:45.000Z",
  "fixTime": "2025-01-18T15:29:45.000Z",
  "valid": true,
  "latitude": 40.7128,
  "longitude": -74.0060,
  "altitude": 10.0,
  "speed": 0.0,
  "course": 0.0,
  "accuracy": 5.0,
  "attributes": {
    "sat": 8,
    "rssi": 25,
    "ignition": true,
    "power": 12.5,
    "battery": 4.1,
    "rolloverApplied": true,
    "rolloverK": 1,
    "rolloverDeltaSec": 600000000
  }
}
```

### H02 Device with Rollover Issue

#### Before Compensation
```json
{
  "deviceId": 789012,
  "protocol": "h02",
  "serverTime": "2025-01-18T10:15:30.000Z",
  "deviceTime": "2006-03-15T14:30:45.000Z",
  "fixTime": "2006-03-15T14:30:45.000Z",
  "valid": true,
  "latitude": 51.5074,
  "longitude": -0.1278,
  "altitude": 25.0,
  "speed": 45.2,
  "course": 180.0,
  "attributes": {
    "batteryLevel": 85,
    "status": 305419896,
    "ignition": true,
    "odometer": 125000
  }
}
```

#### After Compensation
```json
{
  "deviceId": 789012,
  "protocol": "h02",
  "serverTime": "2025-01-18T10:15:30.000Z",
  "deviceTime": "2025-01-18T10:15:15.000Z",
  "fixTime": "2025-01-18T10:15:15.000Z",
  "valid": true,
  "latitude": 51.5074,
  "longitude": -0.1278,
  "altitude": 25.0,
  "speed": 45.2,
  "course": 180.0,
  "attributes": {
    "batteryLevel": 85,
    "status": 305419896,
    "ignition": true,
    "odometer": 125000,
    "rolloverApplied": true,
    "rolloverK": 1,
    "rolloverDeltaSec": 599999970
  }
}
```

## Log Examples

### Successful Rollover Correction
```
2025-01-18 15:30:00.123 DEBUG TimestampRolloverHandler - Applied rollover compensation: device=123456, original=2006-01-06 00:00:00, corrected=2025-01-18 15:29:45, k=1, delta=600000000s
```

### Rollover Detection Without Suitable Correction
```
2025-01-18 15:30:00.456 WARN  TimestampRolloverHandler - Rollover detected but no suitable correction found: device=123456, deviceTime=2006-01-06 00:00:00, serverTime=2025-01-18 15:30:00
```

### Configuration Disabled
```
2025-01-18 15:30:00.789 DEBUG TimestampRolloverHandler - Rollover compensation disabled for device=123456
```

## Database Storage Examples

### Position Table (Before)
```sql
INSERT INTO tc_positions (deviceid, servertime, devicetime, fixtime, valid, latitude, longitude, ...)
VALUES (123456, '2025-01-18 15:30:00', '2006-01-06 00:00:00', '2006-01-06 00:00:00', 1, 40.7128, -74.0060, ...);
```

### Position Table (After)
```sql
INSERT INTO tc_positions (deviceid, servertime, devicetime, fixtime, valid, latitude, longitude, attributes, ...)
VALUES (123456, '2025-01-18 15:30:00', '2025-01-18 15:29:45', '2025-01-18 15:29:45', 1, 40.7128, -74.0060, 
        '{"rolloverApplied":true,"rolloverK":1,"rolloverDeltaSec":600000000,"sat":8,"rssi":25}', ...);
```

## Configuration Examples

### Traccar.xml Configuration
```xml
<?xml version='1.0' encoding='UTF-8'?>
<!DOCTYPE properties SYSTEM 'http://java.sun.com/dtd/properties.dtd'>
<properties>
    <!-- Enable rollover compensation globally -->
    <entry key='time.rollover.auto'>true</entry>
    
    <!-- Conservative 30-day window -->
    <entry key='time.rollover.window.days'>30</entry>
    
    <!-- Standard GPS week rollover period -->
    <entry key='time.rollover.step.weeks'>1024</entry>
    
    <!-- Try up to 2 correction factors -->
    <entry key='time.rollover.max.multiple'>2</entry>
    
    <!-- Other configuration... -->
    <entry key='database.driver'>org.h2.Driver</entry>
    <entry key='database.url'>jdbc:h2:./data/database</entry>
</properties>
```

### Device-Specific Configuration (Web Interface)
```
Device ID: 123456
Attributes:
- time.rollover.auto: true
- time.rollover.window.days: 90
- time.rollover.step.weeks: 1024
- time.rollover.max.multiple: 2
```

### Group Configuration (Web Interface)
```
Group: GT06N Devices
Attributes:
- time.rollover.auto: true
- time.rollover.window.days: 60
- time.rollover.step.weeks: 1024
- time.rollover.max.multiple: 1
```

## API Response Examples

### REST API Position Response (Before)
```json
{
  "id": 12345,
  "deviceId": 123456,
  "protocol": "gt06",
  "serverTime": "2025-01-18T15:30:00.000+00:00",
  "deviceTime": "2006-01-06T00:00:00.000+00:00",
  "fixTime": "2006-01-06T00:00:00.000+00:00",
  "outdated": false,
  "valid": true,
  "latitude": 40.7128,
  "longitude": -74.0060,
  "altitude": 10.0,
  "speed": 0.0,
  "course": 0.0,
  "address": "New York, NY, USA",
  "accuracy": 5.0,
  "network": null,
  "attributes": {
    "sat": 8,
    "rssi": 25,
    "ignition": true
  }
}
```

### REST API Position Response (After)
```json
{
  "id": 12345,
  "deviceId": 123456,
  "protocol": "gt06",
  "serverTime": "2025-01-18T15:30:00.000+00:00",
  "deviceTime": "2025-01-18T15:29:45.000+00:00",
  "fixTime": "2025-01-18T15:29:45.000+00:00",
  "outdated": false,
  "valid": true,
  "latitude": 40.7128,
  "longitude": -74.0060,
  "altitude": 10.0,
  "speed": 0.0,
  "course": 0.0,
  "address": "New York, NY, USA",
  "accuracy": 5.0,
  "network": null,
  "attributes": {
    "sat": 8,
    "rssi": 25,
    "ignition": true,
    "rolloverApplied": true,
    "rolloverK": 1,
    "rolloverDeltaSec": 600000000
  }
}
```

## Troubleshooting Examples

### Check if Rollover is Being Applied
```sql
-- Query positions with rollover corrections
SELECT deviceid, devicetime, servertime, attributes
FROM tc_positions 
WHERE attributes LIKE '%rolloverApplied%'
ORDER BY servertime DESC
LIMIT 10;
```

### Monitor Rollover Statistics
```sql
-- Count rollover corrections by device
SELECT deviceid, 
       COUNT(*) as total_positions,
       SUM(CASE WHEN attributes LIKE '%rolloverApplied%' THEN 1 ELSE 0 END) as rollover_corrections
FROM tc_positions 
WHERE servertime > DATE_SUB(NOW(), INTERVAL 1 DAY)
GROUP BY deviceid
HAVING rollover_corrections > 0;
```

### Verify Configuration
```sql
-- Check device-level rollover configuration
SELECT d.name, d.uniqueid, da.attribute, da.value
FROM tc_devices d
LEFT JOIN tc_device_attributes da ON d.id = da.deviceid
WHERE da.attribute LIKE 'time.rollover%'
ORDER BY d.name;
```