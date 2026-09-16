package com.rentmanager.modules.maintenance.application.attachment;

import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;
import com.rentmanager.modules.maintenance.domain.repository.MaintenanceRequestRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.shared.exception.BusinessException;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Photos on maintenance requests (V102).
 *
 * <h3>Authorisation</h3>
 * Two ways in, and only two:
 * <ul>
 *   <li>Landlord side: the request must belong to the caller's organisation
 *       (tenantId from the verified token).</li>
 *   <li>Renter side: the request must have been raised by one of the caller's
 *       own renter profiles, under the same landlord organisation.</li>
 * </ul>
 * Anything else is "not found" — the same answer whether the request exists
 * elsewhere or not at all.
 *
 * <h3>Validation</h3>
 * The declared content type is not trusted: the first bytes must actually be
 * JPEG, PNG or WebP. At most {@link #MAX_PER_REQUEST} photos per request and
 * {@link #MAX_BYTES} each.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceAttachmentService {

    public static final int MAX_PER_REQUEST = 5;
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    public enum Uploader { RENTER, LANDLORD }

    public record AttachmentView(UUID id, String contentType, int sizeBytes, String uploadedBy, Instant createdAt) {}

    public record AttachmentContent(byte[] bytes, String contentType) {}

    private final MaintenanceRequestRepository requests;
    private final TenantProfileRepository profiles;
    private final AttachmentStorage storage;
    private final JdbcTemplate jdbc;

    // ── Landlord side ────────────────────────────────────────────────────────

    public AttachmentView addAsLandlord(UUID tenantId, UUID requestId, byte[] bytes) {
        MaintenanceRequest request = landlordRequest(tenantId, requestId);
        return add(request, bytes, Uploader.LANDLORD);
    }

    public List<AttachmentView> listAsLandlord(UUID tenantId, UUID requestId) {
        MaintenanceRequest request = landlordRequest(tenantId, requestId);
        return list(request);
    }

    public AttachmentContent contentAsLandlord(UUID tenantId, UUID requestId, UUID attachmentId) {
        MaintenanceRequest request = landlordRequest(tenantId, requestId);
        return content(request, attachmentId);
    }

    // ── Renter side ──────────────────────────────────────────────────────────

    public AttachmentView addAsRenter(String clerkUserId, UUID requestId, byte[] bytes) {
        return add(renterRequest(clerkUserId, requestId), bytes, Uploader.RENTER);
    }

    public List<AttachmentView> listAsRenter(String clerkUserId, UUID requestId) {
        return list(renterRequest(clerkUserId, requestId));
    }

    public AttachmentContent contentAsRenter(String clerkUserId, UUID requestId, UUID attachmentId) {
        return content(renterRequest(clerkUserId, requestId), attachmentId);
    }

    // ── Shared ───────────────────────────────────────────────────────────────

    private MaintenanceRequest landlordRequest(UUID tenantId, UUID requestId) {
        if (tenantId == null) {
            throw notFound();
        }
        return requests.findByIdAndTenantId(requestId, tenantId).orElseThrow(MaintenanceAttachmentService::notFound);
    }

    private MaintenanceRequest renterRequest(String clerkUserId, UUID requestId) {
        List<TenantProfile> mine = profiles.findAllByClerkUserId(clerkUserId);
        for (TenantProfile profile : mine) {
            var found = requests.findByIdAndTenantId(requestId, profile.getTenantId())
                    .filter(r -> Objects.equals(r.getTenantProfileId(), profile.getId()));
            if (found.isPresent()) {
                return found.get();
            }
        }
        throw notFound();
    }

    private AttachmentView add(MaintenanceRequest request, byte[] bytes, Uploader uploader) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("The photo is empty.", ErrorCode.VALIDATION_ERROR);
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessException("Photos must be 5 MB or smaller.", ErrorCode.VALIDATION_ERROR);
        }
        String contentType = sniffImageType(bytes);
        if (contentType == null) {
            throw new BusinessException("Only JPEG, PNG or WebP photos can be attached.", ErrorCode.VALIDATION_ERROR);
        }
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM maintenance_attachments WHERE tenant_id = ? AND maintenance_request_id = ?",
                Integer.class, request.getTenantId(), request.getId());
        if (existing != null && existing >= MAX_PER_REQUEST) {
            throw new BusinessException("A request can have at most " + MAX_PER_REQUEST + " photos.", ErrorCode.VALIDATION_ERROR);
        }

        String key = storage.put("rentmanager/maintenance/" + request.getTenantId() + "/" + request.getId(), bytes, contentType);

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update("""
                    INSERT INTO maintenance_attachments
                        (id, tenant_id, maintenance_request_id, storage_key, content_type, size_bytes, uploaded_by, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, request.getTenantId(), request.getId(), key, contentType, bytes.length, uploader.name(), Timestamp.from(now));
        } catch (RuntimeException e) {
            storage.delete(key); // don't leave an orphaned private object
            throw e;
        }
        return new AttachmentView(id, contentType, bytes.length, uploader.name(), now);
    }

    private List<AttachmentView> list(MaintenanceRequest request) {
        return jdbc.query("""
                SELECT id, content_type, size_bytes, uploaded_by, created_at FROM maintenance_attachments
                WHERE tenant_id = ? AND maintenance_request_id = ? ORDER BY created_at
                """,
                (rs, i) -> new AttachmentView(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3),
                        rs.getString(4), rs.getTimestamp(5).toInstant()),
                request.getTenantId(), request.getId());
    }

    private AttachmentContent content(MaintenanceRequest request, UUID attachmentId) {
        List<String[]> rows = jdbc.query("""
                SELECT storage_key, content_type FROM maintenance_attachments
                WHERE id = ? AND tenant_id = ? AND maintenance_request_id = ?
                """,
                (rs, i) -> new String[]{rs.getString(1), rs.getString(2)},
                attachmentId, request.getTenantId(), request.getId());
        if (rows.isEmpty()) {
            throw notFound();
        }
        return new AttachmentContent(storage.get(rows.get(0)[0]), rows.get(0)[1]);
    }

    /** Identifies JPEG, PNG and WebP by their magic bytes; null for anything else. */
    static String sniffImageType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        if (b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8), png)) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Maintenance request not found", ErrorCode.RESOURCE_NOT_FOUND);
    }
}
