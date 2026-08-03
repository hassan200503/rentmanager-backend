package com.rentmanager.modules.tax.application.dto;

/**
 * Outcome of a KRA transmission attempt (eTIMS or eRITS).
 *
 * <p>ACCEPTED — KRA acknowledged the submission; ack details are carried
 * in {@code ackReference} / {@code receiptData}. REJECTED — KRA refused
 * (validation error on the payload); {@code error} carries the reason.
 * TRANSPORT_FAILED — the submission could not be delivered (network/KRA
 * downtime); retry is appropriate. NOT_AVAILABLE — the integration is not
 * configured (Phase 1 stub / manual filing); the caller parks the record
 * rather than failing it.
 */
public record TransmissionResult(
        TransmissionStatus status,
        String ackReference,
        String receiptData,
        String error
) {

    public enum TransmissionStatus {
        ACCEPTED,
        REJECTED,
        TRANSPORT_FAILED,
        NOT_AVAILABLE
    }

    public boolean accepted() {
        return status == TransmissionStatus.ACCEPTED;
    }

    public boolean retryable() {
        return status == TransmissionStatus.TRANSPORT_FAILED;
    }

    public static TransmissionResult accepted(String ackReference, String receiptData) {
        return new TransmissionResult(TransmissionStatus.ACCEPTED, ackReference, receiptData, null);
    }

    public static TransmissionResult rejected(String error) {
        return new TransmissionResult(TransmissionStatus.REJECTED, null, null, error);
    }

    public static TransmissionResult transportFailed(String error) {
        return new TransmissionResult(TransmissionStatus.TRANSPORT_FAILED, null, null, error);
    }

    public static TransmissionResult notAvailable(String message) {
        return new TransmissionResult(TransmissionStatus.NOT_AVAILABLE, null, null, message);
    }
}
