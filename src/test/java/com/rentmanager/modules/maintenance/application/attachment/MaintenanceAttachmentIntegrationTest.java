package com.rentmanager.modules.maintenance.application.attachment;

import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;
import com.rentmanager.modules.maintenance.domain.repository.MaintenanceRequestRepository;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Real Postgres (V102): photos are stored, listed and served only to the
 * organisation and the renter that own the request. Object storage is mocked;
 * the authorisation and the table's own constraints are real.
 */
// Rolled back after each test: this suite shares one reusable Postgres
// container, and rows left behind break other suites' cleanup (e.g.
// TenantSaaSIntegrationTest deletes from tenants).
@org.springframework.transaction.annotation.Transactional
class MaintenanceAttachmentIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};

    @MockBean
    private AttachmentStorage storage;

    @Autowired private MaintenanceAttachmentService service;
    @Autowired private MaintenanceRequestRepository requests;
    @Autowired private TenantRepository tenants;
    @Autowired private PropertyRepository properties;
    @Autowired private UnitRepository units;
    @Autowired private TenantProfileRepository profiles;
    @Autowired private JdbcTemplate jdbc;

    private UUID landlordId;
    private String renterClerk;
    private MaintenanceRequest request;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        Tenant landlord = tenants.save(Tenant.create("TEN-" + suffix, "Landlord", "l-" + suffix,
                "l@test.local", "+254700000000", TenantType.STANDARD));
        landlordId = landlord.getId();
        Property property = properties.save(Property.create(landlordId, "Block A", PropertyType.APARTMENT,
                null, null, null, "p", "corr"));
        Unit unit = units.save(Unit.create(landlordId, property.getId(), "U-" + suffix, "A1", null,
                new BigDecimal("1000.00"), null, "u", "corr"));
        renterClerk = "user_" + suffix;
        TenantProfile renter = profiles.save(TenantProfile.create(landlordId, renterClerk, "Renter",
                "r@test.local", "+254711111111", "ID1", "corr"));
        request = requests.save(MaintenanceRequest.submit(landlordId, unit.getId(), property.getId(), renter.getId(),
                null, "Leaking tap", "Kitchen", MaintenanceCategory.PLUMBING, MaintenancePriority.HIGH, "renter", "corr"));

        when(storage.put(anyString(), any(), anyString())).thenAnswer(inv -> "key-" + UUID.randomUUID() + ".jpg");
        when(storage.get(anyString())).thenReturn(JPEG);
    }

    @Test
    void renterAttachesAndBothSidesCanViewButNoOneElseCan() {
        MaintenanceAttachmentService.AttachmentView added = service.addAsRenter(renterClerk, request.getId(), JPEG);

        assertThat(service.listAsLandlord(landlordId, request.getId())).extracting(MaintenanceAttachmentService.AttachmentView::id)
                .containsExactly(added.id());
        assertThat(service.contentAsRenter(renterClerk, request.getId(), added.id()).bytes()).isEqualTo(JPEG);
        assertThat(service.contentAsLandlord(landlordId, request.getId(), added.id()).contentType()).isEqualTo("image/jpeg");

        // Another organisation, by id.
        assertThatThrownBy(() -> service.contentAsLandlord(UUID.randomUUID(), request.getId(), added.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        // A person with no renter profile here.
        assertThatThrownBy(() -> service.listAsRenter("user_stranger", request.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        // A real attachment id under the wrong request.
        assertThatThrownBy(() -> service.contentAsLandlord(landlordId, UUID.randomUUID(), added.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void databaseRefusesAnUnknownContentTypeEvenIfCodeRegressed() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO maintenance_attachments
                  (id, tenant_id, maintenance_request_id, storage_key, content_type, size_bytes, uploaded_by, created_at)
                VALUES (?, ?, ?, 'k', 'text/html', 10, 'RENTER', ?)
                """, UUID.randomUUID(), landlordId, request.getId(), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
