package com.rentmanager.crossmodule.core;

import org.springframework.stereotype.Component;

import java.util.*;

@Component

public class ScenarioContext {

    private final Map<String, Object> store = new HashMap<>();

    public <T> void put(String key, T value) {
        store.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) store.get(key);
    }

    public void clear() {
        store.clear();
    }
}