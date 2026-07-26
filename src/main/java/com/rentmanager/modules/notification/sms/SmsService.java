package com.rentmanager.modules.notification.sms;

public interface SmsService {

    void sendCredentials(String phone, String password);

    void sendReservationConfirmed(String phone);

    void sendSignInLink(String phone, String linkUrl);

}