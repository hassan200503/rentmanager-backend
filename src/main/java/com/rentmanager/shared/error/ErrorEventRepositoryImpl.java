package com.rentmanager.shared.error;

import com.rentmanager.shared.error.persistence.JpaErrorEventRepository;
import com.rentmanager.shared.error.persistence.ErrorEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ErrorEventRepositoryImpl implements ErrorEventRepository {

    private final JpaErrorEventRepository jpaRepository;

    @Override
    public void save(ErrorEvent event) {
        jpaRepository.save(ErrorEventMapper.toEntity(event));
    }
}