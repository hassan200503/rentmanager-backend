package com.rentmanager.modules.lease.application;

import com.rentmanager.modules.lease.application.dto.request.LeaseActionType;
import com.rentmanager.modules.lease.application.service.LeaseActionPolicy;
import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.enums.TerminationType;
import com.rentmanager.modules.lease.domain.model.Lease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves LeaseActionPolicy against the real aggregate: every action the policy
 * offers for a status succeeds on a Lease in that status, and every
 * user-invokable action it does NOT offer is refused. If a domain guard
 * changes, this fails rather than letting clients show a button the backend
 * rejects (or hide one it would accept).
 */
class LeaseActionPolicyTest {

    private static final EnumSet<LeaseActionType> USER_ACTIONS = EnumSet.complementOf(EnumSet.of(LeaseActionType.EXPIRE));

    private Lease leaseIn(LeaseStatus status) {
        return Lease.restore(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "LSE-" + UUID.randomUUID(), LeaseType.FIXED_TERM, BillingCycle.MONTHLY,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("15000.00"), new BigDecimal("15000.00"), status);
    }

    private void perform(Lease lease, LeaseActionType action) {
        switch (action) {
            case APPROVE -> lease.approve();
            case AWAITING_DEPOSIT -> lease.markAwaitingDeposit();
            case ACTIVATE -> lease.activate();
            case REJECT -> lease.reject("not suitable");
            case TERMINATE -> lease.terminate(TerminationType.MUTUAL_AGREEMENT, "moving out", "owner@test", lease.getTenantId());
            case RENEW -> lease.renew(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), lease.getTenantId(), "owner@test");
            case CANCEL -> lease.cancel("fell through");
            case EXPIRE -> lease.expire();
        }
    }

    @ParameterizedTest
    @EnumSource(LeaseStatus.class)
    void offeredActionsSucceedAndOthersAreRefused(LeaseStatus status) {
        List<LeaseActionType> offered = LeaseActionPolicy.allowedActions(status);
        for (LeaseActionType action : USER_ACTIONS) {
            Lease lease = leaseIn(status);
            if (offered.contains(action)) {
                assertThatCode(() -> perform(lease, action))
                        .as("%s should be allowed from %s", action, status)
                        .doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> perform(lease, action))
                        .as("%s should be refused from %s", action, status)
                        .isInstanceOf(RuntimeException.class);
            }
        }
    }

    @Test
    void anOccupiedRenewedLeaseCannotBeCancelled() {
        Lease lease = leaseIn(LeaseStatus.RENEWED);
        assertThatThrownBy(() -> lease.cancel("oops")).isInstanceOf(RuntimeException.class);
        assertThat(lease.getStatus()).isEqualTo(LeaseStatus.RENEWED);
    }

    @ParameterizedTest
    @EnumSource(value = LeaseStatus.class, names = {"TERMINATED", "EXPIRED", "CANCELLED"})
    void anEndedLeaseCannotBeCancelled(LeaseStatus ended) {
        Lease lease = leaseIn(ended);
        assertThatThrownBy(() -> lease.cancel("after the fact")).isInstanceOf(RuntimeException.class);
        assertThat(lease.getStatus()).isEqualTo(ended);
    }

    @Test
    void reservationCreatedLeaseCanStillBeCompensated() {
        Lease lease = leaseIn(LeaseStatus.PENDING_ACTIVATION);
        lease.cancel("Reservation fulfillment failed");
        assertThat(lease.getStatus()).isEqualTo(LeaseStatus.CANCELLED);
    }

    @Test
    void schedulerOnlyExpiryIsNeverOffered() {
        for (LeaseStatus s : LeaseStatus.values()) {
            assertThat(LeaseActionPolicy.allowedActions(s)).doesNotContain(LeaseActionType.EXPIRE);
        }
    }
}
