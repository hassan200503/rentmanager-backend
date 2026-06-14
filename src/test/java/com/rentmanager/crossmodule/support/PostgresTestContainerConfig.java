package com.rentmanager.crossmodule.support;

import org.testcontainers.containers.PostgreSQLContainer;

public final class PostgresTestContainerConfig {

    public static final PostgreSQLContainer<?> INSTANCE =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("rentmanager_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true);

    static {
        INSTANCE.start();
    }

    private PostgresTestContainerConfig() {}
}