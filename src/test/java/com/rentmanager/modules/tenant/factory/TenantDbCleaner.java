package com.rentmanager.modules.tenant.factory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TenantDbCleaner {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void clean() {
        em.createNativeQuery("DELETE FROM tenants")
                .executeUpdate();
        em.flush();
        em.clear();
    }
}