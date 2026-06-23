package com.rentmanager.shared.error;

import com.rentmanager.shared.security.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ErrorTrackingService {

    private static final Logger log = LoggerFactory.getLogger(ErrorTrackingService.class);

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
        log.error("[{}] {} - {} {} : {}",
                errorType, errorCode,
                request.getMethod(), request.getRequestURI(),
                ex.getMessage(), ex);

        try {
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
        } catch (Exception saveEx) {
            log.error("Failed to persist error event: {}", saveEx.getMessage(), saveEx);
        }
    }
}