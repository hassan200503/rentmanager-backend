package com.rentmanager.modules.notification.push.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.rentmanager.modules.notification.push.application.PushSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends through the Expo Push Service, which fronts APNs and FCM so the
 * backend holds no Apple or Google credentials itself — those live in the
 * Expo project (EAS credentials), configured per environment at release.
 *
 * {@code EXPO_ACCESS_TOKEN} ({@code push.expo.access-token}) is optional and
 * only required once "enhanced push security" is enabled on the Expo project,
 * which production should do.
 */
@Slf4j
@Component
public class ExpoPushSender implements PushSender {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient client;

    public ExpoPushSender(
            @Value("${push.expo.base-url:https://exp.host}") String baseUrl,
            @Value("${push.expo.access-token:}") String accessToken
    ) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (accessToken != null && !accessToken.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        this.client = builder.build();
    }

    @Override
    public SendOutcome send(String pushToken, String title, String body, Map<String, String> data) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("to", pushToken);
        message.put("title", title);
        message.put("body", body);
        message.put("data", data == null ? Map.of() : data);
        message.put("sound", "default");
        message.put("priority", "high");
        message.put("channelId", "default");

        try {
            JsonNode response = client.post()
                    .uri("/--/api/v2/push/send")
                    .bodyValue(List.of(message))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(TIMEOUT);
            return interpret(response);
        } catch (WebClientResponseException ex) {
            // Includes 401 (access token missing or wrong): the outbox retries
            // with backoff and gives up after MAX_ATTEMPTS, and the status is
            // logged so the misconfiguration is visible.
            log.warn("Expo push rejected: status={}", ex.getStatusCode().value());
            return SendOutcome.of(Result.TRANSIENT_FAILURE);
        } catch (RuntimeException ex) {
            log.warn("Expo push send failed: {}", ex.getClass().getSimpleName());
            return SendOutcome.of(Result.TRANSIENT_FAILURE);
        }
    }

    @Override
    public Map<String, ReceiptStatus> receipts(Collection<String> ticketIds) {
        if (ticketIds.isEmpty()) {
            return Map.of();
        }
        JsonNode response = client.post()
                .uri("/--/api/v2/push/getReceipts")
                .bodyValue(Map.of("ids", ticketIds))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(TIMEOUT);
        return interpretReceipts(response);
    }

    static SendOutcome interpret(JsonNode response) {
        if (response == null) {
            return SendOutcome.of(Result.TRANSIENT_FAILURE);
        }
        JsonNode ticket = response.path("data").isArray()
                ? response.path("data").path(0)
                : response.path("data");
        String status = ticket.path("status").asText("");
        if ("ok".equals(status)) {
            String id = ticket.path("id").asText("");
            return new SendOutcome(Result.ACCEPTED, id.isEmpty() ? null : id);
        }
        String error = ticket.path("details").path("error").asText("");
        if ("DeviceNotRegistered".equals(error)) {
            return SendOutcome.of(Result.DEVICE_GONE);
        }
        log.warn("Expo push ticket error: {}", error.isEmpty() ? "unknown" : error);
        return SendOutcome.of(Result.TRANSIENT_FAILURE);
    }

    static Map<String, ReceiptStatus> interpretReceipts(JsonNode response) {
        Map<String, ReceiptStatus> out = new HashMap<>();
        if (response == null) {
            return out;
        }
        response.path("data").fields().forEachRemaining(entry -> {
            JsonNode receipt = entry.getValue();
            String status = receipt.path("status").asText("");
            if ("ok".equals(status)) {
                out.put(entry.getKey(), ReceiptStatus.DELIVERED);
            } else if ("DeviceNotRegistered".equals(receipt.path("details").path("error").asText(""))) {
                out.put(entry.getKey(), ReceiptStatus.DEVICE_GONE);
            } else {
                out.put(entry.getKey(), ReceiptStatus.OTHER_ERROR);
            }
        });
        return out;
    }
}
