/*package com.rentmanager.modules.unit.api;

import com.rentmanager.modules.unit.api.controller.UnitQueryController;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import com.rentmanager.modules.unit.application.query.service.UnitQueryService;
import com.rentmanager.shared.error.ErrorTrackingService;
import com.rentmanager.shared.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

@WebMvcTest(UnitQueryController.class)
class UnitQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ===== service layer =====
    @MockBean
    private UnitQueryService service;

    @MockBean
    private ErrorTrackingService errorTrackingService;

    // ===== security FIX (required because filter is global) =====
    @MockBean
    private JwtProvider jwtProvider;

    @MockBean
    private com.rentmanager.shared.security.filter.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void shouldReturnUnitsWithApiResponseWrapper() throws Exception {

        when(service.getAll(any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/units")
                        .header("X-Tenant-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Units retrieved successfully"))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void shouldReturnUnitById() throws Exception {

        when(service.getById(any(), any()))
                .thenReturn(new UnitResponse());

        mockMvc.perform(get("/units/" + UUID.randomUUID())
                        .header("X-Tenant-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Unit retrieved successfully"))
                .andExpect(jsonPath("$.data").exists());
    }
}



 */