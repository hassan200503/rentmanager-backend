package com.rentmanager.modules.announcement.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.announcement.api.dto.AnnouncementHistoryItem;
import com.rentmanager.modules.announcement.api.dto.AnnouncementPreviewResponse;
import com.rentmanager.modules.announcement.api.dto.AnnouncementResponse;
import com.rentmanager.modules.announcement.api.dto.request.CreateAnnouncementRequest;
import com.rentmanager.modules.announcement.application.AnnouncementCommandService;
import com.rentmanager.modules.announcement.application.AnnouncementQueryService;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementPriority;
import com.rentmanager.modules.announcement.domain.model.Announcement;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private final AnnouncementCommandService commandService;
    private final AnnouncementQueryService queryService;

    /**
     * Composes and persists the announcement. The HTTP request never
     * iterates recipients: the fan-out rows are created after commit by
     * the broadcast listener and dispatched on the dedicated dispatch
     * thread.
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateAnnouncementRequest request
    ) {
        AnnouncementPriority priority = request.priority() != null ? request.priority() : AnnouncementPriority.INFO;
        Set<AnnouncementChannel> channels = request.channels() != null && !request.channels().isEmpty()
                ? EnumSet.copyOf(request.channels())
                : EnumSet.allOf(AnnouncementChannel.class);

        Announcement announcement = commandService.create(
                requireTenantId(user),
                user.getUserId(),
                request.message(),
                priority,
                channels,
                request.expiresAt()
        );
        return ResponseEntity.ok(ApiResponse.ok("Announcement created", AnnouncementResponse.from(announcement)));
    }

    /**
     * Recipient + channel counts for the confirm screen - the numbers the
     * landlord sees BEFORE committing, computed by the same planner the
     * broadcast job executes.
     */
    @GetMapping("/preview")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<AnnouncementPreviewResponse>> preview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Set<AnnouncementChannel> channels
    ) {
        Set<AnnouncementChannel> selected = channels != null && !channels.isEmpty()
                ? EnumSet.copyOf(channels)
                : EnumSet.allOf(AnnouncementChannel.class);
        AnnouncementPreviewResponse preview = queryService.preview(requireTenantId(user), selected);
        return ResponseEntity.ok(ApiResponse.ok("Announcement preview retrieved", preview));
    }

    /**
     * History with per-channel delivery stats (sent/delivered/failed/
     * skipped-no-optin) and the in-app read count per announcement.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<List<AnnouncementHistoryItem>>> history(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        List<AnnouncementHistoryItem> history = queryService.history(requireTenantId(user));
        return ResponseEntity.ok(ApiResponse.ok("Announcement history retrieved", history));
    }

    private UUID requireTenantId(AuthenticatedUser user) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant associated with this user");
        }
        return tenantId;
    }
}
