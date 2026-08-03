package com.rentmanager.modules.tax.application.service;

import com.rentmanager.modules.tax.application.config.TaxProperties;
import com.rentmanager.modules.tax.domain.event.TaxInvoiceGenerated;
import com.rentmanager.modules.tax.domain.model.TaxInvoice;
import com.rentmanager.modules.tax.domain.repository.TaxInvoiceRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Generates one eTIMS tax invoice per rent payment, idempotently.
 *
 * <p>Idempotency: existence of a (tenant, transaction) invoice short-
 * circuits generation — replaying the same payment event (duplicate M-Pesa
 * callback, saga retry) never creates a second invoice. The DB unique
 * constraint is the backstop.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxInvoiceGenerationService {

    private final TaxInvoiceRepository invoiceRepository;
    private final DomainEventPublisher eventPublisher;
    private final TaxProperties taxProperties;

    /**
     * @return the freshly generated invoice, or empty if one already exists
     *         for the transaction (idempotent) or invoice generation is
     *         disabled.
     */
    @Transactional
    public Optional<TaxInvoice> generate(GenerateTaxInvoiceCommand command) {
        if (!taxProperties.isInvoiceEnabled() || command == null) {
            return Optional.empty();
        }
        if (invoiceRepository.existsByTenantIdAndRentTransactionId(
                command.tenantId(), command.rentTransactionId())) {
            return Optional.empty();
        }

        TaxInvoice invoice = command.toInvoice();
        TaxInvoice saved = invoiceRepository.save(invoice);

        eventPublisher.publish(new TaxInvoiceGenerated(
                saved.getTenantId(),
                saved.getId(),
                "INV-" + saved.getId(),
                saved.getRentTransactionId(),
                saved.getAmount(),
                saved.getVatTreatment().name()
        ));

        log.info("Generated tax invoice {} for transaction {} ({} {} on {})",
                saved.getId(), saved.getRentTransactionId(),
                saved.getVatTreatment(), saved.getPremisesType(), saved.getOccurredAt());

        return Optional.of(saved);
    }
}
