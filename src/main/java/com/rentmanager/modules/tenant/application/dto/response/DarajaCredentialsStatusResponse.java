package com.rentmanager.modules.tenant.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Deliberately minimal — confirms configuration succeeded without echoing
 * back any credential material (not even the non-secret businessShortCode),
 * since this response could otherwise become an easy place for a future
 * change to accidentally leak sensitive fields back to the client.
 */
@Getter
@AllArgsConstructor
public class DarajaCredentialsStatusResponse {
    private boolean configured;
}