package com.rentmanager.modules.unit.application.dto.request;

import java.util.List;
import java.util.UUID;

public record ReorderUnitMediaRequest(
        List<UUID> mediaIds
) {}