package com.rentmanager.demo;

import com.rentmanager.modules.property.application.command.service.PropertyCommandService;
import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.property.domain.valueobject.Address;
import com.rentmanager.modules.unit.application.command.service.UnitCommandService;
import com.rentmanager.modules.unit.application.dto.request.CreateUnitRequest;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Puts a small set of clearly-labelled sample listings in front of a visitor,
 * so the public listings page is not empty for someone evaluating the product.
 *
 * <h2>Why this exists rather than a SQL script</h2>
 * Hand-written INSERTs would bypass the aggregates, the tenant scoping and the
 * domain events that the rest of this codebase exists to enforce, and they
 * would rot the first time a migration changed a column. This goes through the
 * same application services the landlord dashboard calls, so whatever a
 * landlord could not create by hand, this cannot create either.
 *
 * <h2>Safety</h2>
 * Three separate things must be true before a single row is written: the
 * {@code demo} profile must be active, {@code app.demo.seed.enabled} must be
 * {@code true}, and a landlord organisation must be named explicitly in
 * {@code app.demo.tenant-id}. Nothing is invented: the organisation and the
 * acting user must already exist, because fabricating identity rows would put
 * this database out of step with Clerk.
 *
 * <p>It is also idempotent. Every property is looked up by name first, so a
 * restart — and on a free-tier container there are many — re-runs this without
 * creating a second copy of anything.
 *
 * <p>Each listing is prefixed {@value #SAMPLE_PREFIX} so that nobody, browsing
 * or reading the database, mistakes it for a real vacancy.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** Marks every seeded row as a sample, in the listing and in the database. */
    static final String SAMPLE_PREFIX = "[Sample] ";

    private final PropertyCommandService propertyCommandService;
    private final UnitCommandService unitCommandService;
    private final PropertyRepository propertyRepository;

    @Value("${app.demo.seed.enabled:false}")
    private boolean enabled;

    @Value("${app.demo.tenant-id:}")
    private String tenantIdRaw;

    @Value("${app.demo.user-id:}")
    private String userIdRaw;

    public DemoDataSeeder(PropertyCommandService propertyCommandService,
                          UnitCommandService unitCommandService,
                          PropertyRepository propertyRepository) {
        this.propertyCommandService = propertyCommandService;
        this.unitCommandService = unitCommandService;
        this.propertyRepository = propertyRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Demo seeding is off (app.demo.seed.enabled=false). Nothing written.");
            return;
        }
        if (!StringUtils.hasText(tenantIdRaw) || !StringUtils.hasText(userIdRaw)) {
            log.warn("Demo seeding is enabled but app.demo.tenant-id / app.demo.user-id are "
                    + "not set. Refusing to guess which landlord owns the samples.");
            return;
        }

        final UUID tenantId;
        final UUID userId;
        try {
            tenantId = UUID.fromString(tenantIdRaw.trim());
            userId = UUID.fromString(userIdRaw.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("Demo seeding skipped: app.demo.tenant-id / app.demo.user-id are not UUIDs.");
            return;
        }

        int created = 0;
        for (SampleProperty sample : SAMPLES) {
            try {
                if (seed(tenantId, userId, sample)) {
                    created++;
                }
            } catch (RuntimeException ex) {
                // One bad sample must not stop the application from starting.
                log.warn("Demo seeding skipped '{}': {}", sample.name(), ex.getMessage());
            }
        }
        log.info("Demo seeding finished. {} of {} sample properties created this run.",
                created, SAMPLES.size());
    }

    /** @return true when this run actually created the property. */
    private boolean seed(UUID tenantId, UUID userId, SampleProperty sample) {
        String name = SAMPLE_PREFIX + sample.name();

        if (propertyRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            log.debug("Sample property '{}' already present; leaving it alone.", name);
            return false;
        }

        CreatePropertyRequest request = new CreatePropertyRequest();
        request.setName(name);
        request.setPropertyType(sample.type());
        request.setDescription(sample.description());
        request.setAddress(Address.builder()
                .streetAddress(sample.street())
                .city(sample.city())
                .state(sample.county())
                .country("Kenya")
                .build());

        PropertyResponse property = propertyCommandService.createProperty(tenantId, userId, request);
        UUID propertyId = property.getPropertyId();

        // A property is created as DRAFT; only an ACTIVE one is publicly visible.
        propertyCommandService.activateProperty(tenantId, propertyId);

        for (SampleUnit unit : sample.units()) {
            CreateUnitRequest unitRequest = new CreateUnitRequest();
            unitRequest.setPropertyId(propertyId);
            unitRequest.setUnitNumber(unit.number());
            unitRequest.setLabel(unit.label());
            unitRequest.setRentAmount(unit.rent());
            unitRequest.setDepositAmount(unit.rent());

            UnitResponse createdUnit = unitCommandService.create(tenantId, unitRequest);
            unitCommandService.activate(tenantId, createdUnit.getId(), correlationId());
        }

        log.info("Seeded sample property '{}' with {} unit(s).", name, sample.units().size());
        return true;
    }

    private static String correlationId() {
        return "demo-seed-" + UUID.randomUUID();
    }

    // ── the samples ──────────────────────────────────────────────────────────

    private record SampleUnit(String number, String label, BigDecimal rent) {}

    private record SampleProperty(String name,
                                  PropertyType type,
                                  String street,
                                  String city,
                                  String county,
                                  String description,
                                  List<SampleUnit> units) {}

    private static final List<SampleProperty> SAMPLES = List.of(
            new SampleProperty(
                    "Riverside Court",
                    PropertyType.APARTMENT,
                    "Riverside Drive",
                    "Nairobi",
                    "Nairobi",
                    "Sample listing used to demonstrate the platform. Two-bedroom apartments "
                            + "with borehole water and a backup generator.",
                    List.of(new SampleUnit("A1", "2 bedroom", new BigDecimal("45000")),
                            new SampleUnit("A2", "2 bedroom", new BigDecimal("45000")),
                            new SampleUnit("B1", "1 bedroom", new BigDecimal("32000")))),

            new SampleProperty(
                    "Nyali Palm Residences",
                    PropertyType.APARTMENT,
                    "Links Road",
                    "Mombasa",
                    "Mombasa",
                    "Sample listing used to demonstrate the platform. Sea-facing apartments a "
                            + "short walk from the beach.",
                    List.of(new SampleUnit("12", "3 bedroom", new BigDecimal("70000")),
                            new SampleUnit("14", "2 bedroom", new BigDecimal("52000")))),

            new SampleProperty(
                    "Milimani Studios",
                    PropertyType.STUDIO,
                    "Milimani Road",
                    "Kisumu",
                    "Kisumu",
                    "Sample listing used to demonstrate the platform. Self-contained studios "
                            + "with water and rubbish collection included.",
                    List.of(new SampleUnit("S1", "Studio", new BigDecimal("18000")),
                            new SampleUnit("S2", "Studio", new BigDecimal("18000")),
                            new SampleUnit("S3", "Studio", new BigDecimal("19500")))),

            new SampleProperty(
                    "Kefinco Bedsitters",
                    PropertyType.BEDSITTER,
                    "Kefinco Estate",
                    "Kakamega",
                    "Kakamega",
                    "Sample listing used to demonstrate the platform. Bedsitters on a secured "
                            + "compound, ten minutes from town.",
                    List.of(new SampleUnit("7", "Bedsitter", new BigDecimal("9000")),
                            new SampleUnit("8", "Bedsitter", new BigDecimal("9000"))))
    );
}
