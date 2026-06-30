package com.rentmanager.modules.lease.application.command.service;

import java.util.UUID;

public interface LeaseNumberGenerator {
    String generate(UUID propertyId, UUID unitId);
}