package com.rentmanager.shared.security.config;

import com.rentmanager.shared.security.filter.JwtAuthenticationFilter;
import com.rentmanager.shared.security.handler.CustomAccessDeniedHandler;
import com.rentmanager.shared.security.handler.CustomAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;

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



                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(accessDeniedHandler)
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
                                "/swagger-ui.html"
                        ).permitAll()

                        // Public auth endpoints
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh-token",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password"
                        ).permitAll()

                        // Everything else secured
                        .anyRequest().authenticated()
                )

                // =====================================================
                // JWT Filter (must be BEFORE UsernamePasswordAuthenticationFilter)
                // =====================================================
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}


