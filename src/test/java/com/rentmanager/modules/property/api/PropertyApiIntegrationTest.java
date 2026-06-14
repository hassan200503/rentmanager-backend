package com.rentmanager.modules.property.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.rentmanager.modules.property.application.command.validator.UpdatePropertyValidator;
import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest;
import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class PropertyApiIntegrationTest {

    @MockBean
    private PropertyRepository propertyRepository;

    @MockBean
    private UpdatePropertyValidator updatePropertyValidator;

    @BeforeEach
    void setup() {

        when(propertyRepository.existsByTenantIdAndNameIgnoreCase(any(), any()))
                .thenReturn(false);

        when(propertyRepository.save(any(Property.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(propertyRepository.findByIdAndTenantId(any(), any()))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    UUID tenantId = invocation.getArgument(1);

                    if (TENANT_B.equals(tenantId)) {
                        return Optional.empty();
                    }

                    Property p = Property.rehydrate(
                            id,
                            tenantId,
                            "mock",
                            "PROP-" + id,
                            PropertyType.APARTMENT,
                            PropertyStatus.DRAFT,
                            OccupancyStatus.VACANT,
                            null,
                            null,
                            null,
                            null
                    );

                    return Optional.of(p);
                });

        doNothing().when(updatePropertyValidator).validate(any(), any(), any());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID TENANT_A =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID TENANT_B =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private String createProperty(UUID tenantId, String name) throws Exception {

        CreatePropertyRequest request = new CreatePropertyRequest();
        request.setName(name);
        request.setDescription("Test property");
        request.setPropertyType(PropertyType.APARTMENT);

        MvcResult result = mockMvc.perform(post("/api/v1/properties")
                        .header("X-Tenant-Id", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value(name))
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.propertyId");
    }

    @Test
    void shouldCreatePropertySuccessfully() throws Exception {
        createProperty(TENANT_A, "Green Villa");
    }

    @Test
    void shouldUpdatePropertySuccessfully() throws Exception {

        String id = createProperty(TENANT_A, "Old Name");

        UpdatePropertyRequest update = new UpdatePropertyRequest();
        update.setName("New Name");
        update.setDescription("Updated description");

        mockMvc.perform(put("/api/v1/properties/" + id)
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("New Name"));
    }

    @Test
    void shouldActivatePropertySuccessfully() throws Exception {

        String id = createProperty(TENANT_A, "Activate House");

        mockMvc.perform(post("/api/v1/properties/" + id + "/activate")
                        .header("X-Tenant-Id", TENANT_A.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldArchivePropertySuccessfully() throws Exception {

        String id = createProperty(TENANT_A, "Archive House");

        mockMvc.perform(post("/api/v1/properties/" + id + "/archive")
                        .header("X-Tenant-Id", TENANT_A.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldEnforceTenantIsolation() throws Exception {

        String propertyId = createProperty(TENANT_A, "Tenant A Property");

        mockMvc.perform(get("/api/v1/properties/" + propertyId)
                        .header("X-Tenant-Id", TENANT_B.toString()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldPreventDuplicatePropertyNamesPerTenant() throws Exception {

        when(propertyRepository.existsByTenantIdAndNameIgnoreCase(any(), any()))
                .thenReturn(true);

        CreatePropertyRequest duplicate = new CreatePropertyRequest();
        duplicate.setName("Duplicate House");
        duplicate.setPropertyType(PropertyType.VILLA);

        mockMvc.perform(post("/api/v1/properties")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldAllowSamePropertyNameAcrossDifferentTenants() throws Exception {

        CreatePropertyRequest t1 = new CreatePropertyRequest();
        t1.setName("Shared Name");
        t1.setPropertyType(PropertyType.APARTMENT);

        CreatePropertyRequest t2 = new CreatePropertyRequest();
        t2.setName("Shared Name");
        t2.setPropertyType(PropertyType.APARTMENT);

        mockMvc.perform(post("/api/v1/properties")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t1)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/properties")
                        .header("X-Tenant-Id", TENANT_B.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t2)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldHandleFullPropertyLifecycle() throws Exception {

        String id = createProperty(TENANT_A, "Lifecycle Property");

        mockMvc.perform(post("/api/v1/properties/" + id + "/activate")
                        .header("X-Tenant-Id", TENANT_A.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/properties/" + id + "/archive")
                        .header("X-Tenant-Id", TENANT_A.toString()))
                .andExpect(status().isOk());
    }
}