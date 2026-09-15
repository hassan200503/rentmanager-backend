package com.rentmanager.modules.maintenance.application.attachment;

import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;
import com.rentmanager.modules.maintenance.domain.repository.MaintenanceRequestRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.shared.exception.BusinessException;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MaintenanceAttachmentServiceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P'};
    private static final byte[] HTML_PRETENDING = "<html><script>alert(1)</script>".getBytes();

    private MaintenanceRequestRepository requests;
    private TenantProfileRepository profiles;
    private AttachmentStorage storage;
    private JdbcTemplate jdbc;
    private MaintenanceAttachmentService service;

    private final UUID landlord = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        requests = mock(MaintenanceRequestRepository.class);
        profiles = mock(TenantProfileRepository.class);
        storage = mock(AttachmentStorage.class);
        jdbc = mock(JdbcTemplate.class);
        service = new MaintenanceAttachmentService(requests, profiles, storage, jdbc);
    }

    private MaintenanceRequest requestBy(UUID tenantProfileId) {
        return MaintenanceRequest.submit(landlord, UUID.randomUUID(), UUID.randomUUID(), tenantProfileId, UUID.randomUUID(),
                "Leak", "Kitchen", MaintenanceCategory.PLUMBING, MaintenancePriority.HIGH, "renter", "corr");
    }

    private TenantProfile profile(UUID tenantId, String clerk) {
        return TenantProfile.rehydrate(UUID.randomUUID(), tenantId, clerk, "R", "r@x", "+254712345678", "1");
    }

    @Test
    void recognisesRealImagesByContentNotByDeclaredType() {
        assertThat(MaintenanceAttachmentService.sniffImageType(JPEG)).isEqualTo("image/jpeg");
        assertThat(MaintenanceAttachmentService.sniffImageType(PNG)).isEqualTo("image/png");
        assertThat(MaintenanceAttachmentService.sniffImageType(WEBP)).isEqualTo("image/webp");
        assertThat(MaintenanceAttachmentService.sniffImageType(HTML_PRETENDING)).isNull();
        assertThat(MaintenanceAttachmentService.sniffImageType(new byte[]{1})).isNull();
    }

    @Test
    void renterCanAttachToTheirOwnRequest() {
        TenantProfile mine = profile(landlord, "user_renter");
        MaintenanceRequest request = requestBy(mine.getId());
        when(profiles.findAllByClerkUserId("user_renter")).thenReturn(List.of(mine));
        when(requests.findByIdAndTenantId(request.getId(), landlord)).thenReturn(Optional.of(request));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(0);
        when(storage.put(anyString(), any(), eq("image/jpeg"))).thenReturn("rentmanager/maintenance/x/y/abc.jpg");

        MaintenanceAttachmentService.AttachmentView view = service.addAsRenter("user_renter", request.getId(), JPEG);

        assertThat(view.contentType()).isEqualTo("image/jpeg");
        assertThat(view.uploadedBy()).isEqualTo("RENTER");
        verify(storage).put(eq("rentmanager/maintenance/" + landlord + "/" + request.getId()), any(), eq("image/jpeg"));
    }

    @Test
    void anotherRenterUnderTheSameLandlordCannotSeeOrAttach() {
        TenantProfile owner = profile(landlord, "user_owner");
        TenantProfile neighbour = profile(landlord, "user_neighbour");
        MaintenanceRequest request = requestBy(owner.getId());
        when(profiles.findAllByClerkUserId("user_neighbour")).thenReturn(List.of(neighbour));
        when(requests.findByIdAndTenantId(request.getId(), landlord)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.addAsRenter("user_neighbour", request.getId(), JPEG))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.listAsRenter("user_neighbour", request.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void landlordOfAnotherOrganisationGetsNotFound() {
        UUID otherLandlord = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(requests.findByIdAndTenantId(requestId, otherLandlord)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.contentAsLandlord(otherLandlord, requestId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.contentAsLandlord(null, requestId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsNonImagesOversizeAndTooMany() {
        MaintenanceRequest request = requestBy(UUID.randomUUID());
        when(requests.findByIdAndTenantId(request.getId(), landlord)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.addAsLandlord(landlord, request.getId(), HTML_PRETENDING))
                .isInstanceOf(BusinessException.class).hasMessageContaining("JPEG, PNG or WebP");

        byte[] huge = new byte[MaintenanceAttachmentService.MAX_BYTES + 1];
        huge[0] = (byte) 0xFF; huge[1] = (byte) 0xD8; huge[2] = (byte) 0xFF;
        assertThatThrownBy(() -> service.addAsLandlord(landlord, request.getId(), huge))
                .isInstanceOf(BusinessException.class).hasMessageContaining("5 MB");

        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(MaintenanceAttachmentService.MAX_PER_REQUEST);
        assertThatThrownBy(() -> service.addAsLandlord(landlord, request.getId(), JPEG))
                .isInstanceOf(BusinessException.class).hasMessageContaining("at most");

        verifyNoInteractions(storage);
    }

    @Test
    void failedInsertRemovesTheStoredObject() {
        MaintenanceRequest request = requestBy(UUID.randomUUID());
        when(requests.findByIdAndTenantId(request.getId(), landlord)).thenReturn(Optional.of(request));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(0);
        when(storage.put(anyString(), any(), anyString())).thenReturn("key.jpg");
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.addAsLandlord(landlord, request.getId(), JPEG)).isInstanceOf(RuntimeException.class);
        verify(storage).delete("key.jpg");
    }
}
