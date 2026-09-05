package com.rentmanager.shared.config;

import com.rentmanager.modules.integration.bridge.PlatformDarajaCredentialsResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Reports, at startup, whether this instance is actually able to move money.
 *
 * <h2>Why this exists</h2>
 * Every Daraja setting has a fallback, and the fallbacks are sandbox values:
 * {@code DARAJA_BASE_URL} defaults to {@code https://sandbox.safaricom.co.ke},
 * {@code DARAJA_SHORT_CODE} to {@code 174379} (Safaricom's public sandbox
 * shortcode), and the key/passkey to literal {@code YOUR_*} placeholders. A
 * deployment that forgets them starts cleanly, reports healthy, and serves
 * traffic — then every rent prompt goes to the sandbox. Nothing in the system
 * said otherwise until a landlord noticed no money had arrived.
 *
 * <h2>Why it warns rather than refusing to start</h2>
 * The prod profile is also the local default (see {@code application.yml}),
 * so failing startup on sandbox configuration would break every developer
 * machine rather than catching a bad deploy. The signal that a box is really
 * production is its configuration, not its profile — and this class cannot
 * tell the difference on its own.
 *
 * <p>To turn this into a hard gate, set {@code app.payments.require-live=true}
 * in the production environment only: the instance then refuses to start
 * unless the M-Pesa configuration is live. That is deliberately opt-in, so
 * the decision to block a boot sits with whoever owns the deploy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentConfigurationValidator {

    /** Safaricom's public sandbox shortcode — never a real business's. */
    private static final String SANDBOX_SHORTCODE = "174379";

    private final PlatformDarajaCredentialsResolver darajaResolver;
    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void reportPaymentReadiness() {
        List<String> problems = problems();

        if (problems.isEmpty()) {
            log.info("M-Pesa configuration: LIVE (base URL {}). Rent collection is able to move real money.",
                    safeBaseUrl());
            return;
        }

        boolean requireLive = environment.getProperty("app.payments.require-live", Boolean.class, false);

        String detail = String.join("\n  - ", problems);
        String banner = """

                ============================================================
                  M-PESA CONFIGURATION IS NOT LIVE
                  This instance cannot collect real rent. Problems found:
                  - %s
                  Configure the Daraja provider under the platform admin
                  Integrations screen (preferred — encrypted, audited, has a
                  roll-to-production step), or set the DARAJA_* environment
                  variables. See docs/ai/PRODUCTION_CHECKLIST.md.
                ============================================================
                """.formatted(detail);

        if (requireLive) {
            log.error(banner);
            throw new IllegalStateException(
                    "app.payments.require-live is true but the M-Pesa configuration is not live: " + detail);
        }

        log.warn(banner);
    }

    /**
     * Each problem is phrased as the specific value that is wrong, because
     * "M-Pesa is misconfigured" sends an operator hunting through four
     * settings to find which one.
     */
    private List<String> problems() {
        List<String> problems = new ArrayList<>();

        PlatformDarajaCredentialsResolver.PlatformDarajaCredentials creds;
        try {
            creds = darajaResolver.credentials();
        } catch (Exception e) {
            problems.add("platform Daraja credentials could not be resolved: " + e.getMessage());
            return problems;
        }

        String baseUrl = creds.baseUrl() == null ? "" : creds.baseUrl();
        if (baseUrl.isBlank()) {
            problems.add("base URL is not set (DARAJA_BASE_URL)");
        } else if (baseUrl.toLowerCase().contains("sandbox")) {
            problems.add("base URL points at the sandbox: " + baseUrl);
        }

        if (isPlaceholder(creds.consumerKey())) {
            problems.add("consumer key is unset or still the YOUR_CONSUMER_KEY placeholder");
        }
        if (isPlaceholder(creds.consumerSecret())) {
            problems.add("consumer secret is unset or still a placeholder");
        }
        if (isPlaceholder(creds.passkey())) {
            problems.add("passkey is unset or still the YOUR_DARAJA_PASSKEY placeholder");
        }
        if (SANDBOX_SHORTCODE.equals(trim(creds.businessShortCode()))) {
            problems.add("business shortcode is Safaricom's sandbox shortcode " + SANDBOX_SHORTCODE);
        } else if (trim(creds.businessShortCode()).isEmpty()) {
            problems.add("business shortcode is not set (DARAJA_SHORT_CODE)");
        }

        return problems;
    }

    /**
     * Treats the shipped {@code YOUR_*} sentinels as unset. They are not
     * merely wrong — they reach Safaricom and fail, which is why
     * {@code PlatformDarajaCredentialsResolver} documents them as reaching
     * the provider "and failing loudly".
     */
    private boolean isPlaceholder(String value) {
        String v = trim(value);
        return v.isEmpty() || v.startsWith("YOUR_");
    }

    private String safeBaseUrl() {
        try {
            return trim(darajaResolver.credentials().baseUrl());
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
