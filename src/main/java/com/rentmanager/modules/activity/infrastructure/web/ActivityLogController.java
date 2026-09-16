package com.rentmanager.modules.activity.infrastructure.web;

import com.rentmanager.modules.activity.application.ActivityLogService;
import com.rentmanager.modules.activity.domain.model.ActivityLog;
import com.rentmanager.shared.security.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    @GetMapping
    public List<ActivityLog> recent(@RequestParam(defaultValue = "20") int limit) {
        return activityLogService.recent(TenantContext.getTenantId(), limit);
    }

    @GetMapping("/paginated")
    public Page<ActivityLog> findAll(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String eventType,
            Pageable pageable
    ) {
        return activityLogService.findAll(TenantContext.getTenantId(), entityType, eventType, pageable);
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return activityLogService.subscribe(TenantContext.getTenantId());
    }
}
