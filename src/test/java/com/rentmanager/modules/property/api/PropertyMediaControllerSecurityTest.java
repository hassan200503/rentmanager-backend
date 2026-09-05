package com.rentmanager.modules.property.api;

import com.rentmanager.modules.property.api.controller.PropertyMediaController;
import com.rentmanager.modules.property.application.command.service.PropertyMediaCommandService;
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
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link PropertyMediaController} carried no {@code @PreAuthorize} — same
 * gap as its twin {@code UnitMediaController} (see that controller's Javadoc
 * for the full reasoning), found while fixing that one. A renter's JWT
 * carries the landlord's {@code tenantId}, so before the class-level gate
 * existed, a renter could upload/delete/reorder the landlord's property
 * photos. This is the regression guard.
 */
@WebMvcTest(controllers = PropertyMediaController.class)
class PropertyMediaControllerSecurityTest {

    private static final String PROPERTIES_BASE = "/api/v1/properties";
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final UUID MEDIA_ID = UUID.randomUUID();

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MediaUploadService mediaUploadService;

    @MockBean
    private PropertyMediaCommandService propertyMediaCommandService;

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
        return new MediaUploadResponse(MEDIA_ID, TENANT_ID, PROPERTY_ID, "https://example.com/x.jpg",
                "IMAGE", null, false, 0);
    }

    // ── A renter must be forbidden ──────────────────────────────────────

    @Test
    void renter_forbidden_onUploadMedia() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "x".getBytes());

        mockMvc.perform(multipart(PROPERTIES_BASE + "/{propertyId}/media", PROPERTY_ID)
                        .file(file)
                        .with(authentication(token("ROLE_TENANT")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void renter_forbidden_onDeleteMedia() throws Exception {
        mockMvc.perform(delete(PROPERTIES_BASE + "/{propertyId}/media/{mediaId}", PROPERTY_ID, MEDIA_ID)
                        .with(authentication(token("ROLE_TENANT")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void renter_forbidden_onSetPrimaryMedia() throws Exception {
        mockMvc.perform(put(PROPERTIES_BASE + "/{propertyId}/media/{mediaId}/primary", PROPERTY_ID, MEDIA_ID)
                        .with(authentication(token("ROLE_TENANT")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void renter_forbidden_onUpdateCaption() throws Exception {
        mockMvc.perform(patch(PROPERTIES_BASE + "/{propertyId}/media/{mediaId}", PROPERTY_ID, MEDIA_ID)
                        .with(authentication(token("ROLE_TENANT")))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"caption\":\"new caption\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void renter_forbidden_onReorderMedia() throws Exception {
        mockMvc.perform(patch(PROPERTIES_BASE + "/{propertyId}/media/reorder", PROPERTY_ID)
                        .with(authentication(token("ROLE_TENANT")))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"items\":[{\"mediaId\":\"" + MEDIA_ID + "\",\"sortOrder\":0}]}"))
                .andExpect(status().isForbidden());
    }

    // ── STAFF forbidden (write-only controller — matches PropertyCommandController) ──

    @Test
    void staff_forbidden_onUploadMedia() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "x".getBytes());

        mockMvc.perform(multipart(PROPERTIES_BASE + "/{propertyId}/media", PROPERTY_ID)
                        .file(file)
                        .with(authentication(token("ROLE_LANDLORD_STAFF")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ── OWNER/MANAGER succeed ───────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER"})
    void ownerAndManager_succeed_onUploadMedia(String authority) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "x".getBytes());
        when(mediaUploadService.uploadPropertyMedia(any(), any(), any(), any(Boolean.class)))
                .thenReturn(sampleMedia());

        mockMvc.perform(multipart(PROPERTIES_BASE + "/{propertyId}/media", PROPERTY_ID)
                        .file(file)
                        .with(authentication(token(authority)))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER"})
    void ownerAndManager_succeed_onDeleteMedia(String authority) throws Exception {
        mockMvc.perform(delete(PROPERTIES_BASE + "/{propertyId}/media/{mediaId}", PROPERTY_ID, MEDIA_ID)
                        .with(authentication(token(authority)))
                        .with(csrf()))
                .andExpect(status().isOk());
    }
}
