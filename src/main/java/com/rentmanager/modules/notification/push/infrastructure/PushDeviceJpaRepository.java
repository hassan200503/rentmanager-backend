package com.rentmanager.modules.notification.push.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceJpaRepository extends JpaRepository<PushDeviceJpaEntity, UUID> {

    Optional<PushDeviceJpaEntity> findByPushToken(String pushToken);

    List<PushDeviceJpaEntity> findTop20ByClerkUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(String clerkUserId);
}
