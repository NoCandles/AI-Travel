package com.ai.travel.util;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class IDGenerator {

    public static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String generateTripId() {
        return "TRIP-" + System.currentTimeMillis();
    }

    public static String generateDayId() {
        return "DAY-" + System.currentTimeMillis();
    }

    public static String generateSpotId() {
        return "SPOT-" + System.currentTimeMillis();
    }
}
