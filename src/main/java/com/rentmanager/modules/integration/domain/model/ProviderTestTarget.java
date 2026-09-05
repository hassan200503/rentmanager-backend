package com.rentmanager.modules.integration.domain.model;

/**
 * Declares the destination a provider's real Test Connection expects when it
 * sends live traffic. Present on a {@link ProviderDefinition} when a Test sends
 * a real message (SMS / email / WhatsApp); null for providers whose Test is a
 * credential-only check (Daraja, Media Storage, Clerk).
 *
 * <p>This is the single authority the control plane consults: the backend
 * enforces {@code required} server-side before ever invoking a provider, and
 * the console renders the destination field entirely from this metadata — so
 * the client never hard-codes which provider needs what.
 */
public record ProviderTestTarget(
        TestTargetKind kind,
        boolean required,
        String label,
        String message
) {
    /** The input shape the console should render for the destination. */
    public enum TestTargetKind {
        TEXT, EMAIL, PHONE
    }

    public static ProviderTestTarget email(boolean required, String label, String message) {
        return new ProviderTestTarget(TestTargetKind.EMAIL, required, label, message);
    }

    public static ProviderTestTarget phone(boolean required, String label, String message) {
        return new ProviderTestTarget(TestTargetKind.PHONE, required, label, message);
    }
}