package com.rentmanager.shared.security.config;

import com.rentmanager.shared.security.handler.CustomAccessDeniedHandler;
import com.rentmanager.shared.security.handler.CustomAuthenticationEntryPoint;
import com.rentmanager.shared.security.jwt.ClerkJwtAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
@Profile("!test")
public class SecurityConfig {

    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final ClerkJwtAuthenticationConverter clerkJwtAuthenticationConverter;

    /**
     * A separate chain for the management port, ordered ahead of the
     * application chain.
     *
     * <h2>Why this is needed</h2>
     * The application chain ends in {@code anyRequest().authenticated()},
     * and it applies to the management port too. That made
     * {@code /actuator/prometheus} return 401 — and a Prometheus scraper
     * cannot present a Clerk JWT, so metrics would have been collected by
     * nobody while appearing perfectly configured.
     *
     * <h2>Why permitting these is safe</h2>
     * Three things hold at once, and the guarantee needs all three:
     * <ul>
     *   <li>Only {@code health} and {@code prometheus} are exposed at all
     *       ({@code management.endpoints.web.exposure.include}), so
     *       {@code /env}, {@code /heapdump} and the rest are not mapped and
     *       cannot be reached however this chain is written.</li>
     *   <li>Both are read-only. Neither changes any state.</li>
     *   <li>The management port is separate and must not be publicly routed
     *       — see the comment on {@code management.server.port}.</li>
     * </ul>
     *
     * <p>Authenticating the probes is not an option either: an orchestrator
     * has no credentials to present, and a liveness probe that returns 401 is
     * a container that gets restarted forever.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // =====================================================
                // CSRF (disabled for stateless APIs)
                // =====================================================
                .csrf(csrf -> csrf.disable())

                // =====================================================
                // CORS (use global config if you have one)
                // =====================================================
                .cors(Customizer.withDefaults())

                // =====================================================
                // Stateless session (JWT-based auth)
                // =====================================================
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // =====================================================
                // Exception Handling (SAAS-GRADE CONTROL POINT)
                // =====================================================
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(accessDeniedHandler)               // 403
                        .authenticationEntryPoint(authenticationEntryPoint)     // 401
                )

                // =====================================================
                // Authorization Rules
                // =====================================================
                .authorizeHttpRequests(auth -> auth

                        // Public system endpoints
                        .requestMatchers(
                                // Health and the liveness/readiness probes.
                                // These run on the management port (8081) and
                                // must answer without a token — an
                                // orchestrator has no credentials to present.
                                "/actuator/health",
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/v1/public/**",
                                // Clerk webhooks: Svix HMAC-SHA256 verified in the controller.
                                // No Bearer token — Clerk signs with a webhook secret instead.
                                "/api/v1/webhooks/clerk"
                                // "/api/v1/dev/**" was permitted here. It was
                                // not exploitable — every controller under it
                                // is @Profile("dev") and the default profile
                                // is prod, so nothing is mapped there in a
                                // real deployment — but it was belt without
                                // braces: the first ungated controller added
                                // under that prefix would have shipped open
                                // to the internet. The dev endpoints still
                                // work in the dev profile; they now simply
                                // require authentication like everything else.
                        ).permitAll()

                        // Everything else secured
                        .anyRequest().authenticated()
                )

                // =====================================================
                // OAuth2 Resource Server (Clerk JWT verification via JWKS)
                // =====================================================
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(clerkJwtAuthenticationConverter)
                        )
                );

        return http.build();
    }



}
