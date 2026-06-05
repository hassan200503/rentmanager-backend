package com.rentmanager.modules.unit.infrastructure.persistence.specification;

import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitJpaEntity;
import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitMediaJpaEntity;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class UnitMediaSpecification {

    /**
     * Units that have any media at all.
     */
    public static Specification<UnitJpaEntity> hasAnyMedia(UUID tenantId) {
        return (root, query, cb) -> {
            query.distinct(true);

            root.join("media", JoinType.INNER);

            return cb.equal(root.get("tenantId"), tenantId);
        };
    }

    /**
     * Units that have a cover image.
     * Assumes UnitMediaJpaEntity has a boolean field: isCover
     */
    public static Specification<UnitJpaEntity> hasCoverImage(UUID tenantId) {
        return (root, query, cb) -> {
            query.distinct(true);

            Join<Object, Object> media = root.join("media", JoinType.INNER);

            return cb.and(
                    cb.equal(root.get("tenantId"), tenantId),
                    cb.isTrue(media.get("isCover"))
            );
        };
    }

    /**
     * Units filtered by media existence using URL (safe fallback filter)
     * Useful if you want "units with images/videos" without classification enums.
     */
    public static Specification<UnitJpaEntity> hasMediaWithUrl(UUID tenantId) {
        return (root, query, cb) -> {
            query.distinct(true);

            Join<Object, Object> media = root.join("media", JoinType.INNER);

            return cb.and(
                    cb.equal(root.get("tenantId"), tenantId),
                    cb.isNotNull(media.get("url"))
            );
        };
    }
}