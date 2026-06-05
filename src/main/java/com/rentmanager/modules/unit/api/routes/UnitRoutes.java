package com.rentmanager.modules.unit.api.routes;

public final class UnitRoutes {

    private UnitRoutes() {
    }

    public static final String BASE = "/api/v1/units";

    public static final String CREATE = BASE;

    public static final String UPDATE = BASE + "/{unitId}";

    public static final String DELETE = BASE + "/{unitId}";

    public static final String ACTIVATE = BASE + "/{unitId}/activate";

    public static final String DEACTIVATE = BASE + "/{unitId}/deactivate";

    public static final String GET_BY_ID = BASE + "/{unitId}";

    public static final String GET_ALL = BASE;

    public static final String SEARCH = BASE + "/search";

    public static final String GET_BY_PROPERTY = BASE + "/property/{propertyId}";
}