package com.rentmanager.crossmodule.core;

import com.rentmanager.crossmodule.config.CrossModuleTestConfig;
import com.rentmanager.crossmodule.support.DatabaseCleaner;
import com.rentmanager.crossmodule.support.EventCapture;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest
@Import(CrossModuleTestConfig.class)
@Transactional
public abstract class CrossModuleBaseIT {

    @Autowired
    protected ScenarioContext context;

    @Autowired
    protected TestDataFactory factory;

    @Autowired
    protected EventCapture eventCapture;

    @Autowired
    protected DatabaseCleaner databaseCleaner;

    protected void reset() {
        eventCapture.clear();
        databaseCleaner.cleanAll();
        context.clear();
    }
}