package com.rentmanager.modules.tenant.renter.application;

import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Claims landlord-entered tenancies for the person they belong to, the first
 * time that person signs in.
 *
 * A landlord records a renter with a name, a phone number and (sometimes) an
 * email. That record has no Clerk identity. When the renter later signs up,
 * their session has an identity but no tenancy, so the portal would show
 * "no tenancy found" beside a tenancy their landlord is already managing.
 *
 * <h2>Why email, and only email</h2>
 * The email on the session is verified by Clerk before it appears in a token;
 * a phone number typed by a landlord is not verified by anyone, and matching on
 * it would let someone claim another person's rent history by entering their
 * number at sign-up. Only unlinked records are ever considered, so a profile
 * already held by an identity can never be re-pointed.
 *
 * Linking every match is deliberate: one person may rent from several
 * landlords, and each landlord holds a separate record for them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenterIdentityLinker {

    private final TenantProfileRepository tenantProfileRepository;

    /**
     * @return the records now held by this identity, empty when none matched
     */
    @Transactional
    public List<TenantProfile> linkByVerifiedEmail(String clerkUserId, String verifiedEmail) {
        if (clerkUserId == null || clerkUserId.isBlank()
                || verifiedEmail == null || verifiedEmail.isBlank()) {
            return List.of();
        }

        List<TenantProfile> unlinked = tenantProfileRepository.findUnlinkedByEmail(verifiedEmail.trim());
        if (unlinked.isEmpty()) {
            return List.of();
        }

        List<TenantProfile> linked = unlinked.stream()
                .map(profile -> {
                    profile.linkIdentity(clerkUserId);
                    return tenantProfileRepository.save(profile);
                })
                .toList();

        // Logged without the email: it identifies a person (PhoneMasker exists
        // for the same reason on phone numbers).
        log.info("Linked {} landlord-entered tenancy record(s) to a signed-in renter", linked.size());
        return linked;
    }
}
