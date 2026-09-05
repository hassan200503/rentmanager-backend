package com.rentmanager.modules.rentledger.application.reminder;

import com.rentmanager.modules.rentledger.domain.enums.ReminderMilestone;
import com.rentmanager.modules.rentledger.domain.model.RentReminderPolicy;
import com.rentmanager.modules.rentledger.domain.repository.RentReminderPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads and writes a landlord's reminder cadence.
 *
 * <p>Always returns all six milestones, filling any the landlord has never
 * configured with {@link RentReminderPolicy#defaultFor}. The settings screen
 * renders a complete grid either way, and a landlord who sees five rows
 * because the sixth was never seeded has no way to discover the missing one
 * exists — or that it may be messaging their tenants.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentReminderPolicyService {

    private final RentReminderPolicyRepository policyRepository;

    /** The landlord's cadence, defaults filled in, in milestone order. */
    @Transactional(readOnly = true)
    public List<RentReminderPolicy> getCadence(UUID tenantId) {
        Map<ReminderMilestone, RentReminderPolicy> stored = new EnumMap<>(ReminderMilestone.class);
        for (RentReminderPolicy policy : policyRepository.findByTenant(tenantId)) {
            stored.put(policy.getMilestone(), policy);
        }

        List<RentReminderPolicy> cadence = new ArrayList<>(ReminderMilestone.values().length);
        for (ReminderMilestone milestone : ReminderMilestone.values()) {
            cadence.add(stored.getOrDefault(
                    milestone, RentReminderPolicy.defaultFor(tenantId, milestone)));
        }
        return cadence;
    }

    /**
     * Replaces the landlord's cadence with {@code requested}.
     *
     * <p>Any milestone the caller omits is written at its default rather than
     * left as it was. A partial update against a six-row grid is how a
     * landlord ends up with a cadence that does not match the one they were
     * shown when they pressed save.
     */
    @Transactional
    public List<RentReminderPolicy> updateCadence(
            UUID tenantId, List<RentReminderPolicy> requested) {

        Map<ReminderMilestone, RentReminderPolicy> byMilestone =
                new EnumMap<>(ReminderMilestone.class);
        for (RentReminderPolicy policy : requested) {
            byMilestone.put(policy.getMilestone(), policy);
        }

        List<RentReminderPolicy> complete = new ArrayList<>(ReminderMilestone.values().length);
        for (ReminderMilestone milestone : ReminderMilestone.values()) {
            complete.add(byMilestone.getOrDefault(
                    milestone, RentReminderPolicy.defaultFor(tenantId, milestone)));
        }

        List<RentReminderPolicy> saved = policyRepository.saveAll(tenantId, complete);

        log.info("Rent reminder cadence updated. tenantId={} enabledMilestones={}",
                tenantId, saved.stream().filter(RentReminderPolicy::isEnabled).count());

        return getCadence(tenantId);
    }
}
