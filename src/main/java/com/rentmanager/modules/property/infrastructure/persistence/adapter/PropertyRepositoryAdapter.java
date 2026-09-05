package com.rentmanager.modules.property.infrastructure.persistence.adapter;

import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity;
import com.rentmanager.modules.property.infrastructure.persistence.mapper.PropertyPersistenceMapper;
import com.rentmanager.modules.property.infrastructure.persistence.repository.PropertyJpaRepository;
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
    private final PropertyPersistenceMapper persistenceMapper;

    @Override
    @Transactional
    public Property save(Property property) {

        if (property == null) {
            throw new IllegalArgumentException("Property cannot be null");
        }

        PropertyJpaEntity entity;

        if (property.getId() != null) {

            entity = jpaRepository.findById(property.getId())
                    .map(existing -> {
                        PropertyPersistenceMapper.updateEntity(property, existing);
                        return existing;
                    })
                    .orElseGet(() -> persistenceMapper.toJpaEntity(property));

        } else {
            entity = persistenceMapper.toJpaEntity(property);
        }

        PropertyJpaEntity saved = jpaRepository.save(entity);

        return persistenceMapper.toDomain(saved);
    }

    @Override
    public Optional<Property> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(persistenceMapper::toDomain);
    }

    @Override
    public Optional<Property> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findByIdAndTenantId(id, tenantId)
                .map(persistenceMapper::toDomain);
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
                .map(persistenceMapper::toDomain);
    }

    @Override
    public Page<Property> search(String keyword, Pageable pageable) {
        return jpaRepository.searchByNameContainingIgnoreCase(keyword, pageable)
                .map(persistenceMapper::toDomain);
    }

    @Override
    public Page<Property> search(String tenantId, String keyword, Pageable pageable) {
        return jpaRepository.searchByNameContainingIgnoreCase(keyword, pageable)
                .map(persistenceMapper::toDomain);
    }

    @Override
    public Page<Property> searchByTenantId(UUID tenantId, String keyword, Pageable pageable) {
        return jpaRepository.findByTenantIdAndNameContainingIgnoreCase(tenantId, keyword, pageable)
                .map(persistenceMapper::toDomain);
    }

    @Override
    public List<Property> findByOwnerId(UUID ownerId) {
        return jpaRepository.findByOwnerId(ownerId)
                .stream()
                .map(persistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<Property> findByStatus(String status) {
        // FIX: convert String -> PropertyStatus enum before hitting the repository.
        // Passing a raw String against an @Enumerated(EnumType.STRING) field threw
        // Hibernate's QueryArgumentException under Hibernate 6.4's stricter binding.
        PropertyStatus propertyStatus = PropertyStatus.valueOf(status);
        return jpaRepository.findByStatus(propertyStatus)
                .stream()
                .map(persistenceMapper::toDomain)
                .toList();
    }

    @Override
    public Page<Property> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable)
                .map(persistenceMapper::toDomain);
    }

    @Override
    public List<Property> findByOwnerIdAndTenantId(UUID ownerId, UUID tenantId) {
        // FIX: previously delegated to findByOwnerId(ownerId) alone, silently ignoring
        // tenantId and returning properties across all tenants. Now properly scoped.
        return jpaRepository.findByOwnerIdAndTenantId(ownerId, tenantId)
                .stream()
                .map(persistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<Property> findByStatusAndTenantId(String status, UUID tenantId) {
        // FIX: previously delegated to findByStatus(status) alone, silently ignoring
        // tenantId (cross-tenant leak) AND passing a raw String against the enum
        // column (Hibernate crash). Both fixed here: convert to enum, scope by tenant.
        PropertyStatus propertyStatus = PropertyStatus.valueOf(status);
        return jpaRepository.findByStatusAndTenantId(propertyStatus, tenantId)
                .stream()
                .map(persistenceMapper::toDomain)
                .toList();
    }

    // =====================================================
    // PUBLIC LISTING HARDENING (2026-07-08)
    // =====================================================

    @Override
    public Page<Property> findByStatus(PropertyStatus status, Pageable pageable) {
        return jpaRepository.findByStatus(status, pageable)
                .map(persistenceMapper::toDomain);
    }

    @Override
    public Page<Property> searchByStatus(String keyword, PropertyStatus status, Pageable pageable) {
        return jpaRepository.findByStatusAndNameContainingIgnoreCase(status, keyword, pageable)
                .map(persistenceMapper::toDomain);
    }

    @Override
    public Page<Property> searchByStatusAndLocation(
            String keyword,
            String location,
            PropertyStatus status,
            Pageable pageable
    ) {
        // Null-safe: Hibernate 6.4 binds null String params as bytea on
        // Postgres (LOWER() then fails); "" is the documented no-filter value.
        return jpaRepository.searchPublic(
                        keyword == null ? "" : keyword,
                        location == null ? "" : location,
                        status,
                        pageable
                )
                .map(persistenceMapper::toDomain);
    }

    @Override
    public Optional<Property> findByIdAndStatus(UUID id, PropertyStatus status) {
        return jpaRepository.findByIdAndStatus(id, status)
                .map(persistenceMapper::toDomain);
    }

    @Override
    public List<Property> findAllByIdInAndStatus(List<UUID> ids, PropertyStatus status) {
        // An IN () with no values is a syntax error on Postgres.
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllByIdInAndStatus(ids, status)
                .stream()
                .map(persistenceMapper::toDomain)
                .toList();
    }
}