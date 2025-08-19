/*
 * Copyright 2025 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.handler;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.config.Keys;
import org.traccar.helper.model.AttributeUtil;
import org.traccar.model.Position;
import org.traccar.session.cache.CacheManager;

import java.util.Date;

public class TimestampRolloverHandler extends BasePositionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimestampRolloverHandler.class);
    
    // GPS week rollover constants
    private static final long SECONDS_PER_WEEK = 7 * 24 * 60 * 60;
    private static final long ROLLOVER_THRESHOLD_YEARS = 8;
    private static final long ROLLOVER_THRESHOLD_MS = ROLLOVER_THRESHOLD_YEARS * 365 * 24 * 60 * 60 * 1000L;
    
    // Characteristic rollover range (2005-2007)
    private static final int CHARACTERISTIC_START_YEAR = 2005;
    private static final int CHARACTERISTIC_END_YEAR = 2007;
    
    private final CacheManager cacheManager;

    @Inject
    public TimestampRolloverHandler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void onPosition(Position position, Callback callback) {
        try {
            if (shouldApplyRolloverCompensation(position)) {
                Date correctedTime = calculateRolloverCorrection(
                    position.getDeviceTime(), 
                    position.getServerTime(), 
                    position.getDeviceId()
                );
                
                if (correctedTime != null) {
                    position.setDeviceTime(correctedTime);
                    position.setFixTime(correctedTime);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error in timestamp rollover compensation for device {}: {}", 
                        position.getDeviceId(), e.getMessage(), e);
        }
        
        callback.processed(false);
    }

    private boolean shouldApplyRolloverCompensation(Position position) {
        // Check if rollover compensation is enabled
        boolean autoEnabled = AttributeUtil.lookup(cacheManager, Keys.TIME_ROLLOVER_AUTO, position.getDeviceId());
        if (!autoEnabled) {
            return false;
        }
        
        // Only process valid positions
        if (!position.getValid()) {
            return false;
        }
        
        // Check if device time and server time are available
        Date deviceTime = position.getDeviceTime();
        Date serverTime = position.getServerTime();
        if (deviceTime == null || serverTime == null) {
            return false;
        }
        
        // Check time difference threshold (8 years)
        long timeDifference = Math.abs(serverTime.getTime() - deviceTime.getTime());
        if (timeDifference > ROLLOVER_THRESHOLD_MS) {
            return true;
        }
        
        // Check characteristic rollover range (2005-2007)
        @SuppressWarnings("deprecation")
        int deviceYear = deviceTime.getYear() + 1900;
        if (deviceYear >= CHARACTERISTIC_START_YEAR && deviceYear <= CHARACTERISTIC_END_YEAR) {
            return true;
        }
        
        return false;
    }

    private Date calculateRolloverCorrection(Date deviceTime, Date serverTime, long deviceId) {
        int stepWeeks = AttributeUtil.lookup(cacheManager, Keys.TIME_ROLLOVER_STEP_WEEKS, deviceId);
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
            position.set(Position.KEY_ROLLOVER_APPLIED, true);
            position.set(Position.KEY_ROLLOVER_K, bestK);
            position.set(Position.KEY_ROLLOVER_DELTA_SEC, (bestCorrection.getTime() - deviceTime.getTime()) / 1000);
            
            LOGGER.debug("Applied rollover compensation: device={}, original={}, corrected={}, k={}, delta={}s", 
                        deviceId, deviceTime, bestCorrection, bestK, 
                        (bestCorrection.getTime() - deviceTime.getTime()) / 1000);
        } else {
            LOGGER.warn("Rollover detected but no suitable correction found: device={}, deviceTime={}, serverTime={}", 
                       deviceId, deviceTime, serverTime);
        }
        
        return bestCorrection;
    }

    private boolean isTimestampInValidRange(Date timestamp, Date reference, int windowDays) {
        long windowMs = windowDays * 24 * 60 * 60 * 1000L;
        long difference = Math.abs(timestamp.getTime() - reference.getTime());
        return difference <= windowMs;
    }
}