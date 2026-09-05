package com.rentmanager.modules.rentledger.application.reminder;

import com.rentmanager.modules.rentledger.domain.enums.ReminderMilestone;
import com.rentmanager.shared.util.money.MoneyFormatter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Composes the text of every rent reminder.
 *
 * <h2>One place, twelve messages</h2>
 * Six milestones times two audiences. {@code SmsService} carries typed
 * methods for two of them and nothing for the rest, so extending that
 * interface would have meant ten more single-use methods on a shared port.
 * Composing here and dispatching through {@code NotificationDispatchService}
 * keeps the wording in one file where it can be read as a set — which is the
 * only way to notice that the tone escalates sensibly from day −7 to day +7.
 *
 * <h2>Nothing is claimed that the system cannot do</h2>
 * No late fee is mentioned: there is no late-fee engine, and threatening one
 * that will never be charged trains renters to ignore the message. No account
 * suspension, no credit reporting, no legal language. What each message does
 * say is the amount, the unit, the date, and where to pay — which is the
 * information a renter actually needs in order to act.
 *
 * <h2>SMS is billed by the segment</h2>
 * Every SMS body is written to fit inside one 160-character GSM segment for
 * realistic inputs. The landlord pays per segment, so a message that runs two
 * characters over doubles their cost for that milestone across every tenant.
 */
public final class ReminderMessageFactory {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private ReminderMessageFactory() {
        // utility class — not instantiable
    }

    /** A rendered message: subject is used by email, ignored by SMS. */
    public record Message(String subject, String body) {}

    public static Message forRenter(
            ReminderMilestone milestone,
            String renterName,
            String unitNumber,
            BigDecimal balanceOwed,
            String currency,
            LocalDate dueDate
    ) {
        String name = firstName(renterName);
        String amount = MoneyFormatter.format(balanceOwed, currency);
        String date = dueDate.format(DATE);
        String unit = unitLabel(unitNumber);

        String body = switch (milestone) {
            case T_MINUS_7 -> "Hi %s, your rent of %s for %s is due on %s. You can pay anytime in the RentManager portal."
                    .formatted(name, amount, unit, date);
            case T_MINUS_3 -> "Hi %s, your rent of %s for %s is due in 3 days, on %s. Pay in the RentManager portal."
                    .formatted(name, amount, unit, date);
            case DUE_TODAY -> "Hi %s, your rent of %s for %s is due today. Pay now in the RentManager portal."
                    .formatted(name, amount, unit);
            case OVERDUE_1 -> "Hi %s, your rent of %s for %s was due on %s and is 1 day late. Pay in the RentManager portal."
                    .formatted(name, amount, unit, date);
            case OVERDUE_3 -> "Hi %s, your rent of %s for %s is 3 days late (due %s). Please pay in the RentManager portal."
                    .formatted(name, amount, unit, date);
            case OVERDUE_7 -> "Hi %s, your rent of %s for %s is 7 days late (due %s). Pay in the RentManager portal or contact your landlord."
                    .formatted(name, amount, unit, date);
        };

        String subject = switch (milestone) {
            case T_MINUS_7, T_MINUS_3 -> "Rent due %s — %s".formatted(date, unit);
            case DUE_TODAY -> "Rent due today — %s".formatted(unit);
            case OVERDUE_1, OVERDUE_3, OVERDUE_7 ->
                    "Rent overdue — %s".formatted(unit);
        };

        return new Message(subject, body);
    }

    /**
     * The landlord-facing message. Sent only at milestones where the policy
     * sets {@code notifyLandlord} — by default just {@code OVERDUE_7}, the
     * point at which a human decision is actually needed.
     */
    public static Message forLandlord(
            ReminderMilestone milestone,
            String renterName,
            String unitNumber,
            BigDecimal balanceOwed,
            String currency,
            LocalDate dueDate
    ) {
        String amount = MoneyFormatter.format(balanceOwed, currency);
        String date = dueDate.format(DATE);
        String unit = unitLabel(unitNumber);
        int daysLate = Math.max(milestone.dayOffset(), 0);

        String body = daysLate > 0
                ? "%s in %s is %d day%s late on rent of %s (due %s). — RentManager"
                        .formatted(safeName(renterName), unit, daysLate,
                                   daysLate == 1 ? "" : "s", amount, date)
                : "%s in %s has rent of %s due on %s. — RentManager"
                        .formatted(safeName(renterName), unit, amount, date);

        String subject = daysLate > 0
                ? "%s — rent %d day%s late".formatted(unit, daysLate, daysLate == 1 ? "" : "s")
                : "%s — rent due %s".formatted(unit, date);

        return new Message(subject, body);
    }

    /**
     * First name only, for SMS length. Falls back to a neutral greeting
     * rather than an empty gap when the profile has no usable name — "Hi ,"
     * reads as a broken system and undermines the message it is carrying.
     */
    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "there";
        }
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private static String safeName(String fullName) {
        return (fullName == null || fullName.isBlank()) ? "A tenant" : fullName.trim();
    }

    /**
     * Unit numbers are stored bare ("A1", "HWSW"), so they need the word in
     * front of them to read as a place rather than a code.
     */
    private static String unitLabel(String unitNumber) {
        return (unitNumber == null || unitNumber.isBlank())
                ? "your unit"
                : "Unit " + unitNumber.trim();
    }
}
