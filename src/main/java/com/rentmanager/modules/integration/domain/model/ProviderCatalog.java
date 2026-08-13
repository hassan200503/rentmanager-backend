package com.rentmanager.modules.integration.domain.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The seeded provider catalog: every provider the platform depends on,
 * its display metadata and the per-environment credential fields the Owner
 * configures from the console. Static by design - adding a provider is a
 * code change (plus its adapter wiring), not a data change.
 */
public final class ProviderCatalog {

    public static final String DARAJA = "daraja";
    public static final String AFRICASTALKING = "africastalking";
    public static final String WHATSAPP = "whatsapp";
    public static final String EMAIL = "email";
    public static final String MEDIA_STORAGE = "media_storage";
    public static final String CLERK = "clerk";

    private static final List<ProviderDefinition> DEFINITIONS = List.of(
            new ProviderDefinition(
                    DARAJA,
                    "M-Pesa (Daraja)",
                    "payments",
                    "https://developer.safaricom.co.ke",
                    true,
                    List.of(
                            ProviderField.plain("consumer_key", "Consumer Key", "Daraja portal → My Apps → app credentials"),
                            ProviderField.secret("consumer_secret", "Consumer Secret", "Daraja portal → My Apps → app credentials"),
                            ProviderField.plain("business_shortcode", "Business Shortcode", "Paybill / Till number (sandbox default 174379)"),
                            ProviderField.secret("passkey", "STK Passkey", "Issued with the shortcode after Go-Live"),
                            ProviderField.plain("initiator_name", "B2C Initiator Name", "Safaricom web operator username for disbursements"),
                            ProviderField.secret("initiator_password", "B2C Initiator Password", "Raw operator password (expires ~3 months) OR precomputed SecurityCredential"),
                            ProviderField.plain("security_certificate", "Safaricom Security Certificate", "base64 X.509 public cert — derives the B2C SecurityCredential from the initiator password"),
                            ProviderField.plain("base_url", "Base URL", "Sandbox: https://sandbox.safaricom.co.ke"),
                            ProviderField.plain("result_url", "B2C Result URL", "Public HTTPS endpoint for B2C results"),
                            ProviderField.plain("queue_timeout_url", "B2C Timeout URL", "Public HTTPS endpoint for B2C timeouts")
                    )),
            new ProviderDefinition(
                    AFRICASTALKING,
                    "SMS (Africa's Talking)",
                    "sms",
                    "https://africastalking.com",
                    true,
                    List.of(
                            ProviderField.plain("username", "Username", "Sandbox apps use the literal username sandbox"),
                            ProviderField.secret("api_key", "API Key", "AT Dashboard → Settings → API Key"),
                            ProviderField.plain("sender_id", "Sender ID", "AT-approved sender ID (production)"),
                            ProviderField.plain("base_url", "Base URL", "Sandbox: https://api.sandbox.africastalking.com/version1/messaging")
                    )),
            new ProviderDefinition(
                    WHATSAPP,
                    "WhatsApp (Meta Cloud API)",
                    "whatsapp",
                    "https://developers.facebook.com/docs/whatsapp/cloud-api",
                    true,
                    List.of(
                            ProviderField.plain("app_id", "Meta App ID", "Meta for Developers → your app → Settings → Basic"),
                            ProviderField.secret("app_secret", "App Secret", "Meta for Developers → your app → Settings → Basic"),
                            ProviderField.plain("waba_id", "WABA ID", "Meta Business Suite → WhatsApp Manager"),
                            ProviderField.plain("phone_number_id", "Phone Number ID", "Meta Business Suite → WhatsApp Manager"),
                            ProviderField.secret("access_token", "Permanent Access Token", "Business Settings → System Users → generate token (whatsapp_business_messaging)"),
                            ProviderField.secret("verify_token", "Webhook Verify Token", "Chosen by you; must match Meta's console exactly"),
                            ProviderField.plain("template_name", "Utility Template Name", "Pre-approved template for broadcasts (e.g. announcement_utility_v1)")
                    )),
            new ProviderDefinition(
                    EMAIL,
                    "Email (SMTP)",
                    "email",
                    null,
                    true,
                    List.of(
                            ProviderField.plain("host", "SMTP Host", "e.g. smtp.resend.com, smtp.sendgrid.net"),
                            ProviderField.plain("port", "SMTP Port", "Usually 587 (STARTTLS) or 465 (SSL)"),
                            ProviderField.plain("username", "Username", "SMTP login"),
                            ProviderField.secret("password", "Password / API Key", "SMTP login or provider API key"),
                            ProviderField.plain("from_address", "From Address", "Must be verified for the sending domain"),
                            ProviderField.plain("from_name", "From Name", "Display name shown to recipients"),
                            ProviderField.plain("tls", "TLS (true/false)", "Secure transport on the configured port")
                    )),
            new ProviderDefinition(
                    MEDIA_STORAGE,
                    "Media Storage (Cloudinary)",
                    "storage",
                    "https://cloudinary.com/documentation",
                    true,
                    List.of(
                            ProviderField.plain("cloud_name", "Cloud Name", "Cloudinary dashboard → Account Details"),
                            ProviderField.plain("api_key", "API Key", "Cloudinary dashboard → Account Details"),
                            ProviderField.secret("api_secret", "API Secret", "Cloudinary dashboard → Account Details")
                    )),
            new ProviderDefinition(
                    CLERK,
                    "Authentication (Clerk)",
                    "auth",
                    "https://clerk.com/docs",
                    true,
                    List.of(
                            ProviderField.plain("publishable_key", "Publishable Key", "Clerk Dashboard → API Keys"),
                            ProviderField.secret("secret_key", "Secret Key", "Clerk Dashboard → API Keys"),
                            ProviderField.secret("webhook_signing_secret", "Webhook Signing Secret", "Clerk Dashboard → Webhooks → endpoint"),
                            ProviderField.plain("base_url", "API Base URL", "Default https://api.clerk.com/v1")
                    ))
    );

    private static final Map<String, ProviderDefinition> BY_KEY =
            DEFINITIONS.stream().collect(Collectors.toUnmodifiableMap(ProviderDefinition::key, d -> d));

    private ProviderCatalog() {
    }

    public static List<ProviderDefinition> all() {
        return DEFINITIONS;
    }

    public static ProviderDefinition get(String providerKey) {
        ProviderDefinition definition = BY_KEY.get(providerKey);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown integration provider: " + providerKey);
        }
        return definition;
    }

    public static boolean exists(String providerKey) {
        return BY_KEY.containsKey(providerKey);
    }
}