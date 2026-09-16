package com.rentmanager.modules.tax.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.tax.api.dto.MonthlyFilingResponse;
import com.rentmanager.modules.tax.api.dto.PropertyTaxRegistrationResponse;
import com.rentmanager.modules.tax.api.dto.TaxInvoiceResponse;
import com.rentmanager.modules.tax.api.dto.TaxSummaryResponse;
import com.rentmanager.modules.tax.application.dto.ErisPropertyRegistrationSubmission;
import com.rentmanager.modules.tax.application.port.ErisTransmissionPort;
import com.rentmanager.modules.tax.application.service.MriRatePolicyService;
import com.rentmanager.modules.tax.domain.model.MonthlyRentalIncomeFiling;
import com.rentmanager.modules.tax.domain.model.PropertyTaxRegistration;
import com.rentmanager.modules.tax.domain.model.TaxInvoice;
import com.rentmanager.modules.tax.domain.repository.MonthlyRentalIncomeFilingRepository;
import com.rentmanager.modules.tax.domain.repository.PropertyTaxRegistrationRepository;
import com.rentmanager.modules.tax.domain.repository.TaxInvoiceRepository;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only + light-command REST facade over the tax pipeline.
 *
 * <p>The backend pipeline (invoice generation, monthly filing computation)
 * runs automatically via the event listener and monthly scheduler. This
 * controller exposes what was computed so landlords can see their tax
 * position, mark invoices self-filed, initiate eRITS property registration,
 * and mark filings ready for manual submission.
 *
 * <p>KRA transmission adapters are Phase-1 stubs (NOT_AVAILABLE); the
 * lifecycle methods here are wired correctly so Phase-2/3 real adapters
 * drop in without controller changes.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tax")
