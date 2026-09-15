package com.rentmanager.shared.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Refuses to start a real deployment whose configuration would be unsafe.
 *
 * <h2>Why opt-in</h2>
 * The {@code prod} profile is also the local default, and every setting has a
 * developer-friendly fallback (localhost database with a known password,
 * localhost CORS, public API docs). Failing on those by profile would break
 * every developer machine. The deployment image sets
 * {@code APP_DEPLOYMENT_STRICT=true}; only then do these checks run, and a
 * single failure stops the boot.
 *
 * <h2>What it checks, and why each matters</h2>
 * Every check is something that starts cleanly, reports healthy and serves
 * traffic while being wrong — the class of mistake nobody notices until it is
 * exploited. Problems name the setting, never its value.
 *
 * <p>Provider credentials (Daraja, Cloudinary, SMS) are deliberately not
 * required here: those features fail closed on their own and may be
 * configured after the first deploy. Callback secrets are checked for
 * strength only when set.
 */
@Slf4j
@Component
public class DeploymentSafetyGuard {

    static final String STRICT_PROPERTY = "app.deployment.strict";
    private static final int MIN_DB_PASSWORD_LENGTH = 16;
    private static final int MIN_CALLBACK_SECRET_LENGTH = 32;

    private final Environment environment;

    public DeploymentSafetyGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void verify() {
        if (!environment.getProperty(STRICT_PROPERTY, Boolean.class, false)) {
            return;
        }
        List<String> problems = problems(environment);
        if (!problems.isEmpty()) {
            String detail = String.join("\n  - ", problems);
            log.error("Refusing to start: unsafe deployment configuration:\n  - {}", detail);
            throw new IllegalStateException("Unsafe deployment configuration:\n  - " + detail);
        }
        log.info("Deployment safety checks passed.");
    }

    static List<String> problems(Environment env) {
        List<String> problems = new ArrayList<>();

        if (Arrays.stream(env.getActiveProfiles()).anyMatch("dev"::equalsIgnoreCase)) {
            problems.add("SPRING_PROFILES_ACTIVE includes 'dev' (unauthenticated test endpoints)");
        }

        String dbUrl = value(env, "spring.datasource.url");
        if (dbUrl.contains("localhost") || dbUrl.contains("127.0.0.1")) {
            problems.add("DB_URL points at localhost (the developer default)");
        }
        String dbPassword = value(env, "spring.datasource.password");
        if (dbPassword.length() < MIN_DB_PASSWORD_LENGTH || "rentmanager".equals(dbPassword)) {
            problems.add("DB_PASSWORD is the default or shorter than " + MIN_DB_PASSWORD_LENGTH + " characters");
        }

        if (!value(env, "spring.security.oauth2.resourceserver.jwt.jwk-set-uri").startsWith("https://")) {
            problems.add("CLERK_JWKS_URL must be an https URL");
        }
        String clerkSecret = value(env, "clerk.secret-key");
        if (!clerkSecret.startsWith("sk_")) {
            problems.add("CLERK_SECRET_KEY is unset or still the placeholder");
        }

        String origins = value(env, "cors.allowed-origins");
        for (String origin : origins.split(",")) {
            String o = origin.trim().toLowerCase(Locale.ROOT);
            if (o.isEmpty()) {
                continue;
            }
            if (o.equals("*") || !o.startsWith("https://") || o.contains("localhost") || o.contains("127.0.0.1")) {
                problems.add("CORS_ALLOWED_ORIGINS must list only https origins (found a wildcard, http or localhost entry)");
                break;
            }
        }

        if (env.getProperty("springdoc.api-docs.enabled", Boolean.class, true)) {
            problems.add("API_DOCS_ENABLED must be false (the full API map would be public)");
        }

        if (!"native".equalsIgnoreCase(value(env, "server.forward-headers-strategy"))) {
            problems.add("server.forward-headers-strategy must be 'native' behind the reverse proxy "
                    + "(client IPs for rate limiting and audit)");
        }

        checkOptionalSecret(env, "daraja.callback-secret", "DARAJA_CALLBACK_SECRET", problems);
        checkOptionalSecret(env, "daraja.b2c.callback-secret", "DARAJA_B2C_CALLBACK_SECRET", problems);

        return problems;
    }

    private static void checkOptionalSecret(Environment env, String property, String name, List<String> problems) {
        String secret = value(env, property);
        if (!secret.isEmpty() && secret.length() < MIN_CALLBACK_SECRET_LENGTH) {
            problems.add(name + " is set but shorter than " + MIN_CALLBACK_SECRET_LENGTH
                    + " characters (it is the only thing authenticating M-Pesa callbacks)");
        }
    }

    private static String value(Environment env, String property) {
        String v = env.getProperty(property);
        return v == null ? "" : v.trim();
    }
}
