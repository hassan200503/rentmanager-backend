package com.rentmanager.modules.integration.domain.model;

/**
 * The two credential environments every provider can carry. Development and
 * Production configs for a given provider are independent records that are
 * never merged and never fall back on each other.
 */
public enum IntegrationEnvironment {
    DEVELOPMENT,
    PRODUCTION
}