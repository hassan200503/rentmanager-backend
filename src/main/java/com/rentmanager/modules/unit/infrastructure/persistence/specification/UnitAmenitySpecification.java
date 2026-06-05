package com.rentmanager.modules.unit.infrastructure.persistence.specification;

import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitJpaEntity;
import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitAmenityJpaEntity;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

public class UnitAmenitySpecification {

    /**
     * Filters units that have ANY of the given amenity names.
     */
    public static Specification<UnitJpaEntity> hasAnyAmenity(List<String> amenityNames, UUID tenantId) {
        return (root, query, cb) -> {
            if (amenityNames == null || amenityNames.isEmpty()) {
                return cb.conjunction();
            }

            query.distinct(true);

            Join<UnitJpaEntity, UnitAmenityJpaEntity> amenities = root.join("amenities", JoinType.INNER);

            return cb.and(
                    cb.equal(root.get("tenantId"), tenantId),
                    amenities.get("name").in(amenityNames)
            );
        };
    }

    /**
     * Filters units that contain ALL given amenities.
     * (More strict matching using grouping)
     */
    public static Specification<UnitJpaEntity> hasAllAmenities(List<String> amenityNames, UUID tenantId) {
        return (root, query, cb) -> {
            if (amenityNames == null || amenityNames.isEmpty()) {
                return cb.conjunction();
            }

            query.distinct(true);

            Join<UnitJpaEntity, UnitAmenityJpaEntity> amenities = root.join("amenities", JoinType.INNER);

            Predicate tenantPredicate = cb.equal(root.get("tenantId"), tenantId);
            Predicate amenityPredicate = amenities.get("name").in(amenityNames);

            query.groupBy(root.get("id"));
            query.having(cb.equal(cb.count(amenities.get("id")), amenityNames.size()));

            return cb.and(tenantPredicate, amenityPredicate);
        };
    }
}