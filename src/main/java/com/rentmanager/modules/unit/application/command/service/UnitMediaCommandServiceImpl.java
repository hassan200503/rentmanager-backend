package com.rentmanager.modules.unit.application.command.service;

import com.rentmanager.modules.unit.domain.model.UnitMedia;
import com.rentmanager.modules.unit.domain.repository.UnitMediaRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UnitMediaCommandServiceImpl
        implements UnitMediaCommandService {

    private final UnitMediaRepository unitMediaRepository;

    @Override
    public void setPrimaryMedia(
            UUID tenantId,
            UUID unitId,
            UUID mediaId
    ) {

        UnitMedia targetMedia = unitMediaRepository
                .findByIdAndTenantId(mediaId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unit media not found",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        if (!targetMedia.getUnitId().equals(unitId)) {
            throw new ResourceNotFoundException(
                    "Unit media not found",
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }

        unitMediaRepository
                .findByTenantIdAndUnitIdAndPrimaryMediaTrue(
                        tenantId,
                        unitId
                )
                .ifPresent(existingPrimary -> {

                    if (existingPrimary.getId().equals(mediaId)) {
                        return;
                    }

                    UnitMedia demoted = UnitMedia.rehydrate(
                            existingPrimary.getId(),
                            existingPrimary.getTenantId(),
                            existingPrimary.getUnitId(),
                            existingPrimary.getUrl(),
                            existingPrimary.getType(),
                            existingPrimary.getCaption(),
                            false,
                            existingPrimary.getSortOrder()
                    );

                    unitMediaRepository.save(demoted);
                });

        UnitMedia promoted = UnitMedia.rehydrate(
                targetMedia.getId(),
                targetMedia.getTenantId(),
                targetMedia.getUnitId(),
                targetMedia.getUrl(),
                targetMedia.getType(),
                targetMedia.getCaption(),
                true,
                targetMedia.getSortOrder()
        );

        unitMediaRepository.save(promoted);
    }

    @Override
    public void updateCaption(
            UUID tenantId,
            UUID unitId,
            UUID mediaId,
            String caption
    ) {

        UnitMedia media = unitMediaRepository
                .findByIdAndTenantId(mediaId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unit media not found",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        if (!media.getUnitId().equals(unitId)) {
            throw new ResourceNotFoundException(
                    "Unit media not found",
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }

        UnitMedia updated = UnitMedia.rehydrate(
                media.getId(),
                media.getTenantId(),
                media.getUnitId(),
                media.getUrl(),
                media.getType(),
                caption,
                media.isPrimary(),
                media.getSortOrder()
        );

        unitMediaRepository.save(updated);
    }

    @Override
    public void reorderMedia(
            UUID tenantId,
            UUID unitId,
            List<UUID> mediaIdsInOrder
    ) {

        List<UnitMedia> mediaList =
                unitMediaRepository.findAllByTenantIdAndUnitId(
                        tenantId,
                        unitId
                );

        if (mediaList.size() != mediaIdsInOrder.size()) {
            throw new IllegalArgumentException(
                    "All unit media IDs must be provided for reordering."
            );
        }

        for (int i = 0; i < mediaIdsInOrder.size(); i++) {

            UUID mediaId = mediaIdsInOrder.get(i);

            UnitMedia media = mediaList.stream()
                    .filter(m -> m.getId().equals(mediaId))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Unit media not found",
                            ErrorCode.RESOURCE_NOT_FOUND
                    ));

            UnitMedia reordered = UnitMedia.rehydrate(
                    media.getId(),
                    media.getTenantId(),
                    media.getUnitId(),
                    media.getUrl(),
                    media.getType(),
                    media.getCaption(),
                    media.isPrimary(),
                    i
            );

            unitMediaRepository.save(reordered);
        }
    }
}