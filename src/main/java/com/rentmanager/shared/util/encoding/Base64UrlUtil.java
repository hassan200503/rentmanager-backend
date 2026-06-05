package com.rentmanager.shared.util.encoding;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Base64UrlUtil {

    private Base64UrlUtil() {}

    public static String encode(String value) {
        if (value == null) return null;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String value) {
        if (value == null) return null;
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}