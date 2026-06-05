package com.rentmanager.modules.auth.infrastructure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "permissions")
@Getter
@Setter
public class PermissionEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;
}