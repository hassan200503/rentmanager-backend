package com.rentmanager.shared.exception;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.shared.error.ErrorTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DataIntegrityViolationException -> 409 mapping. Manual mock() per AGENTS.md.
 */
class GlobalExceptionHandlerDataIntegrityTest {

    private ErrorTrackingService errorTrackingService;
    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        errorTrackingService = mock(ErrorTrackingService.class);
        handler = new GlobalExceptionHandler(errorTrackingService);
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/tenants/123/subscription");
    }

    @Test
    void pendingGuardViolation_returns409AlreadyPending() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement; SQL [insert into subscription_payment_requests ...]; "
                        + "constraint [uk_sub_payment_requests_pending]"
        );

        ResponseEntity<ApiResponse<Object>> response = handler.handleDataIntegrityViolation(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ApiResponse<Object> body = response.getBody();
        assertNotNull(body);
        assertFalse(body.success());
        assertEquals("SUBSCRIPTION_PAYMENT_ALREADY_PENDING", body.errorCode());
    }

    @Test
    void otherConstraintViolation_returnsGeneric409Conflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"some_other_index\""
        );

        ResponseEntity<ApiResponse<Object>> response = handler.handleDataIntegrityViolation(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ApiResponse<Object> body = response.getBody();
        assertNotNull(body);
        assertFalse(body.success());
        assertEquals("CONFLICT", body.errorCode());
    }
}
