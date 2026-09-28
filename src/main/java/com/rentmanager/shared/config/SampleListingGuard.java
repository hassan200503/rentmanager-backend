package com.rentmanager.shared.config;

import com.rentmanager.modules.property.application.command.service.PropertyCommandService;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps demo listings out of the public listings page.
 *
 * <h2>Why this exists</h2>
 * The demo seeder on the {@code demo-data-seeder} branch creates properties
 * prefixed {@code "[Sample] "} so that nobody reading the database mistakes
 * one for a real vacancy. It seeds; it has no matching unseed. Once those rows
 * existed in the production database they outlived the branch that made them,
 * and {@code [Sample] Riverside Court} sat on the live listings page next to a
 * genuine property for days — advertising a two-bedroom in Nairobi at
 * KSh 45,000 that nobody can rent.
 *
 * <p>That is worse than untidy. The page tells renters "a unit is listed only
 * while it has no active lease, so the vacancies here are real", and a renter
 * who reserves a sample unit is being asked to send a deposit for a home that
 * does not exist. The owner could not remove it either: archiving a property
 * is a landlord-scoped command ({@code PropertyCommandController.archiveProperty}
 * takes the caller's own tenant), and the platform admin console can list
 * properties but has no archive of its own, so a platform owner has no way to
 * take down a listing that belongs to another organisation.
 *
 * <h2>What it does</h2>
 * At startup, outside the {@code demo} profile, any ACTIVE property whose name
 * begins with the seeder's own marker is archived through the ordinary domain
 * command — so the status transition, its domain event and the audit trail are
 * exactly what a landlord clicking "Archive" would produce. Nothing is
 * deleted, and a landlord or the seeder can activate it again.
 *
 * <h2>Why a startup guard rather than a migration</h2>
 * A Flyway migration would have to write {@code UPDATE properties SET status}
 * directly, bypassing the domain model, the archive validator and the event
 * that keeps the occupancy rollup honest. Doing it through the command service
 * costs one query per boot and keeps a single path to the invariant.
 *
 * <h2>Why it is safe to leave in place</h2>
 * The marker is a literal prefix the seeder applies to its own rows, and the
 * work is idempotent: once archived, later boots find nothing and do nothing.
 * Under the {@code demo} profile it is disabled entirely, so a demo
 * environment still shows its sample data. A landlord who genuinely wants a
 * property called "[Sample] ..." on the public page can set
 * {@code app.demo.archive-sample-listings=false}, which is a far cheaper
 * escape hatch than the alternative failure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleListingGuard {

    /**
     * The prefix the demo seeder puts on every row it creates. Changing it
     * here without changing it there silently stops this guard working.
     */
    private static final String SAMPLE_PREFIX = "[Sample] ";

    /**
     * Scanned per boot. The guard reads a page rather than every property so
     * a large portfolio cannot turn startup into a full table scan; demo rows
     * are seeded in small numbers and sort no lower than this.
     */
    private static final int SCAN_LIMIT = 500;

    private final PropertyRepository propertyRepository;
    private final PropertyCommandService propertyCommandService;
    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void archiveSampleListings() {
        if (environment.matchesProfiles("demo")) {
            log.info("Sample listing guard: demo profile active, leaving sample listings visible.");
            return;
        }
        // Integration tests boot the full context, so this listener fires in
        // every one of them. It has no business scanning or mutating a
        // fixture's data on the way past — a test that seeds a property and
        // finds it archived by unrelated startup code is a miserable thing to
        // debug — so it stands down under the test profile.
        if (environment.matchesProfiles("test")) {
            return;
        }
        if (!environment.getProperty("app.demo.archive-sample-listings", Boolean.class, true)) {
            log.info("Sample listing guard: disabled by app.demo.archive-sample-listings=false.");
            return;
        }

        final List<Property> samples = findActiveSamples();
        if (samples.isEmpty()) {
            return;
        }

        final List<String> archived = new ArrayList<>();
        for (Property property : samples) {
            try {
                propertyCommandService.archiveProperty(property.getTenantId(), property.getId());
                archived.add(property.getName());
            } catch (RuntimeException ex) {
                // One stubborn row must not stop the others, and must never
                // stop the application booting: a visible sample listing is a
                // problem, an API that will not start is a bigger one.
                log.warn("Sample listing guard: could not archive property {} ({})",
                        property.getId(), ex.getMessage());
            }
        }

        if (!archived.isEmpty()) {
            log.warn("Sample listing guard: archived {} demo listing(s) so they no longer appear "
                    + "on the public listings page: {}", archived.size(), archived);
        }
    }

    private List<Property> findActiveSamples() {
        try {
            final Page<Property> page = propertyRepository.findAll(PageRequest.of(0, SCAN_LIMIT));
            return page.getContent().stream()
                    .filter(p -> p.getStatus() == PropertyStatus.ACTIVE)
                    .filter(p -> p.getName() != null && p.getName().startsWith(SAMPLE_PREFIX))
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Sample listing guard: could not scan properties ({}).", ex.getMessage());
            return List.of();
        }
    }
}
