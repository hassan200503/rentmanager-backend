package com.rentmanager.shared.exception;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerEntryNotFoundException;
import com.rentmanager.shared.error.ErrorTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;
import com.rentmanager.shared.exception.ConflictException;
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ErrorTrackingService errorTrackingService;

    public GlobalExceptionHandler(ErrorTrackingService errorTrackingService) {
        this.errorTrackingService = errorTrackingService;
    }

    // =========================================================
    // BUSINESS EXCEPTION
    // =========================================================
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(
            BusinessException ex,
            HttpServletRequest request
    ) {

        errorTrackingService.capture(
                ex,
                "BUSINESS",
                ex.getErrorCode().name(),
                resolveModule(request),
                request,
                Map.of()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ex.getMessage(), ex.getErrorCode().name()));
    }

    // =========================================================
    // NOT FOUND
    // =========================================================
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {

        errorTrackingService.capture(
                ex,
                "NOT_FOUND",
                ex.getErrorCode().name(),
                resolveModule(request),
                request,
                Map.of()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage(), ex.getErrorCode().name()));
    }

    // =========================================================
    // PROPERTY NOT FOUND
    // =========================================================
    // FIX (this session): PropertyNotFoundException extends RuntimeException
    // directly, NOT the shared ResourceNotFoundException — so without this
    // handler, every "property not found" (including the correctly-enforced
    // cross-tenant case in PropertyQueryServiceImpl.getById()) fell through
    // to the generic Exception handler below and returned 500 Internal
    // Server Error with INTERNAL_ERROR/SYSTEM telemetry, instead of the
    // correct 404/NOT_FOUND/PROPERTY. Confirmed live via PropertyApiTest's
    // shouldEnforceTenantIsolation test, which had encoded the 500 as
    // expected behavior rather than catching it as a bug.
    //
    // NOTE: a cleaner long-term fix would be making PropertyNotFoundException
    // extend ResourceNotFoundException directly, so it's covered by the
    // handler above without a separate handler here — but that changes the
    // exception's type hierarchy, and other callers may catch it as a plain
    // RuntimeException elsewhere in the codebase (not audited this session).
    // This additive handler achieves the same correct HTTP/telemetry outcome
    // without that risk. Revisit consolidating the two exception types as a
    // separate, deliberate cleanup if desired.
    @ExceptionHandler(PropertyNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handlePropertyNotFound(
            PropertyNotFoundException ex,
            HttpServletRequest request
    ) {

        errorTrackingService.capture(
                ex,
                "NOT_FOUND",
                ex.getErrorCode().name(),
                resolveModule(request),
                request,
                Map.of()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage(), ex.getErrorCode().name()));
    }









    // =========================================================
    // RENT LEDGER ENTRY NOT FOUND
    // =========================================================
    // Same treatment as PropertyNotFoundException above: a dedicated
    // RuntimeException (not BusinessException) so a missing rent ledger
    // entry correctly returns 404/NOT_FOUND instead of falling into the
    // BusinessException handler's 400, which rent-ledger's other exception
    // (RentLedgerStateException) intentionally still uses for actual
    // invariant violations.
    @ExceptionHandler(RentLedgerEntryNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleRentLedgerEntryNotFound(
            RentLedgerEntryNotFoundException ex,
            HttpServletRequest request
    ) {

        errorTrackingService.capture(
                ex,
                "NOT_FOUND",
                ErrorCode.RENT_LEDGER_ENTRY_NOT_FOUND.name(),
                resolveModule(request),
                request,
                Map.of()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.RENT_LEDGER_ENTRY_NOT_FOUND.name()));
    }









    // =========================================================
    // VALIDATION
    // =========================================================
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(message, "VALIDATION_ERROR"));
    }

    // =========================================================
    // INVALID JSON
    // =========================================================
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("Malformed JSON request", "INVALID_JSON"));
    }

    // =========================================================
    // ❗ FIX: DUPLICATE / CONFLICT HANDLING (CRITICAL FOR YOUR TESTS)
    // =========================================================
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {

        errorTrackingService.capture(
                ex,
                "BUSINESS",
                "ILLEGAL_ARGUMENT",
                resolveModule(request),
                request,
                Map.of()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), "CONFLICT"));
    }

    // =========================================================
    // STATE CONFLICT
    // =========================================================
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request
    ) {

        errorTrackingService.capture(
                ex,
                "BUSINESS",
                "INVALID_STATE",
                resolveModule(request),
                request,
                Map.of("type", "IllegalStateException")
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), "INVALID_STATE"));
    }




    // =========================================================
// CONFLICT
// =========================================================
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Object>> handleConflict(
            ConflictException ex,
            HttpServletRequest request
    ) {

        errorTrackingService.capture(
                ex,
                "BUSINESS",
                "CONFLICT",
                resolveModule(request),
                request,
                Map.of()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), "CONFLICT"));
    }



    // =========================================================
    // ACCESS DENIED
    // =========================================================
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {

        errorTrackingService.capture(
                ex,
                "SECURITY",
                "ACCESS_DENIED",
                resolveModule(request),
                request,
                Map.of()
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("Access denied", "ACCESS_DENIED"));
    }

    // =========================================================
    // SECURITY EXCEPTION (CROSS-TENANT ACCESS DENIALS)
    // =========================================================
    // Distinct from Spring Security's AccessDeniedException above.
    // TenantCommandServiceImpl.validateTenantAccess() throws plain
    // java.lang.SecurityException for cross-tenant access attempts, which
    // — without this handler — previously fell through to the generic
    // Exception handler below and returned a misleading 500 Internal
    // Server Error for what is actually a 403 Forbidden authorization
    // failure. This handler corrects that for every existing caller of
    // validateTenantAccess() across TenantCommandServiceImpl, not just
    // newly-added endpoints.
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Object>> handleSecurityException(
            SecurityException ex,
            HttpServletRequest request
    ) {

        errorTrackingService.capture(
                ex,
                "SECURITY",
                "ACCESS_DENIED",
                resolveModule(request),
                request,
                Map.of("type", "SecurityException")
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("Access denied", "ACCESS_DENIED"));
    }

    // =========================================================
    // FALLBACK (KEEP LAST)
    // =========================================================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(
            Exception ex,
            HttpServletRequest request
    ) {

        errorTrackingService.capture(
                ex,
                "SYSTEM",
                "INTERNAL_ERROR",
                resolveModule(request),
                request,
                Map.of("type", ex.getClass().getSimpleName())
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("Internal server error", "INTERNAL_ERROR"));
    }

    // =========================================================
    // SaaS MODULE RESOLUTION (FIX: REMOVE HARDCODED LEASE)
    // =========================================================
    private String resolveModule(HttpServletRequest request) {

        String uri = request.getRequestURI();

        if (uri.contains("/properties")) return "PROPERTY";
        if (uri.contains("/leases")) return "LEASE";
        if (uri.contains("/tenants")) return "TENANT";
        if (uri.contains("/rent-ledger")) return "RENT_LEDGER";

        return "SYSTEM";
    }

}