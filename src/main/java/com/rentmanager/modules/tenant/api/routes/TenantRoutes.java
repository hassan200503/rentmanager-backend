package com.rentmanager.modules.tenant.api.routes;

public final class TenantRoutes {

    private TenantRoutes() {}

    public static final String BASE = "/api/v1/tenants";

    public static final String SUBSCRIPTION_PLANS = BASE + "/subscription-plans";

    public static final String BY_ID = "/{id}";

    public static final String BY_CODE = "/code/{code}";

    public static final String DEACTIVATE = "/{id}/deactivate";
}