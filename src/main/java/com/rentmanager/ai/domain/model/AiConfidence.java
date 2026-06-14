package com.rentmanager.ai.domain.model;

import java.math.BigDecimal;

public record AiConfidence(BigDecimal score) {

    public AiConfidence {
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.ONE) > 1) {
            throw new IllegalArgumentException("Confidence must be between 0 and 1");
        }
    }
}