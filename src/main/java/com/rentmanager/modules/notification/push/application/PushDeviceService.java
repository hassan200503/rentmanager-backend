package com.rentmanager.modules.notification.push.application;

import com.rentmanager.modules.notification.push.domain.PushDevice;
import com.rentmanager.modules.notification.push.domain.PushDeviceRepository;
import com.rentmanager.modules.notification.push.domain.PushPlatform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Owns the device registry. The caller's identity always comes from the
 * verified JWT (passed in by the controller) — never from the request body.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushDeviceService {

    private final PushDeviceRepository repository;

    @Transactional
    public PushDevice register(String clerkUserId, String pushToken, PushPlatform platform, String appVersion) {
        if (!PushDevice.isValidToken(pushToken)) {
            throw new IllegalArgumentException("Invalid push token");
        }
        return repository.findByToken(pushToken)
                .map(existing -> {
                    if (!existing.isOwnedBy(clerkUserId)) {
                        // A different person signed in on this device. Move it
                        // so the previous owner's notifications stop landing
                        // on a phone someone else is now holding.
                        log.info("Push device {} changed owner", existing.getId());
                    }
                    existing.claimFor(clerkUserId, platform, appVersion);
                    return repository.save(existing);
                })
                .orElseGet(() -> repository.save(PushDevice.register(clerkUserId, pushToken, platform, appVersion)));
    }

    /**
     * Revokes the token only if the caller owns it. Unknown or foreign tokens
     * are a silent no-op so the endpoint cannot be used to probe or revoke
     * someone else's device.
     */
    @Transactional
    public void unregister(String clerkUserId, String pushToken) {
        repository.findByToken(pushToken)
                .filter(device -> device.isOwnedBy(clerkUserId))
                .ifPresent(device -> {
                    device.revoke();
                    repository.save(device);
                });
    }

    @Transactional(readOnly = true)
    public List<PushDevice> activeDevices(String clerkUserId) {
        if (clerkUserId == null || clerkUserId.isBlank()) {
            return List.of();
        }
        return repository.findActiveByClerkUserId(clerkUserId);
    }

    /** Revokes every live device of a person (account deletion). */
    @Transactional
    public int revokeAllFor(String clerkUserId) {
        int revoked = 0;
        List<PushDevice> batch;
        // Bounded: each pass revokes up to 20; 50 passes is far beyond any real person.
        for (int pass = 0; pass < 50 && !(batch = repository.findActiveByClerkUserId(clerkUserId)).isEmpty(); pass++) {
            for (PushDevice device : batch) {
                device.revoke();
                repository.save(device);
                revoked++;
            }
        }
        return revoked;
    }

    /** True when the token is still live and still belongs to the intended person. */
    @Transactional(readOnly = true)
    public boolean isDeliverable(String pushToken, String intendedClerkUserId) {
        return repository.findByToken(pushToken)
                .map(device -> device.isActive() && device.isOwnedBy(intendedClerkUserId))
                .orElse(false);
    }

    /** Provider reported the token will never work again. */
    @Transactional
    public void revokeDead(String pushToken) {
        repository.findByToken(pushToken).ifPresent(device -> {
            device.revoke();
            repository.save(device);
        });
    }
}
