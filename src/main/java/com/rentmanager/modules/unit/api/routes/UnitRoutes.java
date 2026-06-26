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

    public static final String MEDIA =
            BASE + "/{unitId}/media";

    public static final String MEDIA_BY_ID =
            MEDIA + "/{mediaId}";

    public static final String SET_PRIMARY_MEDIA =
            MEDIA_BY_ID + "/primary";

    public static final String UPDATE_MEDIA =
            MEDIA_BY_ID;

    public static final String REORDER_MEDIA =
            MEDIA + "/reorder";
}