package com.rentmanager.shared.security.config;

import com.rentmanager.shared.security.handler.CustomAccessDeniedHandler;
import com.rentmanager.shared.security.handler.CustomAuthenticationEntryPoint;
import com.rentmanager.shared.security.jwt.ClerkJwtAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean
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
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/v1/public/**"
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
