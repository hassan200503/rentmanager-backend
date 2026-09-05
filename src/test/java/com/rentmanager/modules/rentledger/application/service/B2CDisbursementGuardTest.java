package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.audit.application.service.FinancialAuditService;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.infrastructure.daraja.DarajaB2CService;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.shared.observability.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two guards that close the payout hole.
 *
 * <p>Before these, {@code POST /api/v1/disbursements} sent whatever amount the
 * caller asked for to whatever phone number they typed, with no ceiling and no
 * check that the number belonged to the landlord. The role that could call it
 * is MANAGER — described in the project's own notes as a caretaker.
 */
class B2CDisbursementGuardTest {

    private DisbursementRepository disbursementRepository;
    private RentLedgerEntryRepository rentLedgerEntryRepository;
    private RentLedgerApplicationService rentLedgerApplicationService;
    private DarajaB2CService darajaB2CService;
    private TenantRepository tenantRepository;
    private DisbursementEntitlementService entitlementService;
    private B2CDisbursementService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();

    private static final String REGISTERED_PAYOUT_PHONE = "+254711000111";

    @BeforeEach
    void setUp() {
        disbursementRepository = mock(DisbursementRepository.class);
        rentLedgerEntryRepository = mock(RentLedgerEntryRepository.class);
        rentLedgerApplicationService = mock(RentLedgerApplicationService.class);
        darajaB2CService = mock(DarajaB2CService.class);
        tenantRepository = mock(TenantRepository.class);
        entitlementService = mock(DisbursementEntitlementService.class);

        when(disbursementRepository.save(any(Disbursement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // The charge is now read under a row lock before entitlement is
        // computed. The guards under test live behind that read, so it has to
        // resolve for any of them to be reached at all.
        RentLedgerEntry lockedEntry = mock(RentLedgerEntry.class);
        when(rentLedgerEntryRepository.findByIdAndTenantIdForUpdate(any(), any()))
                .thenReturn(Optional.of(lockedEntry));

        // A real transaction service, not a mock: the guards it now owns are
        // exactly what this class exists to prove, so they are exercised
        // through the same two-bean path production uses.
        DisbursementTransactionService transactionService = new DisbursementTransactionService(
                disbursementRepository, rentLedgerEntryRepository,
                tenantRepository, entitlementService,
                mock(FinancialAuditService.class), mock(BusinessMetrics.class));

        service = new B2CDisbursementService(
                disbursementRepository, rentLedgerEntryRepository,
                rentLedgerApplicationService, darajaB2CService,
                mock(FinancialAuditService.class), mock(BusinessMetrics.class),
                transactionService);
    }

    private Tenant landlordWithPayoutPhone(String phone) {
        Tenant landlord = mock(Tenant.class);
        when(landlord.getPayoutPhoneNumber()).thenReturn(phone);
        when(landlord.getName()).thenReturn("Karungwa Properties");
        return landlord;
    }

    // ── The destination is derived, never supplied ───────────────────────

    @Test
    void payoutGoesToTheRegisteredNumberNotAnythingTheCallerCouldSupply() {
        Tenant landlord = landlordWithPayoutPhone(REGISTERED_PAYOUT_PHONE);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        when(entitlementService.settleableAmount(tenantId, entryId))
                .thenReturn(new BigDecimal("14250"));
        when(darajaB2CService.initiateB2C(any(), any(), any(), any(), any()))
                .thenReturn("OCID-1");

        service.initiateDisbursement(
                tenantId, leaseId, entryId, new BigDecimal("5000"), "BusinessPayment", "Rent");

        verify(darajaB2CService).initiateB2C(
                eq(new BigDecimal("5000")),
                eq(REGISTERED_PAYOUT_PHONE),
                eq("Karungwa Properties"),
                eq("Rent"),
                eq("BusinessPayment"));
    }

    /**
     * Refusing outright is the right behaviour. Falling back to any other
     * number — the landlord's contact phone, the user's own — would be
     * guessing at where money should go.
     */
    @Test
    void aLandlordWithNoRegisteredPayoutNumberCannotDisburseAtAll() {
        Tenant landlord = landlordWithPayoutPhone(null);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));

        assertThatThrownBy(() -> service.initiateDisbursement(
                tenantId, leaseId, entryId, new BigDecimal("5000"), "BusinessPayment", "Rent"))
                .isInstanceOf(RentLedgerStateException.class)
                .hasMessageContaining("payout number");

        verify(darajaB2CService, never()).initiateB2C(any(), any(), any(), any(), any());
    }

    @Test
    void aBlankRegisteredPayoutNumberIsTreatedAsAbsent() {
        Tenant landlord = landlordWithPayoutPhone("   ");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));

