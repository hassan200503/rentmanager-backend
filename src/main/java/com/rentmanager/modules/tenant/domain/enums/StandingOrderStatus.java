package com.rentmanager.modules.tenant.domain.enums;

/**
 * Lifecycle of a merchant-initiated M-Pesa Ratiba standing order
 * (Safaricom's standing-order feature, Daraja product "Mpesa Ratiba").
 *
 * <ul>
 *   <li>PENDING_AUTHORIZATION - order submitted to Daraja; awaiting the
 *       landlord's NI-push PIN consent and the async creation callback.</li>
 *   <li>ACTIVE - the standing order is live on the customer's M-Pesa
 *       account; each monthly execution lands in our Paybill C2B queue.</li>
 *   <li>FAILED - Daraja rejected the creation (e.g. customer declined the
 *       PIN prompt, opt-in failed, or a processing error).</li>
 *   <li>CANCELLED - superseded by a newer order or manually voided.</li>
 * </ul>
 */
public enum StandingOrderStatus {
    PENDING_AUTHORIZATION,
    ACTIVE,
    FAILED,
    CANCELLED
}
