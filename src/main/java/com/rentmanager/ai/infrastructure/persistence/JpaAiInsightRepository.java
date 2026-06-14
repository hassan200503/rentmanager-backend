package com.rentmanager.ai.infrastructure.persistence;

import com.rentmanager.ai.infrastructure.persistence.AiInsightEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAiInsightRepository extends JpaRepository<AiInsightEntity, Long> {
}