package com.rentmanager.crossmodule.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * SaaS-grade event capture utility for cross-module testing.
 *
 * Guarantees:
 * - thread-safe event storage
 * - deterministic test isolation
 * - safe reuse in Spring context cache
 * - reliable event validation across modules
 */
public class EventCapture {

    /**
     * CopyOnWriteArrayList ensures:
     * - safe concurrent writes
     * - no corruption under parallel tests
     * - read consistency during assertions
     */
    private final List<Object> events = new CopyOnWriteArrayList<>();

    /**
     * Records a domain event emitted during test execution.
     */
    public void record(Object event) {
        if (event == null) {
            return;
        }
        events.add(event);
    }

    /**
     * Returns immutable snapshot of captured events.
     * Prevents external mutation of internal state.
     */
    public List<Object> getAll() {
        return List.copyOf(events);
    }

    /**
     * Clears all recorded events (test isolation reset).
     */
    public void clear() {
        events.clear();
    }

    /**
     * Checks if any event of given type was recorded.
     */
    public boolean contains(Class<?> eventType) {
        if (eventType == null) {
            return false;
        }

        return events.stream()
                .anyMatch(e -> e != null && e.getClass().equals(eventType));
    }

    /**
     * Retrieves all events of a specific type.
     * Useful for cross-module validation assertions.
     */
    public <T> List<T> getEventsOfType(Class<T> eventType) {
        return events.stream()
                .filter(eventType::isInstance)
                .map(eventType::cast)
                .collect(Collectors.toList());
    }

    /**
     * Returns event count (useful for invariants in tests).
     */
    public int size() {
        return events.size();
    }

    /**
     * Hard reset (forces full isolation between tests).
     */
    public void reset() {
        events.clear();
    }
}