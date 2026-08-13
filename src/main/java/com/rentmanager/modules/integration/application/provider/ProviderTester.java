package com.rentmanager.modules.integration.application.provider;

import java.util.Map;

/**
 * Real, non-mocked connection test for one provider. Implementations hit
 * the provider's actual sandbox/live endpoints with the saved credentials
 * and report the provider's own response — never a paraphrase.
 */
public interface ProviderTester {

    String providerKey();

    /**
     * @param credentials decrypted, saved credentials for the environment
     *                    under test (may include environment fallbacks)
     * @param target      optional owner-supplied destination (phone number,
     *                    email address) for providers whose test sends real
     *                    traffic; null when the provider supports a
     *                    credential-only check
     * @param baseUrlOverride explicit environment base URL override (null for most)
     */
    TestResult test(Map<String, String> credentials, String target, String baseUrlOverride);

    record TestResult(boolean ok, String message, String error) {
        public static TestResult success(String message) {
            return new TestResult(true, message, null);
        }

        public static TestResult failure(String message, String error) {
            return new TestResult(false, message, error);
        }
    }
}