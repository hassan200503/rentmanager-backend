package com.rentmanager.ai.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataAiInsightRepository
        extends JpaRepository<AiInsightJpaEntity, Long> {
}