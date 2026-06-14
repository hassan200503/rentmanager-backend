package com.rentmanager.modules.property.domain.model;

import com.rentmanager.domain.base.BaseTenantEntity;
import com.rentmanager.modules.property.domain.enums.MediaType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "property_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PropertyMedia extends BaseTenantEntity {


    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "file_name")
    private String fileName;
}