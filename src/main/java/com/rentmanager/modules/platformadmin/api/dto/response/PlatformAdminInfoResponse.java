package com.rentmanager.modules.platformadmin.api.dto.response;

/**
 * Bootstrap response for the Super Admin shell: tells the caller which
 * platform sub-role their token carries (OWNER is a superset of ADMIN).
 * Contains no secrets and no landlord-scoped data.
 */
public record PlatformAdminInfoResponse(String platformRole) {
}