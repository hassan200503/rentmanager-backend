package com.rentmanager.modules.notification.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Fallback SMS implementation for local/dev environments. Logs instead of sending.
 * Active whenever africastalking.enabled is false or unset.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "africastalking", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingSmsService implements SmsService {

    @Override
    public void sendCredentials(String phone, String password) {
        log.warn("[SMS STUB] Would send credentials to {} (SmsService not wired to a real provider)",
                PhoneMasker.mask(phone));
    }

    @Override
    public void sendReservationConfirmed(String phone) {
        log.warn("[SMS STUB] Would send reservation-confirmed (existing account) to {} " +
                        "(SmsService not wired to a real provider)",
                PhoneMasker.mask(phone));
    }
}