        assertThatThrownBy(() -> service.initiateDisbursement(
                tenantId, leaseId, entryId, new BigDecimal("5000"), "BusinessPayment", "Rent"))
                .isInstanceOf(RentLedgerStateException.class);

        verify(darajaB2CService, never()).initiateB2C(any(), any(), any(), any(), any());
    }

    // ── The amount is capped at what is owed ─────────────────────────────

    @Test
    void requestingMoreThanIsOwedIsRefusedAndNoMoneyMoves() {
        Tenant landlord = landlordWithPayoutPhone(REGISTERED_PAYOUT_PHONE);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        when(entitlementService.settleableAmount(tenantId, entryId))
                .thenReturn(new BigDecimal("14250"));

        assertThatThrownBy(() -> service.initiateDisbursement(
                tenantId, leaseId, entryId, new BigDecimal("14250.01"), "BusinessPayment", "Rent"))
                .isInstanceOf(RentLedgerStateException.class)
                .hasMessageContaining("exceeds");

        verify(darajaB2CService, never()).initiateB2C(any(), any(), any(), any(), any());
        verify(disbursementRepository, never()).save(any(Disbursement.class));
    }

    @Test
    void requestingExactlyWhatIsOwedIsAllowed() {
        Tenant landlord = landlordWithPayoutPhone(REGISTERED_PAYOUT_PHONE);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        when(entitlementService.settleableAmount(tenantId, entryId))
                .thenReturn(new BigDecimal("14250"));
        when(darajaB2CService.initiateB2C(any(), any(), any(), any(), any()))
                .thenReturn("OCID-2");

        Disbursement result = service.initiateDisbursement(
                tenantId, leaseId, entryId, new BigDecimal("14250"), "BusinessPayment", "Rent");

        assertThat(result).isNotNull();
        verify(darajaB2CService).initiateB2C(any(), any(), any(), any(), any());
    }

    @Test
    void partialPayoutsAreAllowedBecauseAskingForLessIsAlwaysSafe() {
        Tenant landlord = landlordWithPayoutPhone(REGISTERED_PAYOUT_PHONE);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        when(entitlementService.settleableAmount(tenantId, entryId))
                .thenReturn(new BigDecimal("14250"));
        when(darajaB2CService.initiateB2C(any(), any(), any(), any(), any()))
                .thenReturn("OCID-3");

        service.initiateDisbursement(
                tenantId, leaseId, entryId, new BigDecimal("1000"), "BusinessPayment", "Rent");

        verify(darajaB2CService).initiateB2C(
                eq(new BigDecimal("1000")), any(), any(), any(), any());
    }

    /**
     * An entry that has produced nothing yet — no payment landed, or every
     * payment already paid out — has a ceiling of zero, so any positive
     * request fails.
     */
    @Test
    void nothingCanBeDisbursedAgainstAnEntryWithNoRemainingProceeds() {
        Tenant landlord = landlordWithPayoutPhone(REGISTERED_PAYOUT_PHONE);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        when(entitlementService.settleableAmount(tenantId, entryId))
                .thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.initiateDisbursement(
                tenantId, leaseId, entryId, new BigDecimal("0.01"), "BusinessPayment", "Rent"))
                .isInstanceOf(RentLedgerStateException.class);

        verify(darajaB2CService, never()).initiateB2C(any(), any(), any(), any(), any());
    }

    @Test
    void anUnknownLandlordCannotDisburse() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiateDisbursement(
                tenantId, leaseId, entryId, new BigDecimal("5000"), "BusinessPayment", "Rent"))
                .isInstanceOf(RentLedgerStateException.class);

        verify(darajaB2CService, never()).initiateB2C(any(), any(), any(), any(), any());
    }
}
