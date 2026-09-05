package com.rentmanager.modules.tenant.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Deliberately minimal — confirms configuration succeeded without echoing
 * back any credential material (not even the non-secret businessShortCode),
 * since this response could otherwise become an easy place for a future
 * change to accidentally leak sensitive fields back to the client.
 *
 * <p>{@code collectionMode} is the one addition, and it is not credential
 * material: it says whether this landlord's rent settles into their own
 * M-Pesa ({@code DIRECT}) or passes through the platform
 * ({@code PLATFORM_CUSTODY}). The Payment settings page needs it to state
 * what is actually true for the landlord reading it rather than describing
 * the default and hoping they are on it — which is the difference between an
 * accurate page and one that happens to be right.
 */
@Getter
@AllArgsConstructor
public class DarajaCredentialsStatusResponse {
    private boolean configured;
    private String collectionMode;
}
