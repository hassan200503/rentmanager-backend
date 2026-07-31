package com.rentmanager.modules.tenant.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Subscription billing policy knobs (Phase 1 dual revenue model).
 *
 * <ul>
 *   <li>{@code graceDays} - how long a premium landlord keeps premium
 *       benefits after a failed/timed-out renewal payment before the
 *       scheduler auto-reverts them to COMMISSION billing. Default 7.</li>
 *   <li>{@code paymentRequestExpiryMinutes} - how long a PENDING
 *       subscription STK push may go without a Daraja callback before the
 *       stale-request sweep marks it EXPIRED (mirrors
 *       {@code RentPaymentRequestExpiryScheduler}'s 30-minute window).
 *       Default 30.</li>
 * </ul>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "subscription-billing")
public class SubscriptionBillingProperties {

    private int graceDays = 7;

    private int paymentRequestExpiryMinutes = 30;
}
