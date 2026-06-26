package com.rentmanager.modules.unit.application.query.service;

import com.rentmanager.modules.unit.domain.repository.UnitMediaRepository;
import com.rentmanager.shared.dto.MediaUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnitMediaQueryServiceImpl implements UnitMediaQueryService {

    private final UnitMediaRepository unitMediaRepository;

    @Override
    public List<MediaUploadResponse> getUnitMedia(
            UUID tenantId,
            UUID unitId
    ) {

        return unitMediaRepository
                .findAllByTenantIdAndUnitId(
                        tenantId,
                        unitId
                )
                .stream()
                .sorted((a, b) ->
                        Integer.compare(
                                a.getSortOrder(),
                                b.getSortOrder()
                        )
                )
                .map(media -> new MediaUploadResponse(
                        media.getId(),
                        media.getTenantId(),
                        unitId,
                        media.getUrl(),
                        media.getType(),
                        media.getCaption(),
                        media.isPrimary(),
                        media.getSortOrder()
                ))
                .toList();
    }
}