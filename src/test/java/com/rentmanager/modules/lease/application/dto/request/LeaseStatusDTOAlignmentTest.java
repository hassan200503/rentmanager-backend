package com.rentmanager.modules.lease.application.dto.request;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression guard for the LeaseStatusDTO/LeaseStatus drift bug found this
 * session: LeaseApplicationService.map(LeaseStatus) does
 * LeaseStatusDTO.valueOf(status.name()), which throws
 * IllegalArgumentException (surfaced to callers as a 409) whenever a
 * LeaseStatus constant has no same-named LeaseStatusDTO counterpart.
 * PENDING_ACTIVATION, CANCELLED, and SUSPENDED were all missing at the time
 * this test was written -- any lease sitting in one of those statuses broke
 * GET /api/v1/leases entirely for that tenant.
 *
 * This test doesn't re-verify the mapping logic itself (that's
 * LeaseApplicationService's job) -- it only asserts the two enums stay in
 * sync going forward, so a future LeaseStatus addition that forgets its
 * LeaseStatusDTO counterpart fails here at test time instead of in
 * production against real user data.
 */
class LeaseStatusDTOAlignmentTest {

    @Test
    void everyLeaseStatusHasAMatchingLeaseStatusDTOConstant() {
        for (LeaseStatus status : LeaseStatus.values()) {
            assertThatCode(() -> LeaseStatusDTO.valueOf(status.name()))
                    .as("LeaseStatus.%s has no matching LeaseStatusDTO constant -- "
                            + "LeaseApplicationService.map() will throw IllegalArgumentException "
                            + "(surfaced as HTTP 409) for any lease in this status", status.name())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void everyLeaseStatusDTOConstantCorrespondsToARealLeaseStatus() {
        // The inverse check: catches dead/orphaned DTO values like the
        // REJECTED constant found this session, which had no corresponding
        // LeaseStatus and could never actually be produced by map() -- a
        // silent trap for any status filter built against it.
        for (LeaseStatusDTO dto : LeaseStatusDTO.values()) {
            assertThatCode(() -> LeaseStatus.valueOf(dto.name()))
                    .as("LeaseStatusDTO.%s has no matching LeaseStatus -- "
                            + "this DTO value can never be produced by map() and any "
                            + "filter using it will silently match nothing", dto.name())
                    .doesNotThrowAnyException();
        }
    }
}