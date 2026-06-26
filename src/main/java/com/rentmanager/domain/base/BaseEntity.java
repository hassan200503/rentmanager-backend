package com.rentmanager.domain.base;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity implements Serializable {

    @Setter
    @Getter
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Setter
    @Getter
    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {

        Instant now = Instant.now();

        // FIX: single source of truth for ID generation
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }

        // FIX: prevent Hibernate version null crash
        if (this.version == null) {
            this.version = 0L;
        }

        if (this.createdAt == null) {
            this.createdAt = now;
        }

        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        if (createdAt == null) {
            return Instant.now();
        }
        return createdAt;
    }

    public Instant getUpdatedAt() {
        if (updatedAt == null) {
            return Instant.now();
        }
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public void restoreId(UUID id) {
        this.id = id;
    }
}