public class TaxController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    private final TaxInvoiceRepository invoiceRepository;
    private final MonthlyRentalIncomeFilingRepository filingRepository;
    private final PropertyTaxRegistrationRepository registrationRepository;
    private final TenantRepository tenantRepository;
    private final MriRatePolicyService mriRatePolicyService;
    private final ErisTransmissionPort erisTransmissionPort;

    // ── Summary ──────────────────────────────────────────────────

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<TaxSummaryResponse>> getSummary(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID tenantId = requireTenantId(user);
        LocalDate today = LocalDate.now(NAIROBI);

        long attentionRequired = invoiceRepository.countAttentionRequiredByTenantId(tenantId);

        var rate = mriRatePolicyService.activeRateAsOf(today);

        Optional<MonthlyRentalIncomeFiling> latest = filingRepository.findLatestByTenantId(tenantId);

        LocalDate nextDeadline = today.withDayOfMonth(1).plusMonths(1).plusDays(4); // 5th of next month

        TaxSummaryResponse summary = new TaxSummaryResponse(
                attentionRequired,
                rate.getRatePercent(),
                latest.map(MonthlyRentalIncomeFiling::getPeriod).orElse(null),
                latest.map(MonthlyRentalIncomeFiling::getMriTaxDue).orElse(null),
                latest.map(MonthlyRentalIncomeFiling::getStatus).orElse(null),
                nextDeadline
        );

        return ResponseEntity.ok(ApiResponse.ok("Tax summary retrieved", summary));
    }

    // ── Invoices ─────────────────────────────────────────────────

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<List<TaxInvoiceResponse>>> listInvoices(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        UUID tenantId = requireTenantId(user);
        int safeSize = Math.min(size, 100);

        List<TaxInvoiceResponse> invoices = invoiceRepository
                .findAllByTenantId(tenantId, page, safeSize)
                .stream()
                .map(TaxInvoiceResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok("Tax invoices retrieved", invoices));
    }

    @PostMapping("/invoices/{invoiceId}/self-file")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    @Transactional
    public ResponseEntity<ApiResponse<TaxInvoiceResponse>> markSelfFiled(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID invoiceId
    ) {
        UUID tenantId = requireTenantId(user);

        TaxInvoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));

        invoice.markSelfFiled();
        TaxInvoice saved = invoiceRepository.save(invoice);

        return ResponseEntity.ok(ApiResponse.ok("Invoice marked self-filed", TaxInvoiceResponse.from(saved)));
    }

    // ── Monthly filings ───────────────────────────────────────────

    @GetMapping("/filings")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<List<MonthlyFilingResponse>>> listFilings(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        UUID tenantId = requireTenantId(user);
        int safeSize = Math.min(size, 60);

        List<MonthlyFilingResponse> filings = filingRepository
                .findAllByTenantId(tenantId, page, safeSize)
                .stream()
                .map(MonthlyFilingResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok("Monthly filings retrieved", filings));
    }

    @PostMapping("/filings/{filingId}/mark-manual")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    @Transactional
    public ResponseEntity<ApiResponse<MonthlyFilingResponse>> markFilingManual(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID filingId
    ) {
        UUID tenantId = requireTenantId(user);

        MonthlyRentalIncomeFiling filing = filingRepository.findById(filingId)
                .filter(f -> tenantId.equals(f.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Filing not found: " + filingId));

        filing.markReadyForManual();
        MonthlyRentalIncomeFiling saved = filingRepository.save(filing);

        return ResponseEntity.ok(ApiResponse.ok("Filing marked for manual submission", MonthlyFilingResponse.from(saved)));
    }

    // ── Property registrations ───────────────────────────────────

    @GetMapping("/properties/{propertyId}/registration")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<PropertyTaxRegistrationResponse>> getPropertyRegistration(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID propertyId
    ) {
        UUID tenantId = requireTenantId(user);

        Optional<PropertyTaxRegistration> registration =
                registrationRepository.findByTenantIdAndPropertyId(tenantId, propertyId);

        if (registration.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok("No registration found", null));
        }

        return ResponseEntity.ok(ApiResponse.ok(
                "Property registration retrieved",
                PropertyTaxRegistrationResponse.from(registration.get())));
    }

    @GetMapping("/registrations")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<List<PropertyTaxRegistrationResponse>>> listRegistrations(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID tenantId = requireTenantId(user);

        List<PropertyTaxRegistrationResponse> registrations = registrationRepository
                .findAllByTenantId(tenantId)
                .stream()
                .map(PropertyTaxRegistrationResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok("Property registrations retrieved", registrations));
    }

    /**
     * Initiates eRITS registration for a property. Phase 1 adapter returns
     * NOT_AVAILABLE so the registration is immediately parked as
     * READY_FOR_MANUAL — the landlord registers directly on eRITS.
     * Idempotent: re-calling returns the existing record.
     */
    @PostMapping("/properties/{propertyId}/registration")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    @Transactional
    public ResponseEntity<ApiResponse<PropertyTaxRegistrationResponse>> initiatePropertyRegistration(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID propertyId
    ) {
        UUID tenantId = requireTenantId(user);

        Optional<PropertyTaxRegistration> existing =
                registrationRepository.findByTenantIdAndPropertyId(tenantId, propertyId);
        if (existing.isPresent()) {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Registration already exists",
                    PropertyTaxRegistrationResponse.from(existing.get())));
        }

        String landlordKraPin = tenantRepository.findById(tenantId)
                .map(Tenant::getKraPin)
                .orElse(null);

        PropertyTaxRegistration registration = PropertyTaxRegistration.initiate(
                tenantId, propertyId, landlordKraPin, null);

        var result = erisTransmissionPort.registerProperty(new ErisPropertyRegistrationSubmission(
                registration.getId(), tenantId, landlordKraPin, null, propertyId));

        if (result.accepted()) {
            registration.markTransmitted();
            registration.markAccepted(result.ackReference());
        } else {
            // Phase 1 stub returns NOT_AVAILABLE; also catches REJECTED/TRANSPORT_FAILED
            registration.markReadyForManual();
        }

        PropertyTaxRegistration saved = registrationRepository.save(registration);

        return ResponseEntity.ok(ApiResponse.ok(
                "Property registration initiated",
                PropertyTaxRegistrationResponse.from(saved)));
    }

    private UUID requireTenantId(AuthenticatedUser user) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException(
                    "No tenant associated with this user. Please complete onboarding.");
        }
        return tenantId;
    }
}
