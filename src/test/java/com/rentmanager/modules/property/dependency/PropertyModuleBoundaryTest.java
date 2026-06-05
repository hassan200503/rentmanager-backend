package com.rentmanager.modules.property.dependency;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class PropertyModuleBoundaryTest {

    @Test
    void shouldRespectModuleBoundaries() {

        JavaClasses imported = new ClassFileImporter()
                .importPackages("com.rentmanager.modules.property");

        noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .check(imported);
    }
}