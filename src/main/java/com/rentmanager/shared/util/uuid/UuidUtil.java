package com.rentmanager.shared.util.uuid;

import java.util.UUID;

public final class UuidUtil {

    private UuidUtil() {
    }

    public static UUID generate() {
        return UUID.randomUUID();
    }
}