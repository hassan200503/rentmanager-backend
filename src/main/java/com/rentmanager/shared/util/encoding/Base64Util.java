package com.rentmanager.shared.util.encoding;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Base64Util {

    private Base64Util() {
    }

    public static String encode(String value) {
        return Base64.getEncoder()
                .encodeToString(
                        value.getBytes(StandardCharsets.UTF_8)
                );
    }

    public static String decode(String value) {
        return new String(
                Base64.getDecoder().decode(value),
                StandardCharsets.UTF_8
        );
    }
}