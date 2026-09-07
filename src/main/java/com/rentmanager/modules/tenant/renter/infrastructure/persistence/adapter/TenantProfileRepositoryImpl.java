package com.rentmanager.modules.tenant.renter.infrastructure.persistence.adapter;

import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.tenant.renter.infrastructure.persistence.entity.TenantProfileEntity;
import com.rentmanager.modules.tenant.renter.infrastructure.persistence.repository.TenantProfileJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TenantProfileRepositoryImpl implements TenantProfileRepository {

    private final TenantProfileJpaRepository jpaRepository;

    @Override
    public TenantProfile save(TenantProfile profile) {
        TenantProfileEntity entity = toEntity(profile);
        TenantProfileEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<TenantProfile> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<TenantProfile> findByTenantIdAndClerkUserId(UUID tenantId, String clerkUserId) {
        return jpaRepository.findByTenantIdAndClerkUserId(tenantId, clerkUserId).map(this::toDomain);
    }

    @Override
    public Optional<TenantProfile> findByClerkUserId(String clerkUserId) {
        return jpaRepository.findByClerkUserId(clerkUserId).map(this::toDomain);
    }

    @Override
    public boolean existsByClerkUserId(String clerkUserId) {
        return jpaRepository.existsByClerkUserId(clerkUserId);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public boolean existsByTenantIdAndEmail(UUID tenantId, String email) {
        return jpaRepository.existsByTenantIdAndEmail(tenantId, email);
    }

    private TenantProfileEntity toEntity(TenantProfile p) {
        return new TenantProfileEntity(
                p.getId(),
                p.getTenantId(),
                p.getClerkUserId(),
                p.getFullName(),
                p.getEmail(),
                p.getPhone(),
                p.getNationalId(),
                p.isWhatsAppOptIn(),
                p.getKraPin()
        );
    }

    private TenantProfile toDomain(TenantProfileEntity e) {
        return TenantProfile.rehydrate(
                e.getId(),
                e.getTenantId(),
                e.getClerkUserId(),
                e.getFullName(),
                e.getEmail(),
                e.getPhone(),
                e.getNationalId(),
                e.isWhatsAppOptIn(),
                e.getKraPin()
        );
    }


    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<TenantProfile> findAllById(Collection<UUID> ids) {
        return jpaRepository.findAllById(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public List<TenantProfile> searchByNameOrPhone(UUID tenantId, String keyword) {
        return jpaRepository.searchByNameOrPhone(tenantId, keyword).stream().map(this::toDomain).toList();
    }
}