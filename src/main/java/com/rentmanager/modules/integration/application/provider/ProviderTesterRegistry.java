package com.rentmanager.modules.integration.application.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes a provider key to its real connection tester.
 */
@Component
public class ProviderTesterRegistry {

    private final Map<String, ProviderTester> testers;

    public ProviderTesterRegistry(List<ProviderTester> testers) {
        this.testers = testers.stream()
                .collect(Collectors.toUnmodifiableMap(ProviderTester::providerKey, Function.identity()));
    }

    public ProviderTester get(String providerKey) {
        ProviderTester tester = testers.get(providerKey);
        if (tester == null) {
            throw new IllegalArgumentException("No test connection available for provider: " + providerKey);
        }
        return tester;
    }
}