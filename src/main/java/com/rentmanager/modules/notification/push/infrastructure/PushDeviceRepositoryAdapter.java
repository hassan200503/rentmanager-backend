package com.rentmanager.modules.notification.push.infrastructure;

import com.rentmanager.modules.notification.push.domain.PushDevice;
import com.rentmanager.modules.notification.push.domain.PushDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PushDeviceRepositoryAdapter implements PushDeviceRepository {

    private final PushDeviceJpaRepository jpaRepository;

    @Override
    public PushDevice save(PushDevice device) {
        PushDeviceJpaEntity jpa = new PushDeviceJpaEntity();
        jpa.restoreId(device.getId());
        jpa.setVersion(device.getVersion());
        jpa.restoreCreatedAt(device.getCreatedAt());
        jpa.setClerkUserId(device.getClerkUserId());
        jpa.setPushToken(device.getPushToken());
        jpa.setPlatform(device.getPlatform());
        jpa.setAppVersion(device.getAppVersion());
        jpa.setRevokedAt(device.getRevokedAt());
        jpa.setLastSeenAt(device.getLastSeenAt());
        return toDomain(jpaRepository.save(jpa));
    }

    @Override
    public Optional<PushDevice> findByToken(String pushToken) {
        return jpaRepository.findByPushToken(pushToken).map(this::toDomain);
    }

    @Override
    public List<PushDevice> findActiveByClerkUserId(String clerkUserId) {
        // Bounded: one person with more than 20 live installations is not a
        // real case, and an unbounded fan-out per event is not acceptable.
        return jpaRepository.findTop20ByClerkUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(clerkUserId)
                .stream().map(this::toDomain).toList();
    }

    private PushDevice toDomain(PushDeviceJpaEntity jpa) {
        return PushDevice.rehydrate(jpa.getId(), jpa.getClerkUserId(), jpa.getPushToken(), jpa.getPlatform(),
                jpa.getAppVersion(), jpa.getRevokedAt(), jpa.getLastSeenAt(), jpa.getCreatedAt(), jpa.getVersion());
    }
}
