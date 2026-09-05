package com.rentmanager.modules.unit.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.unit.api.controller.UnitMediaController;
import com.rentmanager.modules.unit.application.command.service.UnitMediaCommandService;
import com.rentmanager.modules.unit.application.query.service.UnitMediaQueryService;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.dto.MediaUploadResponse;
import com.rentmanager.shared.error.ErrorTrackingService;
import com.rentmanager.shared.security.context.SecurityContextService;
import com.rentmanager.shared.security.jwt.ClerkAuthenticationToken;
import com.rentmanager.shared.security.jwt.JwtProvider;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import com.rentmanager.shared.service.MediaUploadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link UnitMediaController} carried no {@code @PreAuthorize} at all before
 * this test existed — any authenticated role could reach every method,
 * including a renter, whose JWT carries the landlord's {@code tenantId} (see
 * the controller's own Javadoc). This is the regression guard for that gap:
 * a renter (ROLE_TENANT) must be forbidden everywhere, and reads/writes
 * split OWNER/MANAGER/STAFF vs OWNER/MANAGER exactly like
 * {@code UnitQueryController} and {@code PropertyCommandController} already
 * do elsewhere in this codebase.
 */
@WebMvcTest(controllers = UnitMediaController.class)
class UnitMediaControllerSecurityTest {

    private static final String UNITS_BASE = "/api/v1/units";
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID UNIT_ID = UUID.randomUUID();
    private static final UUID MEDIA_ID = UUID.randomUUID();

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MediaUploadService mediaUploadService;

    @MockBean
    private UnitMediaQueryService unitMediaQueryService;

    @MockBean
    private UnitMediaCommandService unitMediaCommandService;

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

    private AbstractAuthenticationToken token(String authority) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", "clerk_user_id")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        Set<? extends GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority(authority));
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(), TENANT_ID, "user@example.com", "", true, authorities);

        return new ClerkAuthenticationToken(principal, jwt, authorities);
    }

    private MediaUploadResponse sampleMedia() {
        return new MediaUploadResponse(MEDIA_ID, TENANT_ID, UNIT_ID, "https://example.com/x.jpg",
                "IMAGE", null, false, 0);
    }

    // ── A renter must be forbidden everywhere ──────────────────────────────
    // This is the exact hole this test class exists to close: a renter's
    // JWT carries the landlord's tenantId, so before @PreAuthorize existed
    // here, every one of these calls would have reached the (correctly
    // tenant-scoped) service layer and succeeded.

    @Test
    void renter_forbidden_onGetMedia() throws Exception {
        mockMvc.perform(get(UNITS_BASE + "/{unitId}/media", UNIT_ID)
                        .with(authentication(token("ROLE_TENANT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void renter_forbidden_onUploadMedia() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "x".getBytes());

        mockMvc.perform(multipart(UNITS_BASE + "/{unitId}/media", UNIT_ID)
                        .file(file)
                        .with(authentication(token("ROLE_TENANT")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void renter_forbidden_onDeleteMedia() throws Exception {
        mockMvc.perform(delete(UNITS_BASE + "/{unitId}/media/{mediaId}", UNIT_ID, MEDIA_ID)
                        .with(authentication(token("ROLE_TENANT")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void renter_forbidden_onSetPrimaryMedia() throws Exception {
        mockMvc.perform(put(UNITS_BASE + "/{unitId}/media/{mediaId}/primary", UNIT_ID, MEDIA_ID)
                        .with(authentication(token("ROLE_TENANT")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void renter_forbidden_onUpdateCaption() throws Exception {
        mockMvc.perform(patch(UNITS_BASE + "/{unitId}/media/{mediaId}", UNIT_ID, MEDIA_ID)
                        .with(authentication(token("ROLE_TENANT")))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"caption\":\"new caption\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void renter_forbidden_onReorderMedia() throws Exception {
        mockMvc.perform(put(UNITS_BASE + "/{unitId}/media/reorder", UNIT_ID)
                        .with(authentication(token("ROLE_TENANT")))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"mediaIds\":[\"" + MEDIA_ID + "\"]}"))
                .andExpect(status().isForbidden());
    }

    // ── Writes: STAFF forbidden, OWNER/MANAGER succeed ─────────────────────

    @Test
    void staff_forbidden_onUploadMedia() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "x".getBytes());

        mockMvc.perform(multipart(UNITS_BASE + "/{unitId}/media", UNIT_ID)
                        .file(file)
                        .with(authentication(token("ROLE_LANDLORD_STAFF")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void staff_forbidden_onDeleteMedia() throws Exception {
        mockMvc.perform(delete(UNITS_BASE + "/{unitId}/media/{mediaId}", UNIT_ID, MEDIA_ID)
                        .with(authentication(token("ROLE_LANDLORD_STAFF")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER"})
    void ownerAndManager_succeed_onUploadMedia(String authority) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "x".getBytes());
        when(mediaUploadService.uploadUnitMedia(any(), any(), any(), any(Boolean.class)))
                .thenReturn(sampleMedia());

        mockMvc.perform(multipart(UNITS_BASE + "/{unitId}/media", UNIT_ID)
                        .file(file)
                        .with(authentication(token(authority)))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER"})
    void ownerAndManager_succeed_onDeleteMedia(String authority) throws Exception {
        mockMvc.perform(delete(UNITS_BASE + "/{unitId}/media/{mediaId}", UNIT_ID, MEDIA_ID)
                        .with(authentication(token(authority)))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    // ── Reads: OWNER/MANAGER/STAFF all succeed ─────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER", "ROLE_LANDLORD_STAFF"})
    void allLandlordRoles_succeed_onGetMedia(String authority) throws Exception {
        when(unitMediaQueryService.getUnitMedia(any(), any())).thenReturn(List.of(sampleMedia()));

        mockMvc.perform(get(UNITS_BASE + "/{unitId}/media", UNIT_ID)
                        .with(authentication(token(authority))))
                .andExpect(status().isOk());
    }
}
