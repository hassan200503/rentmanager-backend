package com.rentmanager.modules.lease.application.dto.request;

import com.rentmanager.modules.lease.domain.enums.LeaseType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression guard for the LeaseTypeDTO/LeaseType drift bug found this
 * session (same class of bug as LeaseStatusDTOAlignmentTest, discovered
 * while checking LeaseApplicationService.map() for other latent
 * valueOf(domainEnum.name()) drift risks after the LeaseStatusDTO fix).
 * LeaseTypeDTO.valueOf(type.name()) throws IllegalArgumentException
 * whenever a LeaseType constant has no same-named LeaseTypeDTO
 * counterpart. STANDARD and MONTH_TO_MONTH were both missing at the time
 * this test was written -- any lease of one of those types would break
 * GET /api/v1/leases/{id} (LeaseDetailResponse mapping). All 10 real dev
 * leases happened to be FIXED_TERM at the time this was caught, so the bug
 * was latent, not yet live.
 *
 * This test doesn't re-verify the mapping logic itself (that's
 * LeaseApplicationService's job) -- it only asserts the two enums stay in
 * sync going forward, so a future LeaseType addition that forgets its
 * LeaseTypeDTO counterpart fails here at test time instead of in
 * production against real user data.
 */
class LeaseTypeDTOAlignmentTest {

    @Test
    void everyLeaseTypeHasAMatchingLeaseTypeDTOConstant() {
        for (LeaseType type : LeaseType.values()) {
            assertThatCode(() -> LeaseTypeDTO.valueOf(type.name()))
                    .as("LeaseType.%s has no matching LeaseTypeDTO constant -- "
                            + "LeaseApplicationService.map() will throw IllegalArgumentException "
                            + "(surfaced as HTTP 409) for any lease of this type", type.name())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void everyLeaseTypeDTOConstantCorrespondsToARealLeaseType() {
        // The inverse check: catches dead/orphaned DTO values like the
        // MONTHLY constant found this session, which had no corresponding
        // LeaseType and could never actually be produced by map() -- a
        // silent trap for anything built against it (e.g. a frontend
        // filter or select option list).
        for (LeaseTypeDTO dto : LeaseTypeDTO.values()) {
            assertThatCode(() -> LeaseType.valueOf(dto.name()))
                    .as("LeaseTypeDTO.%s has no matching LeaseType -- "
                            + "this DTO value can never be produced by map() and any "
                            + "usage of it will silently match nothing", dto.name())
                    .doesNotThrowAnyException();
        }
    }
}