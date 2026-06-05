package com.rentmanager.shared.error.persistence;

import com.rentmanager.shared.error.ErrorEvent;
import com.rentmanager.shared.util.JsonUtil;

public class ErrorEventMapper {

    public static ErrorEventEntity toEntity(ErrorEvent event) {
        ErrorEventEntity entity = new ErrorEventEntity();

        entity.setId(event.getId());
        entity.setTraceId(event.getTraceId());
        entity.setTenantId(event.getTenantId());
        entity.setUserId(event.getUserId());
        entity.setModule(event.getModule());
        entity.setErrorType(event.getErrorType());
        entity.setErrorCode(event.getErrorCode());
        entity.setMessage(event.getMessage());
        entity.setPath(event.getPath());
        entity.setHttpMethod(event.getHttpMethod());

        // IMPORTANT: missing fields (previous bug source)
        entity.setMetadataJson(JsonUtil.toJson(event.getMetadata()));
        entity.setTimestamp(event.getTimestamp());

        return entity;
    }
}