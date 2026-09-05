package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.audit.application.service.FinancialAuditService;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.shared.observability.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The row lock that stops a charge being paid out twice.
 *
 * <p>{@code settleableAmount} is {@code netProceeds - committedPayouts}. Read
 * and write used to sit in one unlocked transaction, so under READ COMMITTED
 * two concurrent payout attempts on the same charge each read the same
 * {@code committedPayouts} — neither seeing the other's uncommitted row — and
 * both passed the cap. The schema did not catch it either: the only unique
 * index on {@code disbursements} is on the Daraja conversation id, which is
 * not assigned until after the call has already gone out.
 *
 * <p>Two things make the fix work, and they are tested in two places. That an
 * INITIATED row counts against the cap the moment it commits is
 * {@code DisbursementEntitlementServiceTest}'s. That the lock is actually
 * taken, and taken before the cap is read, is this class's — an ordering a
 * reader cannot confirm from the method body alone, since both calls look
 * like ordinary repository reads.
 */
class DisbursementTransactionServiceTest {

    private DisbursementRepository disbursementRepository;
    private RentLedgerEntryRepository rentLedgerEntryRepository;
    private TenantRepository tenantRepository;
    private DisbursementEntitlementService entitlementService;
    private DisbursementTransactionService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        disbursementRepository = mock(DisbursementRepository.class);
        rentLedgerEntryRepository = mock(RentLedgerEntryRepository.class);
        tenantRepository = mock(TenantRepository.class);
        entitlementService = mock(DisbursementEntitlementService.class);

        service = new DisbursementTransactionService(
                disbursementRepository, rentLedgerEntryRepository,
                tenantRepository, entitlementService,
                mock(FinancialAuditService.class), mock(BusinessMetrics.class));
    }

    @Test
    void takesTheRowLockOnTheChargeBeforeReadingWhatIsStillSettleable() {
        RentLedgerEntry lockedEntry = mock(RentLedgerEntry.class);
        Tenant landlord = mock(Tenant.class);

        when(rentLedgerEntryRepository.findByIdAndTenantIdForUpdate(entryId, tenantId))
                .thenReturn(Optional.of(lockedEntry));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        when(landlord.getPayoutPhoneNumber()).thenReturn("+254711000111");
        when(landlord.getName()).thenReturn("Karungwa Properties");
        when(entitlementService.settleableAmount(tenantId, entryId))
                .thenReturn(new BigDecimal("14250"));
        when(disbursementRepository.save(any(Disbursement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.reserveEntitlementAndCreate(
                tenantId, leaseId, entryId, new BigDecimal("14250"), "BusinessPayment");

        // Order is the whole point. Reading the cap first and locking after
        // would compile, pass a naive "is the lock taken" assertion, and still
        // leave the race wide open.
        InOrder order = inOrder(rentLedgerEntryRepository, entitlementService, disbursementRepository);
        order.verify(rentLedgerEntryRepository).findByIdAndTenantIdForUpdate(entryId, tenantId);
        order.verify(entitlementService).settleableAmount(tenantId, entryId);
        order.verify(disbursementRepository).save(any(Disbursement.class));
    }

    @Test
    void neverReadsTheCapThroughTheUnlockedFinderInstead() {
        RentLedgerEntry lockedEntry = mock(RentLedgerEntry.class);
        Tenant landlord = mock(Tenant.class);

        when(rentLedgerEntryRepository.findByIdAndTenantIdForUpdate(entryId, tenantId))
                .thenReturn(Optional.of(lockedEntry));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        when(landlord.getPayoutPhoneNumber()).thenReturn("+254711000111");
        when(landlord.getName()).thenReturn("Karungwa Properties");
        when(entitlementService.settleableAmount(tenantId, entryId))
                .thenReturn(new BigDecimal("9000"));
        when(disbursementRepository.save(any(Disbursement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.reserveEntitlementAndCreate(
                tenantId, leaseId, entryId, new BigDecimal("9000"), "BusinessPayment");

        // A later edit swapping this back to the plain finder would restore the
        // race silently — the method would still work, single-threaded.
        verify(rentLedgerEntryRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void refusesWithoutTouchingMoneyWhenTheChargeDoesNotResolveUnderTheLock() {
        when(rentLedgerEntryRepository.findByIdAndTenantIdForUpdate(entryId, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserveEntitlementAndCreate(
                tenantId, leaseId, entryId, new BigDecimal("14250"), "BusinessPayment"))
                .isInstanceOf(RentLedgerStateException.class);

        verifyNoInteractions(entitlementService);
        verify(disbursementRepository, never()).save(any(Disbursement.class));
    }

    @Test
    void theCreatedRowCarriesTheDerivedRecipientSoTheCallerCannotRedirectIt() {
        RentLedgerEntry lockedEntry = mock(RentLedgerEntry.class);
        Tenant landlord = mock(Tenant.class);

        when(rentLedgerEntryRepository.findByIdAndTenantIdForUpdate(entryId, tenantId))
                .thenReturn(Optional.of(lockedEntry));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        when(landlord.getPayoutPhoneNumber()).thenReturn("+254711000111");
        when(landlord.getName()).thenReturn("Karungwa Properties");
        when(entitlementService.settleableAmount(tenantId, entryId))
                .thenReturn(new BigDecimal("14250"));
        when(disbursementRepository.save(any(Disbursement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Disbursement created = service.reserveEntitlementAndCreate(
                tenantId, leaseId, entryId, new BigDecimal("14250"), "BusinessPayment");

        assertThat(created.getRecipientPhone()).isEqualTo("+254711000111");
        assertThat(created.getRecipientName()).isEqualTo("Karungwa Properties");
    }
}
