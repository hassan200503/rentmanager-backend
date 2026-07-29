package com.rentmanager.modules.rentledger.infrastructure.persistence.adapter;

import com.rentmanager.modules.rentledger.domain.model.CommissionPolicy;
import com.rentmanager.modules.rentledger.domain.repository.CommissionPolicyRepository;
import com.rentmanager.modules.rentledger.infrastructure.persistence.mapper.CommissionPolicyPersistenceMapper;
import com.rentmanager.modules.rentledger.infrastructure.persistence.repository.CommissionPolicyJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CommissionPolicyRepositoryAdapter implements CommissionPolicyRepository {

    private final CommissionPolicyJpaRepository jpaRepository;
    private final CommissionPolicyPersistenceMapper mapper;

    @Override
    public CommissionPolicy save(CommissionPolicy policy) {
        return mapper.toDomain(jpaRepository.save(mapper.toJpaEntity(policy)));
    }

    @Override
    public Optional<CommissionPolicy> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<CommissionPolicy> findActiveDefault() {
        return jpaRepository.findByLandlordOrgIdIsNullAndActiveTrue().map(mapper::toDomain);
    }

    @Override
    public Optional<CommissionPolicy> findActiveByLandlordOrgId(UUID landlordOrgId) {
        return jpaRepository.findByLandlordOrgIdAndActiveTrue(landlordOrgId).map(mapper::toDomain);
    }
}
