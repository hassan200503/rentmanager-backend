package com.rentmanager.modules.property.infrastructure.security;

public final class PropertyPermission {

    private PropertyPermission() {
    }

    public static final String PROPERTY_CREATE = "PROPERTY_CREATE";
    public static final String PROPERTY_UPDATE = "PROPERTY_UPDATE";
    public static final String PROPERTY_DELETE = "PROPERTY_DELETE";
    public static final String PROPERTY_VIEW = "PROPERTY_VIEW";
}