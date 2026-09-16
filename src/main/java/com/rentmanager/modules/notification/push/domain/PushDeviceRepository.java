package com.rentmanager.modules.notification.push.domain;

import java.util.List;
import java.util.Optional;

public interface PushDeviceRepository {

    PushDevice save(PushDevice device);

    Optional<PushDevice> findByToken(String pushToken);

    List<PushDevice> findActiveByClerkUserId(String clerkUserId);
}
