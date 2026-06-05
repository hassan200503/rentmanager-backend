package com.rentmanager.shared.security.throttle;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimpleRateLimiter {

    private final Map<String, Integer> requests = new ConcurrentHashMap<>();
    private static final int LIMIT = 100;

    public boolean allow(String key) {

        requests.putIfAbsent(key, 0);

        int count = requests.get(key);

        if (count >= LIMIT) {
            return false;
        }

        requests.put(key, count + 1);
        return true;
    }

    public void reset(String key) {
        requests.remove(key);
    }
}