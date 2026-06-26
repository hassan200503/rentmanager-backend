package com.rentmanager.modules.property.application.query.service;

import com.rentmanager.modules.property.application.dto.response.PropertyMediaResponse;
import com.rentmanager.modules.property.application.query.service.PropertyMediaQueryService;
import com.rentmanager.modules.property.domain.repository.PropertyMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PropertyMediaQueryServiceImpl
        implements PropertyMediaQueryService {

    private final PropertyMediaRepository propertyMediaRepository;

    @Override
    public List<PropertyMediaResponse> getPropertyMedia(
            UUID tenantId,
            UUID propertyId
    ) {
        return propertyMediaRepository
                .findAllByTenantIdAndPropertyId(
                        tenantId,
                        propertyId
                )
                .stream()
                .map(media -> new PropertyMediaResponse(
                        media.getId(),
                        media.getPropertyId(),
                        media.getFileUrl(),
                        media.getFileName(),
                        media.getMediaType().name(),
                        media.isPrimaryMedia(),
                        media.getCaption(),
                        media.getSortOrder()
                ))
                .toList();
    }
}