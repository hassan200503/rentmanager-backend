package com.rentmanager.modules.lease.application.port.out;

import java.util.UUID;

public interface PropertyQueryPort {
    boolean existsById(UUID propertyId);
}