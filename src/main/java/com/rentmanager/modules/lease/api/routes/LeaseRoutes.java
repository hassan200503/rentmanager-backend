package com.rentmanager.modules.lease.api.routes;

public final class LeaseRoutes {

    private LeaseRoutes() {}

    public static final String BASE = "/api/v1/leases";

    public static final String CREATE = "";
    public static final String GET_BY_ID = "/{leaseId}";
    public static final String GET_ALL = "";
    public static final String UPDATE = "/{leaseId}";
    public static final String TERMINATE = "/{leaseId}/terminate";
    public static final String APPROVE = "/{leaseId}/approve";
    public static final String CANCEL = "/{leaseId}/cancel";
    public static final String RENEW = "/{leaseId}/renew";
}