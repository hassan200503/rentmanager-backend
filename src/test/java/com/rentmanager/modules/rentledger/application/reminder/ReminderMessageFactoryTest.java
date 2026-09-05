package com.rentmanager.modules.rentledger.application.reminder;

import com.rentmanager.modules.rentledger.domain.enums.ReminderMilestone;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderMessageFactoryTest {

    private static final LocalDate DUE = LocalDate.of(2026, 9, 5);
    private static final BigDecimal BALANCE = new BigDecimal("12500.00");

    /**
     * Claims the system cannot honour train renters to ignore the message.
     * There is no late-fee engine, no suspension, and no credit reporting, so
     * none of those words may appear.
     */
    @Test
    void noMessagePromisesAConsequenceTheSystemCannotDeliver() {
        String[] forbidden = {
                "late fee", "penalty", "suspend", "eviction", "evict",
                "credit score", "blacklist", "legal action", "court", "debt collector"
        };

        for (ReminderMilestone milestone : ReminderMilestone.values()) {
            String renter = ReminderMessageFactory
                    .forRenter(milestone, "Hassan Karungwa", "HWSW", BALANCE, "KES", DUE)
                    .body().toLowerCase(Locale.ROOT);
            String landlord = ReminderMessageFactory
                    .forLandlord(milestone, "Hassan Karungwa", "HWSW", BALANCE, "KES", DUE)
                    .body().toLowerCase(Locale.ROOT);

            for (String word : forbidden) {
                assertThat(renter).as("renter message at %s", milestone).doesNotContain(word);
                assertThat(landlord).as("landlord message at %s", milestone).doesNotContain(word);
            }
        }
    }

    /**
     * The landlord is billed per SMS segment. A body that runs a couple of
     * characters over 160 silently doubles their cost for that milestone
     * across every tenant they have.
     */
    @Test
    void everyMessageFitsInOneSmsSegment() {
        for (ReminderMilestone milestone : ReminderMilestone.values()) {
            String renter = ReminderMessageFactory
                    .forRenter(milestone, "Hassan Karungwa", "HWSW", BALANCE, "KES", DUE).body();
            String landlord = ReminderMessageFactory
                    .forLandlord(milestone, "Hassan Karungwa", "HWSW", BALANCE, "KES", DUE).body();

            assertThat(renter.length())
                    .as("renter message at %s: <%s>", milestone, renter)
                    .isLessThanOrEqualTo(160);
            assertThat(landlord.length())
                    .as("landlord message at %s: <%s>", milestone, landlord)
                    .isLessThanOrEqualTo(160);
        }
    }

    @Test
    void everyRenterMessageCarriesTheAmountTheUnitAndWhereToPay() {
        for (ReminderMilestone milestone : ReminderMilestone.values()) {
            String body = ReminderMessageFactory
                    .forRenter(milestone, "Hassan", "HWSW", BALANCE, "KES", DUE).body();

            assertThat(body).as("%s", milestone).contains("KSh 12,500.00");
            assertThat(body).as("%s", milestone).contains("Unit HWSW");
            assertThat(body).as("%s", milestone).contains("RentManager");
        }
    }

    @Test
    void usesFirstNameOnlyToKeepTheMessageShort() {
        String body = ReminderMessageFactory
                .forRenter(ReminderMilestone.DUE_TODAY, "Hassan Karungwa", "A1", BALANCE, "KES", DUE)
                .body();

        assertThat(body).startsWith("Hi Hassan,");
        assertThat(body).doesNotContain("Karungwa");
    }

    @Test
    void fallsBackToANeutralGreetingRatherThanAnEmptyGap() {
        String blank = ReminderMessageFactory
                .forRenter(ReminderMilestone.DUE_TODAY, "  ", "A1", BALANCE, "KES", DUE).body();
        String missing = ReminderMessageFactory
                .forRenter(ReminderMilestone.DUE_TODAY, null, "A1", BALANCE, "KES", DUE).body();

        assertThat(blank).startsWith("Hi there,");
        assertThat(missing).startsWith("Hi there,");
    }

    @Test
    void missingUnitNumberReadsAsAPlaceNotAnEmptyLabel() {
        String body = ReminderMessageFactory
                .forRenter(ReminderMilestone.DUE_TODAY, "Hassan", null, BALANCE, "KES", DUE).body();

        assertThat(body).contains("your unit");
        assertThat(body).doesNotContain("Unit ,");
        assertThat(body).doesNotContain("Unit  ");
    }

    @Test
    void dueTodayMessageDoesNotQuoteADateThatWouldReadAsTheFuture() {
        String body = ReminderMessageFactory
                .forRenter(ReminderMilestone.DUE_TODAY, "Hassan", "A1", BALANCE, "KES", DUE).body();

        assertThat(body).contains("due today");
        assertThat(body).doesNotContain("5 Sep 2026");
    }

    @Test
    void landlordMessageSingularisesASingleDay() {
        String oneDay = ReminderMessageFactory
                .forLandlord(ReminderMilestone.OVERDUE_1, "Hassan Karungwa", "A1", BALANCE, "KES", DUE)
                .body();
        String sevenDays = ReminderMessageFactory
                .forLandlord(ReminderMilestone.OVERDUE_7, "Hassan Karungwa", "A1", BALANCE, "KES", DUE)
                .body();

        assertThat(oneDay).contains("1 day late");
        assertThat(sevenDays).contains("7 days late");
    }

    @Test
    void landlordMessageNamesTheTenantInFullSoItIsActionable() {
        String body = ReminderMessageFactory
                .forLandlord(ReminderMilestone.OVERDUE_7, "Hassan Karungwa", "A1", BALANCE, "KES", DUE)
                .body();

        assertThat(body).startsWith("Hassan Karungwa in Unit A1");
    }

    @Test
    void landlordMessageDegradesGracefullyWithoutATenantName() {
        String body = ReminderMessageFactory
                .forLandlord(ReminderMilestone.OVERDUE_7, null, "A1", BALANCE, "KES", DUE).body();

        assertThat(body).startsWith("A tenant in Unit A1");
    }

    @Test
    void emailSubjectsDistinguishUpcomingFromOverdue() {
        assertThat(ReminderMessageFactory
                .forRenter(ReminderMilestone.T_MINUS_3, "Hassan", "A1", BALANCE, "KES", DUE).subject())
                .isEqualTo("Rent due 5 Sep 2026 — Unit A1");

        assertThat(ReminderMessageFactory
                .forRenter(ReminderMilestone.DUE_TODAY, "Hassan", "A1", BALANCE, "KES", DUE).subject())
                .isEqualTo("Rent due today — Unit A1");

        assertThat(ReminderMessageFactory
                .forRenter(ReminderMilestone.OVERDUE_3, "Hassan", "A1", BALANCE, "KES", DUE).subject())
                .isEqualTo("Rent overdue — Unit A1");
    }
}
