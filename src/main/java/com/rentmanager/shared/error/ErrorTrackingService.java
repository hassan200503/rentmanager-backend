package com.rentmanager.shared.error;

import com.rentmanager.shared.security.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ErrorTrackingService {

    private final ErrorEventRepository repository;

    public ErrorTrackingService(ErrorEventRepository repository) {
        this.repository = repository;
    }

    public void capture(
            Exception ex,
            String errorType,
            String errorCode,
            String module,
            HttpServletRequest request,
            Map<String, Object> metadata
    ) {
        String traceId = MDC.get("traceId");

        ErrorEvent event = ErrorEvent.of(
                traceId,
                TenantContext.getTenantIdOrNull(),
                TenantContext.getUserId(),
                module,
                errorType,
                errorCode,
                ex.getMessage(),
                request.getRequestURI(),
                request.getMethod(),
                metadata
        );

        repository.save(event);
    }
}