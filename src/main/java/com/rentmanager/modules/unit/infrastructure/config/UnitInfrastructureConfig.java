package com.rentmanager.modules.unit.infrastructure.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = {
        "com.rentmanager.modules.unit"
})


public class UnitInfrastructureConfig {
}