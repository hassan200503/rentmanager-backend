package com.rentmanager.modules.tenant.renter;

import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.application.RenterDirectoryService;
import com.rentmanager.modules.tenant.renter.application.RenterIdentityLinker;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real Postgres (V103) for renters a landlord entered themselves.
 *
 * Written after a wiring mistake got past the whole unit suite: a new @Query
 * inserted above {@code searchByNameOrPhone} stole that method's query, so
 * Spring tried to derive one from the name ("No property 'name'") and every
 * application context failed — while the list method silently inherited the
 * keyword search. Only starting Spring against a real schema catches that, so
 * this test exercises each repository method and the constraints behind them.
 */
// Rolled back after each test: this suite shares one reusable Postgres
// container, and rows left behind break other suites' cleanup (e.g.
// TenantSaaSIntegrationTest deletes from tenants).
@org.springframework.transaction.annotation.Transactional
class RenterDirectoryPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private RenterDirectoryService renters;
    @Autowired private RenterIdentityLinker linker;
    @Autowired private TenantProfileRepository profiles;
    @Autowired private TenantRepository tenants;
    @Autowired private JdbcTemplate jdbc;

    private UUID landlord;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        Tenant saved = tenants.save(Tenant.create("TEN-" + suffix, "Landlord", "l-" + suffix,
                "l@test.local", "+254700000000", TenantType.STANDARD));
        landlord = saved.getId();
    }

    @Test
    void aLandlordAddsATenantWhoHasNoAccount_andCanListAndSearchThem() {
        TenantProfile added = renters.add(landlord, "Amina Wanjiru", "0722123456", null, "ID-1");

        assertThat(added.isUnlinked()).isTrue();
        assertThat(added.getPhone()).isEqualTo("+254722123456");

        assertThat(renters.list(landlord, PageRequest.of(0, 20)).getContent())
                .extracting(TenantProfile::getId).containsExactly(added.getId());
        // The regression that started this test: search must keep its own query.
        assertThat(renters.search(landlord, "amina")).extracting(TenantProfile::getId).containsExactly(added.getId());
        assertThat(renters.search(landlord, "722123")).extracting(TenantProfile::getId).containsExactly(added.getId());
        assertThat(renters.search(landlord, "someone else")).isEmpty();
    }

    @Test
    void anotherLandlordNeverSeesThem() {
        renters.add(landlord, "Amina Wanjiru", "0722123456", null, null);

        String suffix = UUID.randomUUID().toString();
        UUID other = tenants.save(Tenant.create("TEN-" + suffix, "Other", "o-" + suffix,
                "o@test.local", "+254700000001", TenantType.STANDARD)).getId();

        assertThat(renters.list(other, PageRequest.of(0, 20)).getContent()).isEmpty();
        assertThat(renters.search(other, "amina")).isEmpty();
    }

    @Test
    void addingTheSamePhoneTwiceKeepsOneRecord_andTheDatabaseEnforcesItToo() {
        TenantProfile first = renters.add(landlord, "Amina Wanjiru", "0722123456", null, null);
        TenantProfile again = renters.add(landlord, "Amina W", "+254722123456", null, null);

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(renters.list(landlord, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);

        // Bypassing the service: the partial unique index is the real guarantee.
        // Inserted with SQL rather than through JPA, because inside a
        // transactional test a JPA save is not flushed until commit, so the
        // constraint would not fire where the assertion can see it.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO tenant_profile (id, tenant_id, clerk_user_id, full_name, phone, whatsapp_opt_in)
                VALUES (?, ?, NULL, 'Impostor', '+254722123456', FALSE)
                """, UUID.randomUUID(), landlord))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aRenterWithAnEmailIsClaimedOnFirstSignIn_caseInsensitively() {
        TenantProfile added = renters.add(landlord, "Amina Wanjiru", "0722123456", "Amina@Example.com", null);

        assertThat(profiles.findUnlinkedByEmail("amina@example.com"))
                .extracting(TenantProfile::getId).containsExactly(added.getId());

        assertThat(linker.linkByVerifiedEmail("user_amina", "amina@example.com")).hasSize(1);

        assertThat(profiles.findUnlinkedByEmail("amina@example.com")).isEmpty();
        assertThat(profiles.findAllByClerkUserId("user_amina"))
                .extracting(TenantProfile::getId).containsExactly(added.getId());
        assertThat(profiles.findUnlinkedByTenantIdAndPhone(landlord, "+254722123456")).isEmpty();
    }

    @Test
    void aRenterWithNoEmailIsNeverClaimedByAnybody() {
        renters.add(landlord, "Brian Otieno", "0110000000", null, null);

        assertThat(linker.linkByVerifiedEmail("user_brian", "brian@example.com")).isEmpty();
        assertThat(profiles.findUnlinkedByTenantIdAndPhone(landlord, "+254110000000")).isPresent();
    }
}
