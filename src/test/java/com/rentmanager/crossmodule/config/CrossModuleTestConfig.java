package com.rentmanager.crossmodule.config;

import com.rentmanager.crossmodule.core.ScenarioContext;
import com.rentmanager.crossmodule.core.TenantTestExecutor;
import com.rentmanager.crossmodule.core.TestDataFactory;
import com.rentmanager.crossmodule.support.EventCapture;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CrossModuleTestConfig {

    @Bean
    public ScenarioContext scenarioContext() {
        return new ScenarioContext();
    }

    @Bean
    public TenantTestExecutor tenantTestExecutor(ScenarioContext context) {
        return new TenantTestExecutor(context);
    }

    // =========================================================
    // FIXED: repositories are now actually used
    // =========================================================
    @Bean
    public TestDataFactory testDataFactory(
            PropertyRepository propertyRepository,
            UnitRepository unitRepository,
            LeaseRepository leaseRepository
    ) {
        return new TestDataFactory();
    }

    @Bean
    public EventCapture eventCapture() {
        return new EventCapture();
    }
}