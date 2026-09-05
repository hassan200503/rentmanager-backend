package com.rentmanager.modules.tenant.domain.model;

import com.rentmanager.modules.tenant.domain.enums.CollectionMode;
import com.rentmanager.modules.tenant.domain.enums.TenantType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the direction this system must fail in when it cannot tell whether a
 * landlord is a custody arrangement.
 *
 * <p>Getting it wrong toward DIRECT costs a refused payment and a support
 * message. Getting it wrong toward PLATFORM_CUSTODY means collecting a
 * stranger's rent into the platform's own M-Pesa account without a CBK
 * licence or Safaricom's consent. The two errors are not comparable, so the
 * default is not a matter of taste and should not be "tidied" into a plain
 * field read.
 */
class CollectionModeDefaultsTest {

    private Tenant newTenant() {
        return Tenant.create(
                "T-001", "Acme Properties", "acme",
                "owner@example.com", "+254700000000",
                TenantType.STANDARD
        );
    }

    @Test
    void aNewLandlordCollectsDirectlyAndIsNeverPutIntoCustody() {
        Tenant tenant = newTenant();

        assertThat(tenant.getCollectionMode()).isEqualTo(CollectionMode.DIRECT);
        assertThat(tenant.collectsDirectly())
                .as("a landlord must never be opted into platform custody by default — "
                        + "that is the arrangement that requires a licence")
                .isTrue();
    }

    /**
     * The null case is the one that matters. A row written before V89, a
     * partially-hydrated aggregate, or a mapper that forgets the column all
     * produce null — and null must not read as "custody".
     */
    @Test
    void anUnknownCollectionModeReadsAsDirectNotCustody() {
        Tenant tenant = newTenant();

        // Force the unset state a pre-V89 row or an incomplete mapping gives.
        org.springframework.test.util.ReflectionTestUtils
                .setField(tenant, "collectionMode", null);

        assertThat(tenant.collectsDirectly())
                .as("null must fail closed toward DIRECT")
                .isTrue();
        assertThat(tenant.getCollectionMode())
                .as("the getter must not hand callers a null they will branch on")
                .isEqualTo(CollectionMode.DIRECT);
    }

    @Test
    void custodyIsOnlyEverReachedByAskingForItExplicitly() {
        Tenant tenant = newTenant();

        org.springframework.test.util.ReflectionTestUtils
                .setField(tenant, "collectionMode", CollectionMode.PLATFORM_CUSTODY);

        assertThat(tenant.collectsDirectly()).isFalse();
        assertThat(tenant.getCollectionMode()).isEqualTo(CollectionMode.PLATFORM_CUSTODY);
    }
}
