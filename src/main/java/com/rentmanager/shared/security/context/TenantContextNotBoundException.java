package com.rentmanager.shared.security.context;

/**
 * Thrown when a tenant-scoped operation is attempted without a bound tenant
 * context (see {@link TenantContext#requireTenantId()}). This is an
 * AUTHORIZATION failure — the caller holds no tenant identity — so the API
 * layer maps it to 403, not 500/409. A stack-trace-shaped 500 would leak
 * implementation detail and mislead callers into retrying a request that
 * can never succeed without a tenant.
 *
 * Extends IllegalStateException so any pre-existing
 * {@code catch (IllegalStateException ...)} logic keeps working unchanged —
 * this type only ADDS a precise handler at the API boundary.
 */
public class TenantContextNotBoundException extends IllegalStateException {

    public TenantContextNotBoundException() {
        super("No tenant context bound for this request");
    }
}
