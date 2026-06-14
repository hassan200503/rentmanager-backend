package com.rentmanager.modules.property.infrastructure.persistence.adapter;

import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity;
import com.rentmanager.modules.property.infrastructure.persistence.mapper.PropertyPersistenceMapper;
import com.rentmanager.modules.property.infrastructure.persistence.repository.PropertyJpaRepository;
import com.rentmanager.shared.security.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PropertyRepositoryAdapter implements PropertyRepository {

    private final PropertyJpaRepository jpaRepository;

    // =========================================================
    // SAVE

    @Override
    @Transactional
    public Property save(Property property) {

        if (property == null) {
            throw new IllegalArgumentException("Property cannot be null");
        }

        // =========================
        // CREATE FLOW (ID NOT IN DB YET)
        // =========================
        boolean isNew = !jpaRepository.existsById(property.getId());

        if (isNew) {

            PropertyJpaEntity entity = toEntity(property);

            // IMPORTANT: ensure JPA treats as insert
            entity.setVersion(null);

            PropertyJpaEntity saved = jpaRepository.save(entity);

            return toDomain(saved);
        }

        // =========================
        // UPDATE FLOW (MUST EXIST)
        // =========================
        PropertyJpaEntity existing = jpaRepository.findById(property.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Property not found: " + property.getId()
                ));

        PropertyPersistenceMapper.updateEntity(property, existing);

        PropertyJpaEntity saved = jpaRepository.save(existing);

        return toDomain(saved);
    }
    // =========================================================
    // FIND BY ID
    // =========================================================
    @Override
    public Optional<Property> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public Optional<Property> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findByIdAndTenantId(id, tenantId)
                .map(this::toDomain);
    }

    @Override
    public void delete(Property property) {
        jpaRepository.deleteById(property.getId());
    }

    @Override
    public boolean existsByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.existsByIdAndTenantId(id, tenantId);
    }

    @Override
    public boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name) {
        return jpaRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name);
    }

    @Override
    public Page<Property> findAllByTenantId(UUID tenantId, Pageable pageable) {
        return jpaRepository.findAllByTenantId(tenantId, pageable)
                .map(this::toDomain);
    }

    @Override
    public Page<Property> search(String keyword, Pageable pageable) {
        return jpaRepository.searchByNameContainingIgnoreCase(keyword, pageable)
                .map(this::toDomain);
    }

    @Override
    public Page<Property> search(String tenantId, String keyword, Pageable pageable) {
        return jpaRepository.searchByNameContainingIgnoreCase(keyword, pageable)
                .map(this::toDomain);
    }

    @Override
    public Page<Property> searchByTenantId(UUID tenantId, String keyword, Pageable pageable) {
        return jpaRepository.findByTenantIdAndNameContainingIgnoreCase(tenantId, keyword, pageable)
                .map(this::toDomain);
    }

    @Override
    public List<Property> findByOwnerId(UUID ownerId) {
        return jpaRepository.findByOwnerId(ownerId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Property> findByStatus(String status) {
        return jpaRepository.findByStatus(status)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Page<Property> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable)
                .map(this::toDomain);
    }

    @Override
    public List<Property> findByOwnerIdAndTenantId(UUID ownerId, UUID tenantId) {
        return jpaRepository.findByOwnerId(ownerId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Property> findByStatusAndTenantId(String status, UUID tenantId) {
        return jpaRepository.findByStatus(status)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    // =========================================================
    // MAPPING (NO SETTERS ON DOMAIN)
    // =========================================================

    private Property toDomain(PropertyJpaEntity e) {

        if (e == null) return null;

        return Property.rehydrate(
                e.getId(),
                e.getTenantId(),
                e.getName(),
                e.getReferenceCode(),
                e.getPropertyType(),
                e.getStatus(),
                e.getOccupancyStatus(),
                null, // map later safely
                null,
                null,
                e.getDescription()
        );
    }
    private PropertyJpaEntity toEntity(Property p) {

        if (p == null) return null;

        PropertyJpaEntity e = new PropertyJpaEntity();

        // ==========================
        // ID SAFETY RULE
        // ==========================
        // Only set ID if explicitly present AND this is NOT create flow
        if (p.getId() != null) {
            e.setId(p.getId());
        }

        // ==========================
        // TENANT SAFETY RULE
        // ==========================
        // DO NOT override domain tenant with context blindly
        e.assignTenant(p.getTenantId());

        e.setName(p.getName());
        e.setReferenceCode(p.getReferenceCode());
        e.setPropertyType(p.getPropertyType());
        e.setStatus(p.getStatus());
        e.setOccupancyStatus(p.getOccupancyStatus());
        e.setDescription(p.getDescription());

        return e;
    }
}