package com.rentmanager.modules.lease.application.port.out;

import java.util.UUID;

public interface TenantProfileQueryPort {
    boolean existsById(UUID tenantProfileId);
}