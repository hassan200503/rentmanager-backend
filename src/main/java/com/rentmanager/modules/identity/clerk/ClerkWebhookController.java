package com.rentmanager.modules.identity.clerk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Receives Clerk webhook events and keeps local user records in sync.
 *
 * <h2>Why this exists</h2>
 * Users provisioned via JIT (ClerkJwtAuthenticationConverter) when the JWT
 * carries no {@code email} claim are stored with an {@code unknown@clerk.user}
 * placeholder. Clerk later delivers {@code user.updated} (or the initial
 * {@code user.created} if caught) once the user confirms their address.
 * Without this handler the placeholder is permanent and notification code
 * posts to a mailbox that does not exist. See TD-118.
 *
 * <h2>Signature verification</h2>
 * Clerk uses Svix. The signed payload is
 * {@code svix-id + "." + svix-timestamp + "." + raw-body}. The signing key
 * is the webhook secret with the literal prefix {@code whsec_} stripped and
 * the remainder base64-decoded. The {@code svix-signature} header contains
 * one or more {@code v1,<base64-mac>} entries separated by spaces; any one
 * of them matching is sufficient.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/clerk")
@RequiredArgsConstructor
public class ClerkWebhookController {

    private final ClerkProperties clerkProperties;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final com.rentmanager.modules.identity.account.AccountDeletionService accountDeletionService;

    @PostMapping
    @Transactional
    public ResponseEntity<Void> handle(
            @RequestHeader("svix-id") String svixId,
            @RequestHeader("svix-timestamp") String svixTimestamp,
            @RequestHeader("svix-signature") String svixSignature,
            @RequestBody String rawBody
    ) {
        try {
            verifySignature(svixId, svixTimestamp, svixSignature, rawBody);
        } catch (SecurityException e) {
            log.warn("Clerk webhook: rejected — signature invalid");
            return ResponseEntity.status(401).build();
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String type = root.path("type").asText();

            if ("user.updated".equals(type) || "user.created".equals(type)) {
                handleUserUpsert(root.path("data"));
            }
            if ("user.deleted".equals(type)) {
                // A person deleted their login outside the app (Clerk Account
                // Portal or dashboard). Erase the local identity the same way
                // in-app deletion does, so no orphaned email or device remains.
                String clerkUserId = root.path("data").path("id").asText(null);
                if (clerkUserId != null && !clerkUserId.isBlank()) {
                    accountDeletionService.eraseLocalIdentity(clerkUserId, "CLERK_WEBHOOK");
                }
            }
        } catch (Exception e) {
            log.error("Failed to process Clerk webhook payload", e);
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok().build();
    }

    private void handleUserUpsert(JsonNode data) {
        String clerkUserId = data.path("id").asText(null);
        if (clerkUserId == null || clerkUserId.isBlank()) {
            log.warn("Clerk webhook: missing user id in data");
            return;
        }

        Optional<User> maybeUser = userRepository.findByClerkUserId(clerkUserId);
        if (maybeUser.isEmpty()) {
            log.debug("Clerk webhook: no local user for clerkUserId={} — skipping", clerkUserId);
            return;
        }

        User user = maybeUser.get();
        boolean changed = false;

        String email = primaryEmail(data);
        if (email != null) {
            changed |= user.updateEmailIfChanged(email);
        }

        String firstName = data.path("first_name").asText(null);
        String lastName  = data.path("last_name").asText(null);
        if (firstName != null || lastName != null) {
            changed |= user.updateNameIfChanged(firstName, lastName);
        }

        if (changed) {
            userRepository.save(user);
            log.info("Clerk webhook: profile synced for clerkUserId={}", clerkUserId);
        }
    }

    private String primaryEmail(JsonNode data) {
        JsonNode emailAddresses = data.path("email_addresses");
        if (!emailAddresses.isArray() || emailAddresses.isEmpty()) {
            return null;
        }

        // Prefer the address whose id matches primary_email_address_id.
        String primaryId = data.path("primary_email_address_id").asText(null);
        if (primaryId != null) {
            for (JsonNode entry : emailAddresses) {
                if (primaryId.equals(entry.path("id").asText(null))) {
                    String addr = entry.path("email_address").asText(null);
                    if (addr != null && !addr.isBlank()) {
                        return addr;
                    }
                }
            }
        }

        // Fall back to the first entry.
        String addr = emailAddresses.get(0).path("email_address").asText(null);
        return (addr != null && !addr.isBlank()) ? addr : null;
    }

    /**
     * Verifies the Svix HMAC-SHA256 signature.
     *
     * <p>Any one valid signature in the {@code svix-signature} header
     * (space-separated {@code v1,<base64>} tokens) is sufficient.
     */
    private void verifySignature(
            String svixId,
            String svixTimestamp,
            String svixSignature,
            String rawBody
    ) {
        String secret = clerkProperties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            // Webhook secret not configured — skip verification. This is
            // intentional for local dev where the env var is unset; in
            // production the secret must be present.
            log.warn("Clerk webhook secret not configured; skipping signature verification");
            return;
        }

        try {
            // Strip the 'whsec_' prefix and base64-decode.
            String stripped = secret.startsWith("whsec_") ? secret.substring(6) : secret;
            byte[] keyBytes = Base64.getDecoder().decode(stripped);

            // Signed message: svix-id + "." + svix-timestamp + "." + rawBody
            String toSign = svixId + "." + svixTimestamp + "." + rawBody;
            byte[] messageBytes = toSign.getBytes(StandardCharsets.UTF_8);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] expectedBytes = mac.doFinal(messageBytes);
            String expected = Base64.getEncoder().encodeToString(expectedBytes);

            // svix-signature contains one or more "v1,<base64>" tokens
            // separated by spaces.
            for (String token : svixSignature.split(" ")) {
                if (token.startsWith("v1,")) {
                    String candidate = token.substring(3);
                    if (expected.equals(candidate)) {
                        return; // valid
                    }
                }
            }

            throw new SecurityException("Clerk webhook signature verification failed");

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Clerk webhook signature verification error", e);
        }
    }
}
