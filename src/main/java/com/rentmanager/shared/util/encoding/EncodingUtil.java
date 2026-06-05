package com.rentmanager.shared.util.encoding;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class EncodingUtil {

    private EncodingUtil() {
    }

    public static String urlEncode(String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    public static String urlDecode(String value) {
        return URLDecoder.decode(
                value,
                StandardCharsets.UTF_8
        );
    }
}