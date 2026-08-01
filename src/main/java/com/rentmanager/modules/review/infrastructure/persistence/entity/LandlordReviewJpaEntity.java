package com.rentmanager.modules.review.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "landlord_reviews",
        indexes = {
                @Index(name = "idx_landlord_reviews_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_landlord_reviews_tenant_profile_id", columnList = "tenant_profile_id")
        }
)
public class LandlordReviewJpaEntity extends BaseTenantEntity {

    @Column(name = "tenant_profile_id", nullable = false)
    private UUID tenantProfileId;

    @Column(name = "lease_id", nullable = false)
    private UUID leaseId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment", length = 1000)
    private String comment;
}
