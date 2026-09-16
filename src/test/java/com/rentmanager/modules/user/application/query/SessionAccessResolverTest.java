package com.rentmanager.modules.user.application.query;

import com.rentmanager.modules.user.application.dto.response.SessionAccessResponse;
import com.rentmanager.modules.user.application.query.access.SessionAccessResolver;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SessionAccessResolverTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    private static List<SimpleGrantedAuthority> auth(String... names) {
        return Arrays.stream(names).map(SimpleGrantedAuthority::new).toList();
    }

    @Test
    void ownerIsLandlordWithOrganisation() {
        SessionAccessResponse r = SessionAccessResolver.resolve(USER, TENANT, auth("ROLE_LANDLORD", "ROLE_LANDLORD_OWNER"));
        assertThat(r.landlordRole()).isEqualTo("OWNER");
        assertThat(r.landlordTenantId()).isEqualTo(TENANT);
        assertThat(r.renter()).isFalse();
        assertThat(r.pendingOnboarding()).isFalse();
    }

    @Test
    void staffRoleIsReportedAsStaff() {
        SessionAccessResponse r = SessionAccessResolver.resolve(USER, TENANT, auth("ROLE_LANDLORD", "ROLE_LANDLORD_STAFF"));
        assertThat(r.landlordRole()).isEqualTo("STAFF");
    }

    @Test
    void renterOnlyReportsNoLandlordOrganisation() {
        SessionAccessResponse r = SessionAccessResolver.resolve(USER, TENANT, auth("ROLE_TENANT"));
        assertThat(r.renter()).isTrue();
        assertThat(r.landlordRole()).isNull();
        assertThat(r.landlordTenantId()).isNull();
    }

    @Test
    void landlordAndRenterAreAdditive() {
        SessionAccessResponse r = SessionAccessResolver.resolve(USER, TENANT,
                auth("ROLE_LANDLORD", "ROLE_LANDLORD_MANAGER", "ROLE_TENANT"));
        assertThat(r.landlordRole()).isEqualTo("MANAGER");
        assertThat(r.renter()).isTrue();
    }

    @Test
    void pendingOnboardingIsReported() {
        SessionAccessResponse r = SessionAccessResolver.resolve(USER, null, auth("ROLE_PENDING_ONBOARDING"));
        assertThat(r.pendingOnboarding()).isTrue();
        assertThat(r.landlordRole()).isNull();
        assertThat(r.renter()).isFalse();
    }

    @Test
    void landlordBindingWithoutFineGrainedRoleReportsNoOrganisation() {
        SessionAccessResponse r = SessionAccessResolver.resolve(USER, TENANT, auth("ROLE_LANDLORD"));
        assertThat(r.landlordRole()).isNull();
        assertThat(r.landlordTenantId()).isNull();
    }

    @Test
    void platformAdminIsReported() {
        SessionAccessResponse r = SessionAccessResolver.resolve(USER, null,
                auth("ROLE_PLATFORM_OWNER", "ROLE_PLATFORM_ADMIN", "ROLE_PENDING_ONBOARDING"));
        assertThat(r.platformAdmin()).isTrue();
    }
}
