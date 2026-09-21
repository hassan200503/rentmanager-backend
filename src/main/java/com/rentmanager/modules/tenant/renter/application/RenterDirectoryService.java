package com.rentmanager.modules.tenant.renter.application;

import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.shared.exception.BusinessException;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.phone.KenyanMsisdn;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The landlord's own book of renters.
 *
 * Before this existed, the only production code that created a renter was the
 * public reservation saga — a stranger reserving a vacant unit and paying a
 * deposit. A landlord arriving with tenants already in their units had no way
 * to enter them, so no lease, ledger or payment could be recorded for an
 * existing tenancy: the normal case in Kenya.
 *
 * Every read and write is scoped to the landlord passed in by the controller
 * from {@code TenantContext}; nothing here takes an organisation id from a
 * request body.
 */
@Service
@RequiredArgsConstructor
public class RenterDirectoryService {

    private final TenantProfileRepository tenantProfileRepository;

    /**
     * Records a renter under this landlord.
     *
     * The phone number is the identity that matters: it is how the renter is
     * reached, how M-Pesa reaches them, and the duplicate guard. Entering the
     * same person twice would split their rent history across two profiles,
     * which nobody notices until a balance is wrong — so a second attempt with
     * the same number returns the existing record rather than creating another.
     */
    @Transactional
    public TenantProfile add(
            UUID landlordTenantId,
            String fullName,
            String phone,
            String email,
            String nationalId
    ) {
        if (landlordTenantId == null) {
            throw new BusinessException("No organisation in context", ErrorCode.VALIDATION_ERROR);
        }
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessException("The renter's name is required", ErrorCode.VALIDATION_ERROR);
        }
        // KenyanMsisdn.toE164 normalises but never rejects: given "12345" it
        // returns "12345", and null for null. Validate against the pattern
        // first, or an unusable number is stored and the renter simply never
        // receives an M-Pesa prompt.
        if (phone == null || !phone.strip().matches(KenyanMsisdn.PATTERN)) {
            throw new BusinessException(KenyanMsisdn.MESSAGE, ErrorCode.VALIDATION_ERROR);
        }
        String normalisedPhone = KenyanMsisdn.toE164(phone);

        return tenantProfileRepository
                .findUnlinkedByTenantIdAndPhone(landlordTenantId, normalisedPhone)
                .orElseGet(() -> tenantProfileRepository.save(TenantProfile.createForLandlord(
                        landlordTenantId,
                        fullName,
                        normalisedPhone,
                        email,
                        nationalId,
                        "landlord-added-renter"
                )));
    }

    /** One page of this landlord's renters, by name. */
    @Transactional(readOnly = true)
    public Page<TenantProfile> list(UUID landlordTenantId, Pageable pageable) {
        return tenantProfileRepository.findAllByTenantId(landlordTenantId, pageable);
    }

    /**
     * Renters under this landlord matching a name or phone fragment. Backs the
     * picker on the lease form, which until now asked the landlord to type a
     * renter's UUID by hand — an id nothing in the product ever showed them.
     */
    @Transactional(readOnly = true)
    public List<TenantProfile> search(UUID landlordTenantId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return tenantProfileRepository.searchByNameOrPhone(landlordTenantId, keyword.trim());
    }
}
