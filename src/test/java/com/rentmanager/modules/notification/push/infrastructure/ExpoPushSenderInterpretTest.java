package com.rentmanager.modules.notification.push.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.notification.push.application.PushSender;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExpoPushSenderInterpretTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void okTicketIsAcceptedWithItsId() throws Exception {
        PushSender.SendOutcome outcome = ExpoPushSender.interpret(
                mapper.readTree("{\"data\":[{\"status\":\"ok\",\"id\":\"abc\"}]}"));
        assertThat(outcome.result()).isEqualTo(PushSender.Result.ACCEPTED);
        assertThat(outcome.ticketId()).isEqualTo("abc");
    }

    @Test
    void deviceNotRegisteredIsGone() throws Exception {
        assertThat(ExpoPushSender.interpret(mapper.readTree(
                "{\"data\":[{\"status\":\"error\",\"message\":\"x\",\"details\":{\"error\":\"DeviceNotRegistered\"}}]}")).result())
                .isEqualTo(PushSender.Result.DEVICE_GONE);
    }

    @Test
    void otherErrorsAreTransient() throws Exception {
        assertThat(ExpoPushSender.interpret(mapper.readTree(
                "{\"data\":[{\"status\":\"error\",\"details\":{\"error\":\"MessageRateExceeded\"}}]}")).result())
                .isEqualTo(PushSender.Result.TRANSIENT_FAILURE);
        assertThat(ExpoPushSender.interpret(null).result()).isEqualTo(PushSender.Result.TRANSIENT_FAILURE);
    }

    @Test
    void receiptsAreClassified() throws Exception {
        Map<String, PushSender.ReceiptStatus> receipts = ExpoPushSender.interpretReceipts(mapper.readTree("""
                {"data":{
                  "t1":{"status":"ok"},
                  "t2":{"status":"error","details":{"error":"DeviceNotRegistered"}},
                  "t3":{"status":"error","details":{"error":"MessageTooBig"}}
                }}"""));
        assertThat(receipts).containsExactlyInAnyOrderEntriesOf(Map.of(
                "t1", PushSender.ReceiptStatus.DELIVERED,
                "t2", PushSender.ReceiptStatus.DEVICE_GONE,
                "t3", PushSender.ReceiptStatus.OTHER_ERROR));
    }
}
