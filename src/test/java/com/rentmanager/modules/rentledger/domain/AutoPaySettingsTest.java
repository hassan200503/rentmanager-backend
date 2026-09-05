package com.rentmanager.modules.rentledger.domain;

import com.rentmanager.modules.rentledger.domain.model.autopay.AutoPaySettings;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Domain unit tests for AutoPaySettings. No @Mock/@InjectMocks/MockitoExtension
 * per project convention -- this class needs no mocks at all, it's a pure
 * aggregate. Previously had zero test coverage; added alongside the
 * lastFailureReason field (this session's tenant-portal trust uplift) since
 * that field's exact clear/set/threshold semantics are the kind of behavior
 * a future change could silently break without a test pinning it down.
 */
class AutoPaySettingsTest {

    private AutoPaySettings newSettings() {
        return AutoPaySettings.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "+254712345678");
    }

    @Nested
    class RecordFailure {

        @Test
        void firstFailure_incrementsCounterAndStoresReason_staysEnabled() {
            AutoPaySettings settings = newSettings();
            settings.enable();

            settings.recordFailure("M-Pesa couldn't process the request. We'll retry automatically.");

            assertEquals(1, settings.getConsecutiveFailures());
            assertEquals("M-Pesa couldn't process the request. We'll retry automatically.", settings.getLastFailureReason());
            assertTrue(settings.isEnabled(), "should still be enabled after only 1 failure");
        }

        @Test
        void thirdConsecutiveFailure_autoDisables() {
            AutoPaySettings settings = newSettings();
            settings.enable();

            settings.recordFailure("attempt 1");
            settings.recordFailure("attempt 2");
            settings.recordFailure("auto-pay disabled after 3 failed attempts");

            assertEquals(3, settings.getConsecutiveFailures());
            assertFalse(settings.isEnabled(), "must auto-disable at the 3rd consecutive failure");
            assertEquals("auto-pay disabled after 3 failed attempts", settings.getLastFailureReason());
        }

        @Test
        void shouldRetry_isFalseOnceThresholdReached() {
            AutoPaySettings settings = newSettings();

            settings.recordFailure("1");
            assertTrue(settings.shouldRetry());
            settings.recordFailure("2");
            assertTrue(settings.shouldRetry());
            settings.recordFailure("3");
            assertFalse(settings.shouldRetry());
        }
    }

    @Nested
    class RecordSuccess {

        @Test
        void afterFailures_success_resetsCounterAndClearsReason() {
            AutoPaySettings settings = newSettings();
            settings.recordFailure("some transient failure");
            assertEquals(1, settings.getConsecutiveFailures());

            settings.recordSuccess();

            assertEquals(0, settings.getConsecutiveFailures());
            assertNull(settings.getLastFailureReason(),
                    "a successful run must clear any stale failure explanation");
        }

        @Test
        void success_recordsLastAutoPayDate() {
            AutoPaySettings settings = newSettings();

            settings.recordSuccess();

            assertEquals(java.time.LocalDate.now(), settings.getLastAutoPayDate());
        }
    }

    @Nested
    class Rehydration {

        @Test
        void rehydrate_roundTripsLastFailureReason() {
            UUID id = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            UUID leaseId = UUID.randomUUID();
            UUID profileId = UUID.randomUUID();

            AutoPaySettings settings = AutoPaySettings.rehydrate(
                    id, tenantId, leaseId, profileId, false, "+254700000000",
                    null, 2, java.time.LocalDateTime.now(),
                    "M-Pesa couldn't process the request. We'll retry automatically.",
                    5L
            );

            assertEquals("M-Pesa couldn't process the request. We'll retry automatically.", settings.getLastFailureReason());
            assertEquals(2, settings.getConsecutiveFailures());
            assertEquals(id, settings.getId());
        }
    }
}
