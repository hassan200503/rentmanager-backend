package com.rentmanager.modules.tenant.domain.enums;

/**
 * Whether rent for this landlord passes through the platform's M-Pesa
 * account, or goes straight into the landlord's own.
 *
 * <h2>Why this is separate from {@link BillingMode}</h2>
 * {@code BillingMode} answers "how does the platform earn" (commission vs
 * subscription). This answers "does the platform ever hold the money". They
 * were previously conflated, and the conflation hid the fact that the
 * platform was taking custody of rent by default. A landlord can be billed
 * a percentage fee without the platform ever touching their rent — the fee
 * is invoiced to them, not deducted from a renter's payment.
 *
 * <h2>Why the distinction matters more than a config flag usually does</h2>
 * Custody is what triggers regulation. Collecting rent into a platform
 * account, deducting a cut and disbursing the rest is payment aggregation:
 * Safaricom's M-PESA terms cl. 15.2(l) prohibit it without written consent,
 * and it requires CBK authorisation under the NPS Act 2011 (Electronic
 * Retail PSP: KSh 5m core capital), plus a pre-funded trust account for B2C
 * (cl. 6.1(a)). {@code DIRECT} does not merely reduce that exposure — it
 * removes the facts that create it.
 */
public enum CollectionMode {

    /**
     * The default, and the only mode that needs no licence.
     *
     * <p>The STK push is signed with the landlord's own Daraja credentials
     * and their own shortcode, so the money settles into their paybill or
     * till at the moment the renter pays. RentManager records the payment
     * and never receives it. No commission is deducted (there is nothing to
     * deduct from) and no B2C disbursement happens (there is nothing to
     * disburse).
     *
     * <p>Requires the landlord to have configured their own credentials.
     * Rent initiation refuses when they have not, rather than silently
     * falling back to the platform's — a refusal is a support ticket, a
     * silent fallback is unlicensed aggregation.
     */
    DIRECT,

    /**
     * Legacy. Rent lands in the platform's paybill, commission is deducted,
     * and the net is disbursed to the landlord by B2C.
     *
     * <p><strong>Requires CBK authorisation and Safaricom's written consent
     * before it is used with real money.</strong> It is retained because the
     * code exists, is tested, and would be the right model once licensed —
     * not because it is safe to enable. It is never the default, and it must
     * be set deliberately per landlord.
     */
    PLATFORM_CUSTODY
}
