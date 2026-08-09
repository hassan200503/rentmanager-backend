package com.rentmanager.modules.tenant.api;

import com.rentmanager.modules.tenant.api.controller.PublicSubscriptionPlanController;
import com.rentmanager.modules.tenant.application.dto.response.SubscriptionPlanResponse;
import com.rentmanager.modules.tenant.application.query.service.SubscriptionPlanQueryService;
import com.rentmanager.modules.tenant.domain.enums.BillingCycle;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.error.ErrorTrackingService;
import com.rentmanager.shared.security.context.SecurityContextService;
import com.rentmanager.shared.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public pricing catalog must serve the live plan data (active plans
 * only) to the landing page without authentication, and must never expose
 * mutating behaviour.
 */
@WebMvcTest(controllers = PublicSubscriptionPlanController.class)
@Import(PublicSubscriptionPlanControllerTest.PermitAllSecurityConfig.class)
class PublicSubscriptionPlanControllerTest {

    @TestConfiguration
    static class PermitAllSecurityConfig {
        @Bean
        @Order(1)
        SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionPlanQueryService queryService;

    @MockBean
    private ErrorTrackingService errorTrackingService;

    @MockBean
    private JwtProvider jwtProvider;

    @MockBean
    private SecurityContextService securityContextService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private TenantRepository tenantRepository;

    @MockBean
    private TenantProfileRepository tenantProfileRepository;

    @Test
    void getActivePlans_returnsLiveCatalog() throws Exception {
        SubscriptionPlanResponse plan = new SubscriptionPlanResponse(
                UUID.randomUUID(),
                "STARTER",
                "Starter",
                "Up to 10 units",
                BillingCycle.MONTHLY,
                null,
                10,
                null,
                null,
                new BigDecimal("999.00"),
                null,
                true,
                true
        );

        when(queryService.getActive()).thenReturn(List.of(plan));

        mockMvc.perform(get("/api/v1/public/subscription-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("STARTER"))
                .andExpect(jsonPath("$.data[0].name").value("Starter"))
                .andExpect(jsonPath("$.data[0].monthlyPrice").value(999.00))
                .andExpect(jsonPath("$.data[0].active").value(true));

        verify(queryService).getActive();
    }

    @Test
    void getActivePlans_emptyCatalogIsValidResponse() throws Exception {
        when(queryService.getActive()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/public/subscription-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}