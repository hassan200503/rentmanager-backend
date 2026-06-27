package com.rentmanager.modules.unit.application.query.service;

import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.unit.application.dto.response.PublicUnitResponse;
import com.rentmanager.modules.unit.application.mapper.UnitMapper;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.model.UnitMedia;
import com.rentmanager.modules.unit.domain.repository.UnitMediaRepository;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicUnitQueryServiceImpl implements PublicUnitQueryService {

    private final UnitRepository unitRepository;
    private final UnitMediaRepository unitMediaRepository;
    private final UnitMapper unitMapper;
    private final PropertyRepository propertyRepository; // ADD to constructor deps

    @Override
    public Page<PublicUnitResponse> getVacantUnits(String keyword, Pageable pageable) {
        Page<Unit> units = (keyword == null || keyword.isBlank())
                ? unitRepository.findByOccupancyStatus(UnitOccupancyStatus.VACANT, pageable)
                : unitRepository.searchPublic(keyword, UnitOccupancyStatus.VACANT, pageable);

        return attachImages(units);
    }

    @Override
    public Page<PublicUnitResponse> getVacantUnitsByProperty(UUID propertyId, Pageable pageable) {
        Page<Unit> units = unitRepository.findByPropertyIdAndOccupancyStatus(
                propertyId,
                UnitOccupancyStatus.VACANT,
                pageable
        );

        return attachImages(units);
    }

    @Override
    public PublicUnitResponse getVacantUnitById(UUID unitId) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Unit not found", ErrorCode.UNIT_NOT_FOUND)
                );

        if (unit.getOccupancyStatus() != UnitOccupancyStatus.VACANT) {
            throw new ResourceNotFoundException("Unit not found", ErrorCode.UNIT_NOT_FOUND);
        }

        PublicUnitResponse response = unitMapper.toPublicResponse(unit);

        List<String> images = unitMediaRepository
                .findAllByUnitId(unitId)
                .stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(UnitMedia::getUrl)
                .toList();

        response.setImages(images);
        return response;
    }

    // shared helper: batch-fetch media for a page, avoid N+1
    private Page<PublicUnitResponse> attachImages(Page<Unit> units) {
        List<UUID> unitIds = units.getContent().stream()
                .map(Unit::getId)
                .toList();

        Map<UUID, List<String>> imagesByUnitId = unitMediaRepository
                .findAllByUnitIdIn(unitIds)
                .stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .collect(Collectors.groupingBy(
                        UnitMedia::getUnitId,
                        Collectors.mapping(UnitMedia::getUrl, Collectors.toList())
                ));

        return units.map(unit -> {
            PublicUnitResponse response = unitMapper.toPublicResponse(unit);
            response.setImages(imagesByUnitId.getOrDefault(unit.getId(), List.of()));
            return response;
        });
    }




    @Override
    public PublicUnitResponse getLongestVacantUnit() {
        Unit unit = unitRepository.findLongestVacant()
                .orElseThrow(() ->
                        new ResourceNotFoundException("No vacant units available", ErrorCode.UNIT_NOT_FOUND)
                );

        PublicUnitResponse response = unitMapper.toPublicResponse(unit);

        propertyRepository.findById(unit.getPropertyId()).ifPresent(property -> {
            response.setPropertyName(property.getName());




            if (property.getAddress() != null) {
                String city = property.getAddress().getCity();
                String country = property.getAddress().getCountry();

                String area = (city != null && !city.isBlank()) ? city : null;
                if (country != null && !country.isBlank()) {
                    area = (area != null) ? area + ", " + country : country;
                }
                response.setPropertyArea(area);
            }
        });

        List<String> images = unitMediaRepository
                .findAllByUnitId(unit.getId())
                .stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(UnitMedia::getUrl)
                .toList();
        response.setImages(images);

        return response;
    }



}