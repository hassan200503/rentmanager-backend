package com.rentmanager.modules.notification.sms;

public interface SmsService {

    void sendCredentials(String phone, String password);

    void sendReservationConfirmed(String phone);

    void sendSignInLink(String phone, String linkUrl);

    void sendRentPaymentReceivedConfirmation(String phone, String amount, String receiptNumber);

    void sendRentPaymentNotificationToLandlord(String phone, String tenantName, String amount, String unitNumber);

    void sendRentUpcomingPaymentReminder(String phone, String amount, String dueDate);

    void sendRentOverdueReminder(String phone, String amount, String daysOverdue);

    void sendRentOverdueNotificationToLandlord(String phone, String tenantName, String amount, String unitNumber, String daysOverdue);

    void sendMaintenanceRequestConfirmation(String phone, String title, String requestId);

    void sendMaintenanceRequestNotificationToLandlord(String phone, String tenantName, String unitNumber, String title);

    void sendMaintenanceRequestStatusUpdate(String phone, String title, String status);

    void sendAutoPayConfirmation(String phone, String amount, String mpesaPhone);

    void sendAutoPayFailed(String phone, String reason);

}