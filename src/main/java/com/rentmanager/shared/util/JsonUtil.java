package com.rentmanager.shared.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class JsonUtil {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static String toJson(Map<String, Object> data) {
        try {
            return mapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}