package com.rentmanager.modules.unit.factory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

@Component
public class UnitDbCleaner {

    @PersistenceContext
    private EntityManager em;

    public UnitDbCleaner(EntityManager em) {
        this.em = em;
    }

    @Transactional
    public void clean() {
        em.createNativeQuery("DELETE FROM units").executeUpdate();
    }
}