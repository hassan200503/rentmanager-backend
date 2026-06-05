package com.rentmanager.shared.util.time;

import java.time.Instant;

public final class TimeUtil {

    private TimeUtil() {
    }

    public static Instant nowUtc() {
        return Instant.now();
    }
}