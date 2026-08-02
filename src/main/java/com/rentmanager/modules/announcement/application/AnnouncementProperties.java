package com.rentmanager.modules.announcement.application;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Component
@ConfigurationProperties(prefix = "app.announcement")
public class AnnouncementProperties {

    /** Safety-net sweep cadence for pending announcement deliveries. */
    private long dispatchFixedRateMs = 10_000;

    /** Max rows dispatched per sweep pass - the provider rate-limit throttle. */
    private int batchSize = 10;

    /**
     * Pacing between consecutive sweep passes of the immediate drain after
     * a send: batch-size messages every drain-delay-ms caps sustained
     * fan-out rate without blocking the HTTP request.
     */
    private long drainDelayMs = 2_000;

    /**
     * The registered Meta WhatsApp Business template for announcements.
     * Utility category, one variable slot: the landlord's message is
     * injected into {{1}} and truncated to whatsapp-max-chars. This is
     * the template name approved in Meta Business Manager - until
     * Business Verification + template approval exist, the stub provider
     * logs the templated message and records the delivery.
     */
    private String whatsappTemplateName = "announcement_utility_v1";

    /**
     * Character limit the approved template allows for the {{1}} slot.
     * The landlord's message is truncated to this before injection.
     */
    private int whatsappMaxChars = 500;

    public void setDispatchFixedRateMs(long dispatchFixedRateMs) {
        this.dispatchFixedRateMs = dispatchFixedRateMs;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setDrainDelayMs(long drainDelayMs) {
        this.drainDelayMs = drainDelayMs;
    }

    public void setWhatsappTemplateName(String whatsappTemplateName) {
        this.whatsappTemplateName = whatsappTemplateName;
    }

    public void setWhatsappMaxChars(int whatsappMaxChars) {
        this.whatsappMaxChars = whatsappMaxChars;
    }
}
