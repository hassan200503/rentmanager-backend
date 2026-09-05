package com.rentmanager.shared.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The "dev" profile registers unauthenticated test-setup endpoints under
 * /api/v1/dev/** (see SecurityConfig's permitAll matcher and the
 * @Profile("dev") controllers in the reservation module). Those endpoints
 * create landlords, properties, units, leases and Clerk users with no auth
 * check, so this profile must never run against a real datastore. Refuse to
 * start rather than silently exposing them.
 */
@Slf4j
@Component
@Profile("dev")
public class DevProfileSafetyCheck {

    private final String datasourceUrl;

    public DevProfileSafetyCheck(@Value("${spring.datasource.url}") String datasourceUrl) {
        this.datasourceUrl = datasourceUrl;
    }

    @PostConstruct
    public void verifyLocalDatasource() {
        boolean isLocal = datasourceUrl.contains("localhost")
                || datasourceUrl.contains("127.0.0.1")
                || datasourceUrl.contains("::1");

        if (!isLocal) {
            throw new IllegalStateException(
                    "Refusing to start: the 'dev' Spring profile is active, which exposes "
                            + "unauthenticated /api/v1/dev/** test-setup endpoints, but the configured "
                            + "datasource (" + datasourceUrl + ") is not local. Set "
                            + "SPRING_PROFILES_ACTIVE=prod (the default) for any non-local deployment."
            );
        }

        log.warn("'dev' Spring profile is active: /api/v1/dev/** test-setup endpoints are "
                + "unauthenticated. This must only ever run against a local datasource.");
    }
}
