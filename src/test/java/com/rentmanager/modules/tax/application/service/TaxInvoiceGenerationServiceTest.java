package com.rentmanager.modules.tax.application.service;

import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.tax.application.config.TaxProperties;
import com.rentmanager.modules.tax.domain.enums.VatTreatment;
import com.rentmanager.modules.tax.domain.event.TaxInvoiceGenerated;
import com.rentmanager.modules.tax.domain.model.TaxInvoice;
import com.rentmanager.modules.tax.domain.repository.TaxInvoiceRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaxInvoiceGenerationServiceTest {

    private TaxInvoiceRepository invoiceRepository;
    private DomainEventPublisher eventPublisher;
    private TaxProperties taxProperties;
    private TaxInvoiceGenerationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();
    private final UUID ledgerId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        invoiceRepository = mock(TaxInvoiceRepository.class);
        eventPublisher = mock(DomainEventPublisher.class);
        taxProperties = new TaxProperties();
        service = new TaxInvoiceGenerationService(invoiceRepository, eventPublisher, taxProperties);
    }

    private GenerateTaxInvoiceCommand command() {
        return new GenerateTaxInvoiceCommand(
                tenantId, transactionId, ledgerId, leaseId, null,
                "P000000000A", "P111111111K",
                PremisesType.RESIDENTIAL, VatTreatment.VAT_EXEMPT,
                new BigDecimal("15000.00"), "EXT-1", "MPESA",
                LocalDateTime.of(2026, 8, 3, 10, 30));
    }

    @Test
    void generate_createsInvoiceAndPublishesEvent() {
        when(invoiceRepository.existsByTenantIdAndRentTransactionId(tenantId, transactionId))
                .thenReturn(false);
        when(invoiceRepository.save(any(TaxInvoice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<TaxInvoice> result = service.generate(command());

        assertTrue(result.isPresent());
        assertEquals(com.rentmanager.modules.tax.domain.enums.TaxInvoiceStatus.PENDING,
                result.get().getStatus());
        verify(invoiceRepository).save(any(TaxInvoice.class));
        verify(eventPublisher).publish(any(TaxInvoiceGenerated.class));
    }

    @Test
    void generate_isIdempotentWhenInvoiceAlreadyExists() {
        when(invoiceRepository.existsByTenantIdAndRentTransactionId(tenantId, transactionId))
                .thenReturn(true);

        Optional<TaxInvoice> result = service.generate(command());

        assertTrue(result.isEmpty());
        verify(invoiceRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void generate_skipsWhenDisabled() {
        taxProperties.setInvoiceEnabled(false);

        when(invoiceRepository.existsByTenantIdAndRentTransactionId(tenantId, transactionId))
                .thenReturn(false);

        Optional<TaxInvoice> result = service.generate(command());

        assertTrue(result.isEmpty());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void generate_skipsNullCommand() {
        Optional<TaxInvoice> result = service.generate(null);

        assertTrue(result.isEmpty());
        verify(invoiceRepository, never()).save(any());
    }
}