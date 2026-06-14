package com.rentmanager.crossmodule.support;

import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Configuration
public class PostgresSpringBridge {

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url",
                PostgresTestContainerConfig.INSTANCE::getJdbcUrl);

        registry.add("spring.datasource.username",
                PostgresTestContainerConfig.INSTANCE::getUsername);

        registry.add("spring.datasource.password",
                PostgresTestContainerConfig.INSTANCE::getPassword);

        registry.add("spring.datasource.driver-class-name",
                PostgresTestContainerConfig.INSTANCE::getDriverClassName);
    }
}