package com.rentmanager.modules.unit.api;

import com.rentmanager.modules.unit.api.controller.UnitQueryController;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import com.rentmanager.modules.unit.application.query.service.UnitQueryService;
import com.rentmanager.shared.error.ErrorTrackingService;
import com.rentmanager.shared.exception.GlobalExceptionHandler;
import com.rentmanager.shared.security.context.TenantContext;
import com.rentmanager.shared.security.filter.JwtAuthenticationFilter;
import com.rentmanager.shared.security.jwt.ClerkJwtAuthenticationConverter;
import com.rentmanager.shared.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {UnitQueryController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
@ImportAutoConfiguration(exclude = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class
})
class UnitApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UnitQueryService service;

    @MockBean
    private ErrorTrackingService errorTrackingService;

    @MockBean
    private JwtProvider jwtProvider;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private ClerkJwtAuthenticationConverter clerkJwtAuthenticationConverter;

    private static final UUID TENANT_ID = UUID.randomUUID();

    // With addFilters = false, the real filter chain (including whatever
    // normally derives tenant identity from an authenticated JWT and
    // populates TenantContext) never runs. Previously this test tried to
    // work around that by sending a raw X-Tenant-Id header — but that's
    // exactly the client-trusted-header pattern the tenant-isolation audit
    // closed off. resolveStrictTenantId() is now fail-closed and will not
    // derive tenant identity from an unauthenticated header, so TenantContext
    // is populated directly here instead, the same way TenantControllerSecurityTest
    // does it. TenantContext is backed by a ThreadLocal, and Surefire reuses
    // the same test thread across methods, so it must be cleared after each test.
    @BeforeEach
    void setUpTenantContext() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDownTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldReturnUnitsWithApiResponseWrapper() throws Exception {

        when(service.getAll(any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/api/v1/units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Units retrieved successfully"))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void shouldReturnUnitById() throws Exception {

        when(service.getById(any(), any()))
                .thenReturn(new UnitResponse());

        mockMvc.perform(get("/api/v1/units/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Unit retrieved successfully"))
                .andExpect(jsonPath("$.data").exists());
    }
}