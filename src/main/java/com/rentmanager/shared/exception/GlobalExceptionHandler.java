package com.rentmanager.shared.exception;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerEntryNotFoundException;
import com.rentmanager.modules.rentledger.domain.exception.StkPushRateLimitedException;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaException;
import com.rentmanager.shared.error.ErrorTrackingService;
import com.rentmanager.shared.security.context.TenantContextNotBoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
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
    // RATE LIMITED (STK PUSH)
    // =========================================================
    @ExceptionHandler(StkPushRateLimitedException.class)
    public ResponseEntity<ApiResponse<Object>> handleStkPushRateLimited(
            StkPushRateLimitedException ex,
            HttpServletRequest request
    ) {

        errorTrackingService.capture(
                ex,
                "BUSINESS",
                "RATE_LIMITED",
                resolveModule(request),
                request,
                Map.of("type", "StkPushRateLimitedException")
        );

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiResponse.fail(ex.getMessage(), "RATE_LIMITED"));
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
    // TENANT CONTEXT NOT BOUND (SECURITY: 403, NOT 409/500)
    // =========================================================
    // A tenant-scoped operation ran with no tenant context bound at all —
    // the caller never resolved to any tenant (pending onboarder, renter
    // without validator authorisation, or a token shorn of tenant claims).
    // This is an authorization condition: 403, never a 5xx that reads as a
    // retryable server fault. Mirrors the frontend TenantMismatchError /403
    // contract (AUTH_TENANT_MISMATCH) for cross-persona requests.
    @ExceptionHandler(TenantContextNotBoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleTenantContextNotBound(
            TenantContextNotBoundException ex,
            HttpServletRequest request
    ) {

        errorTrackingService.capture(
                ex,
                "SECURITY",
                "TENANT_NOT_BOUND",
                resolveModule(request),
                request,
                Map.of("type", "TenantContextNotBoundException")
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("A tenant context is required for this operation", "TENANT_NOT_BOUND"));
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
    // DATA INTEGRITY (CONCURRENT RACE LOSERS)
    // =========================================================
    // Constraint violations are surfaced as 409 so a concurrent request
    // that loses a uniqueness race (e.g. the V52
    // uk_sub_payment_requests_pending guard that prevents two live STK
    // pushes for the same tenant) gets a clean "already in progress"
    // response instead of a misleading 500. The frontend already knows
    // SUBSCRIPTION_PAYMENT_ALREADY_PENDING from the pre-check path.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        String message = ex.getMessage() != null ? ex.getMessage() : "";
        boolean pendingSubscriptionRace = message.contains("uk_sub_payment_requests_pending");

        errorTrackingService.capture(
                ex,
                "BUSINESS",
                pendingSubscriptionRace ? "CONCURRENT_SUBSCRIPTION_PAYMENT" : "DATA_INTEGRITY",
                resolveModule(request),
                request,
                Map.of("constraint", pendingSubscriptionRace ? "uk_sub_payment_requests_pending" : "unknown")
        );

        if (pendingSubscriptionRace) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponse.fail(
                            "A subscription payment is already pending - complete the M-Pesa prompt on your phone",
                            "SUBSCRIPTION_PAYMENT_ALREADY_PENDING"));
        }

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(
                        "Operation conflicts with existing data - please retry",
                        "CONFLICT"));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleUploadTooLarge(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.fail("Files must be 5 MB or smaller.", "PAYLOAD_TOO_LARGE"));
    }

    // =========================================================
    // CLIENT REQUEST MISTAKES (4xx, never tracked)
    // =========================================================
    // These used to fall into the generic handler: an unknown path under a
    // public prefix (/api/v1/public/<anything>, disabled /v3/api-docs), a
    // wrong HTTP method or a malformed UUID answered 500 and wrote an
    // error_events row — so any unauthenticated scanner could fill that table
    // and bury real incidents. They are the caller's mistake, not ours.
    @ExceptionHandler({
            org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Object>> handleClientRequestMistake(Exception ex) {
        if (ex instanceof org.springframework.web.servlet.resource.NoResourceFoundException
                || ex instanceof org.springframework.web.servlet.NoHandlerFoundException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.fail("Not found", ErrorCode.RESOURCE_NOT_FOUND.name()));
        }
        if (ex instanceof org.springframework.web.HttpRequestMethodNotSupportedException) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                    .body(ApiResponse.fail("Method not allowed", "METHOD_NOT_ALLOWED"));
        }
        if (ex instanceof org.springframework.web.HttpMediaTypeNotSupportedException) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(ApiResponse.fail("Unsupported content type", "UNSUPPORTED_MEDIA_TYPE"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("Invalid request", ErrorCode.VALIDATION_ERROR.name()));
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

    /**
     * Payment-provider (Daraja) failures: 502 Bad Gateway so callers know
     * the provider — not the API contract — rejected the request, and the
     * exception message surfaces Safaricom's own error text (e.g. "Bad
     * Request - Invalid CallBackURL") for actionable frontend diagnostics.
     */
    @ExceptionHandler(DarajaException.class)
    public ResponseEntity<ApiResponse<Object>> handleDaraja(
            DarajaException ex,
            HttpServletRequest request
    ) {

        errorTrackingService.capture(
                ex,
                "SYSTEM",
                "DARAJA_ERROR",
                resolveModule(request),
                request,
                Map.of("type", ex.getClass().getSimpleName())
        );

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail(ex.getMessage(), "DARAJA_ERROR"));
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