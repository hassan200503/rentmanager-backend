package com.rentmanager.modules.property.application.command.service;

import com.rentmanager.modules.property.application.dto.request.ReorderPropertyMediaRequest;
import com.rentmanager.modules.property.domain.model.PropertyMedia;
import com.rentmanager.modules.property.domain.repository.PropertyMediaRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PropertyMediaCommandServiceImpl
        implements PropertyMediaCommandService {

    private final PropertyMediaRepository propertyMediaRepository;

    @Override
    public void setPrimaryMedia(
            UUID tenantId,
            UUID propertyId,
            UUID mediaId
    ) {

        PropertyMedia targetMedia = propertyMediaRepository
                .findByIdAndTenantId(mediaId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Property media not found",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        if (!targetMedia.getPropertyId().equals(propertyId)) {
            throw new ResourceNotFoundException(
                    "Property media not found",
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }

        propertyMediaRepository
                .findByTenantIdAndPropertyIdAndPrimaryMediaTrue(
                        tenantId,
                        propertyId
                )
                .ifPresent(existingPrimary -> {

                    if (existingPrimary.getId().equals(mediaId)) {
                        return;
                    }

                    PropertyMedia demoted = PropertyMedia.rehydrate(
                            existingPrimary.getId(),
                            existingPrimary.getTenantId(),
                            existingPrimary.getPropertyId(),
                            existingPrimary.getMediaType(),
                            existingPrimary.getFileUrl(),
                            existingPrimary.getFileName(),
                            existingPrimary.getPublicId(),
                            existingPrimary.getContentType(),
                            existingPrimary.getFileSize(),
                            false,
                            existingPrimary.getUploadedAt(),
                            existingPrimary.getCaption(),
                            existingPrimary.getSortOrder(),
                            existingPrimary.getVersion()
                    );

                    propertyMediaRepository.save(demoted);
                });

        PropertyMedia promoted = PropertyMedia.rehydrate(
                targetMedia.getId(),
                targetMedia.getTenantId(),
                targetMedia.getPropertyId(),
                targetMedia.getMediaType(),
                targetMedia.getFileUrl(),
                targetMedia.getFileName(),
                targetMedia.getPublicId(),
                targetMedia.getContentType(),
                targetMedia.getFileSize(),
                true,
                targetMedia.getUploadedAt(),
                targetMedia.getCaption(),
                targetMedia.getSortOrder(),
                targetMedia.getVersion()
        );

        propertyMediaRepository.save(promoted);
    }




    @Override
    public void updateCaption(
            UUID tenantId,
            UUID propertyId,
            UUID mediaId,
            String caption
    ) {

        PropertyMedia media = propertyMediaRepository
                .findByIdAndTenantId(mediaId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Property media not found",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        if (!media.getPropertyId().equals(propertyId)) {
            throw new ResourceNotFoundException(
                    "Property media not found",
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }

        PropertyMedia updated = PropertyMedia.rehydrate(
                media.getId(),
                media.getTenantId(),
                media.getPropertyId(),
                media.getMediaType(),
                media.getFileUrl(),
                media.getFileName(),
                media.getPublicId(),
                media.getContentType(),
                media.getFileSize(),
                media.isPrimaryMedia(),
                media.getUploadedAt(),
                caption,
                media.getSortOrder(),
                media.getVersion()
        );

        propertyMediaRepository.save(updated);
    }



    @Transactional
    @Override
    public void reorderMedia(
            UUID tenantId,
            UUID propertyId,
            List<ReorderPropertyMediaRequest> items
    ) {

        List<PropertyMedia> mediaList =
                propertyMediaRepository.findAllByTenantIdAndPropertyId(
                        tenantId,
                        propertyId
                );

        Map<UUID, PropertyMedia> mediaMap =
                mediaList.stream()
                        .collect(Collectors.toMap(
                                PropertyMedia::getId,
                                Function.identity()
                        ));

        for (ReorderPropertyMediaRequest item : items) {

            PropertyMedia media = mediaMap.get(item.mediaId());

            if (media == null) {
                throw new ResourceNotFoundException(
                        "Property media not found",
                        ErrorCode.RESOURCE_NOT_FOUND
                );
            }

            PropertyMedia updated = PropertyMedia.rehydrate(
                    media.getId(),
                    media.getTenantId(),
                    media.getPropertyId(),
                    media.getMediaType(),
                    media.getFileUrl(),
                    media.getFileName(),
                    media.getPublicId(),
                    media.getContentType(),
                    media.getFileSize(),
                    media.isPrimaryMedia(),
                    media.getUploadedAt(),
                    media.getCaption(),
                    item.sortOrder(),
                    media.getVersion()
            );

            propertyMediaRepository.save(updated);
        }
    }
}