package com.rentmanager.domain.base;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity implements Serializable {

    @Id
    @Column(nullable = false, updatable = false, unique = true)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            this.id = UUID.randomUUID();
        }

        Instant now = Instant.now();

        if (createdAt == null) {
            this.createdAt = now;
        }

        // ensures NOT NULL safety for first insert
        if (updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
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

    public void setId(UUID id) {
        this.id = id;
    }

    protected void restoreId(UUID id) {
        this.id = id;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}