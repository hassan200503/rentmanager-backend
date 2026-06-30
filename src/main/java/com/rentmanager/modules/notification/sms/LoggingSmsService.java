package com.rentmanager.modules.notification.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Temporary no-op SMS implementation. Logs the message instead of sending it.
 *
 * TODO: replace with a real Africa's Talking implementation once API
 * credentials are available. See AfricasTalkingProperties (not yet created)
 * for the config shape this should follow — consumerKey-style pattern
 * matching DarajaProperties.
 */
@Slf4j
@Service
public class LoggingSmsService implements SmsService {

    @Override
    public void sendCredentials(String phone, String password) {
        log.warn("[SMS STUB] Would send credentials to {} — password={} " +
                        "(SmsService not yet wired to a real provider)",
                phone, password);
    }
}