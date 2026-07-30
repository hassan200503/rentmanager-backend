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

    @Override
    public void sendSignInLink(String phone, String linkUrl) {
        log.warn("[SMS STUB] Would send sign-in link to {}: {} (SmsService not wired to a real provider)",
                PhoneMasker.mask(phone), linkUrl);
    }

    @Override
    public void sendRentPaymentReceivedConfirmation(String phone, String amount, String receiptNumber) {
        log.warn("[SMS STUB] Would send rent payment confirmation to {}: Ksh {}, receipt {} (SmsService not wired to a real provider)",
                PhoneMasker.mask(phone), amount, receiptNumber);
    }

    @Override
    public void sendRentPaymentNotificationToLandlord(String phone, String tenantName, String amount, String unitNumber) {
        log.warn("[SMS STUB] Would send rent payment notification to landlord {}: {} paid Ksh {} for unit {} (SmsService not wired to a real provider)",
                PhoneMasker.mask(phone), tenantName, amount, unitNumber);
    }

    @Override
    public void sendRentUpcomingPaymentReminder(String phone, String amount, String dueDate) {
        log.warn("[SMS STUB] Would send upcoming payment reminder to {}: Ksh {} due {} (SmsService not wired to a real provider)",
                PhoneMasker.mask(phone), amount, dueDate);
    }

    @Override
    public void sendRentOverdueReminder(String phone, String amount, String daysOverdue) {
        log.warn("[SMS STUB] Would send overdue reminder to {}: Ksh {} overdue by {} day(s) (SmsService not wired to a real provider)",
                PhoneMasker.mask(phone), amount, daysOverdue);
    }

    @Override
    public void sendRentOverdueNotificationToLandlord(String phone, String tenantName, String amount, String unitNumber, String daysOverdue) {
        log.warn("[SMS STUB] Would send overdue notification to landlord {}: {} Ksh {} for unit {} overdue by {} day(s) (SmsService not wired to a real provider)",
                PhoneMasker.mask(phone), tenantName, amount, unitNumber, daysOverdue);
    }

    @Override
    public void sendMaintenanceRequestConfirmation(String phone, String title, String requestId) {
        log.warn("[SMS STUB] Would send maintenance request confirmation to {}: \"{}\" ref {} (SmsService not wired to a real provider)",
                PhoneMasker.mask(phone), title, requestId);
    }

    @Override
    public void sendMaintenanceRequestNotificationToLandlord(String phone, String tenantName, String unitNumber, String title) {
        log.warn("[SMS STUB] Would send maintenance request notification to landlord {}: {} unit {} reported \"{}\" (SmsService not wired to a real provider)",
                PhoneMasker.mask(phone), tenantName, unitNumber, title);
    }

    @Override
    public void sendMaintenanceRequestStatusUpdate(String phone, String title, String status) {
        log.warn("[SMS STUB] Would send maintenance status update to {}: \"{}\" is now {} (SmsService not wired to a real provider)",
                PhoneMasker.mask(phone), title, status);
    }

    @Override
    public void sendAutoPayConfirmation(String phone, String amount, String mpesaPhone) {
        log.warn("[SMS STUB] Would send auto-pay confirmation to {}: Ksh {} charged to {} (SmsService not wired to a real provider)",
                PhoneMasker.mask(phone), amount, PhoneMasker.mask(mpesaPhone));
    }

    @Override
    public void sendAutoPayFailed(String phone, String reason) {
        log.warn("[SMS STUB] Would send auto-pay failure to {}: {} (SmsService not wired to a real provider)",
                PhoneMasker.mask(phone), reason);
    }
}