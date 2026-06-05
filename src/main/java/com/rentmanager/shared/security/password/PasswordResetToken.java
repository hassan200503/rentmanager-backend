package com.rentmanager.shared.security.password;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
public class PasswordResetToken {

    @Id
    private UUID id;

    private UUID userId;

    private String token;

    private Instant expiresAt;

    private boolean used;
}