package com.rentmanager.modules.notification.sms;

/**
 * Single source of truth for masking phone numbers before they reach any
 * log line. Both SmsService implementations (AfricasTalkingSmsService and
 * LoggingSmsService) must mask identically — this used to live only in
 * AfricasTalkingSmsService, so LoggingSmsService (the dev/local fallback,
 * active whenever africastalking.enabled is false or unset — see its own
 * @ConditionalOnProperty) was logging raw phone numbers at WARN level.
 * Extracted here so there is exactly one masking implementation for both
 * providers to share, rather than two copies that can silently drift.
 */
public final class PhoneMasker {

    private PhoneMasker() {
        // utility class — not instantiable
    }

    /**
     * Masks all but the last 4 characters of a phone number for safe
     * logging. Returns "****" for null or too-short input rather than
     * throwing, since this is a logging helper and must never be the
     * reason an SMS send path fails.
     */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return phone.substring(0, phone.length() - 4).replaceAll(".", "*")
                + phone.substring(phone.length() - 4);
    }
}