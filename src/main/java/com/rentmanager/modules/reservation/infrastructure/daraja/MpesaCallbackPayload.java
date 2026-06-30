package com.rentmanager.modules.reservation.infrastructure.daraja;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Maps the Safaricom STK Push callback payload.
 *
 * Example payload:
 * {
 *   "Body": {
 *     "stkCallback": {
 *       "MerchantRequestID": "...",
 *       "CheckoutRequestID": "ws_CO_...",
 *       "ResultCode": 0,
 *       "ResultDesc": "The service request is processed successfully.",
 *       "CallbackMetadata": {
 *         "Item": [
 *           { "Name": "Amount", "Value": 10000 },
 *           { "Name": "MpesaReceiptNumber", "Value": "NLJ7RT61SV" },
 *           { "Name": "TransactionDate", "Value": 20191219102115 },
 *           { "Name": "PhoneNumber", "Value": 254708374149 }
 *         ]
 *       }
 *     }
 *   }
 * }
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MpesaCallbackPayload {

    @JsonProperty("Body")
    private Body body;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {

        @JsonProperty("stkCallback")
        private StkCallback stkCallback;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StkCallback {

        @JsonProperty("CheckoutRequestID")
        private String checkoutRequestId;

        @JsonProperty("ResultCode")
        private int resultCode;

        @JsonProperty("ResultDesc")
        private String resultDesc;

        @JsonProperty("CallbackMetadata")
        private CallbackMetadata callbackMetadata;

        public boolean isSuccessful() {
            return resultCode == 0;
        }
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CallbackMetadata {

        @JsonProperty("Item")
        private List<MetadataItem> items;

        public String getMpesaReceiptNumber() {
            return items == null ? null : items.stream()
                    .filter(i -> "MpesaReceiptNumber".equals(i.getName()))
                    .map(MetadataItem::getStringValue)
                    .findFirst()
                    .orElse(null);
        }
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetadataItem {

        @JsonProperty("Name")
        private String name;

        @JsonProperty("Value")
        private Object value;

        public String getStringValue() {
            return value != null ? value.toString() : null;
        }
    }
}