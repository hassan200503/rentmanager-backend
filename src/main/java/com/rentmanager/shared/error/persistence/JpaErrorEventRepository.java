package com.rentmanager.shared.error.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaErrorEventRepository extends JpaRepository<ErrorEventEntity, UUID> {
}