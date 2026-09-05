package com.rentmanager.modules.integration.bridge;

import com.rentmanager.modules.integration.application.provider.DarajaProviderTester;
import com.rentmanager.modules.integration.application.provider.ProviderTester;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Verifies a single landlord's own Daraja credentials against Safaricom.
 *
 * <h2>Why this sits in the bridge</h2>
 * The tenant module owns per-landlord credentials but must not reach into
 * the integration module's application layer to test them. This is the same
 * sanctioned crossing {@link PlatformDarajaCredentialsResolver} already
 * provides, and it keeps {@link DarajaProviderTester} — which is shared with
 * the platform Integrations control plane — as the single implementation of
 * "is this key/secret real". One tester, so the landlord-facing check and the
 * platform-admin check can never disagree about what a valid credential is.
 *
 * <h2>Which environment it tests against</h2>
 * A landlord supplies only Consumer Key, Consumer Secret, Shortcode and
 * Passkey — never a base URL. Sandbox vs production is a deployment-wide
 * fact, resolved here from the platform configuration, exactly as
 * {@code DarajaService.initiateSTKPush} does when it posts to
 * {@code activeDaraja().baseUrl()} with the landlord's shortcode in the body.
 * Testing against any other host would prove something the real payment path
 * never exercises.
 *
 * <h2>What a pass does and does not prove</h2>
 * The OAuth token proves the Consumer Key and Secret are valid for this
 * environment. It does NOT prove the Shortcode and Passkey are right — those
 * are only exercised when an STK push is actually signed, and Safaricom
 * offers no way to check them without charging someone. Callers must say so
 * rather than reporting a green tick as "M-Pesa is fully working".
 */
@Component
@RequiredArgsConstructor
public class LandlordDarajaVerifier {

    private final DarajaProviderTester darajaProviderTester;
    private final PlatformDarajaCredentialsResolver platformResolver;

    /**
     * @return the provider's own verdict, never a paraphrase of it.
     */
    public ProviderTester.TestResult verify(String consumerKey, String consumerSecret) {
        String baseUrl = platformResolver.credentials().baseUrl();
        return darajaProviderTester.test(
                Map.of(
                        "consumer_key", consumerKey == null ? "" : consumerKey,
                        "consumer_secret", consumerSecret == null ? "" : consumerSecret
                ),
                null,
                baseUrl
        );
    }

    /**
     * The environment the verification ran against, so the caller can show
     * the landlord which Safaricom host answered. A sandbox host answering
     * "valid" in production is the failure this makes visible.
     */
    public String environmentBaseUrl() {
        return platformResolver.credentials().baseUrl();
    }
}
