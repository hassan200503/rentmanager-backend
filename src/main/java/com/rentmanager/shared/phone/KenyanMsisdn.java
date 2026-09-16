package com.rentmanager.shared.phone;

/**
 * Kenyan mobile numbers, in the forms people actually type.
 *
 * <p>Kenya has two mobile prefixes, not one: the original {@code 07XX} range
 * and the newer {@code 01XX} range (Safaricom issues {@code 0110}–{@code 0115},
 * all M-Pesa capable). Validation used to accept only {@code 07}, so a renter
 * on an {@code 011} line could not pay rent or reserve a unit at all — the
 * request was rejected before it reached Safaricom.
 *
 * <p>Whether a given number is actually registered for M-Pesa is Safaricom's
 * to decide; Daraja rejects one that is not. This class only decides whether
 * the input is a well-formed Kenyan mobile number.
 */
public final class KenyanMsisdn {

    /** Accepts 07XXXXXXXX, 01XXXXXXXX, 2547/2541…, and +2547/+2541… */
    public static final String PATTERN = "^(?:\\+254|254|0)(?:7\\d{8}|1[01]\\d{7})$";

    /** E.164 only — for fields that have always required the + form. */
    public static final String E164_PATTERN = "^\\+254(?:7\\d{8}|1[01]\\d{7})$";

    public static final String MESSAGE =
            "Enter a valid M-Pesa number: 0712345678, 0112345678, or +254712345678";

    private KenyanMsisdn() {
    }

    /** Normalises any accepted form to E.164 (+254…). Returns input stripped if unrecognised. */
    public static String toE164(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        if (value.startsWith("+")) {
            return value;
        }
        if (value.startsWith("254")) {
            return "+" + value;
        }
        if (value.startsWith("0") && value.length() == 10) {
            return "+254" + value.substring(1);
        }
        return value;
    }
}
