package com.rentmanager.crossmodule.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

public class PostgresSpringBridge
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        PostgresTestContainerConfig.INSTANCE.start();
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(ctx,
                "spring.datasource.url=" + PostgresTestContainerConfig.INSTANCE.getJdbcUrl(),
                "spring.datasource.username=" + PostgresTestContainerConfig.INSTANCE.getUsername(),
                "spring.datasource.password=" + PostgresTestContainerConfig.INSTANCE.getPassword(),
                "spring.datasource.driver-class-name=" + PostgresTestContainerConfig.INSTANCE.getDriverClassName()
        );
    }
}
