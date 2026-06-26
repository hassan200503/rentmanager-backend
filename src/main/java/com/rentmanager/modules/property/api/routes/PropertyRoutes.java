package com.rentmanager.modules.property.api.routes;

public final class PropertyRoutes {

    private PropertyRoutes() {}

    public static final String BASE = "/api/v1/properties";

    public static final String BY_ID = BASE + "/{propertyId}";
    public static final String ACTIVATE = BASE + "/{propertyId}/activate";
    public static final String ARCHIVE = BASE + "/{propertyId}/archive";


    public static final String MEDIA_UPLOAD =
            BASE + "/{propertyId}/media";

    public static final String MEDIA_DELETE =
            BASE + "/{propertyId}/media/{mediaId}";


    public static final String SET_PRIMARY_MEDIA =
            BASE + "/{propertyId}/media/{mediaId}/primary";



    public static final String UPDATE_MEDIA =
            BASE + "/{propertyId}/media/{mediaId}";




    public static final String MEDIA = BY_ID + "/media";
    public static final String MEDIA_BY_ID = MEDIA + "/{mediaId}";

    public static final String REORDER_MEDIA = MEDIA + "/reorder";
}