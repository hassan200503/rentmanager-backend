package com.rentmanager.modules.support;

import com.rentmanager.crossmodule.support.PostgresSpringBridge;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresSpringBridge.class)
public abstract class AbstractPostgresIntegrationTest {
}
