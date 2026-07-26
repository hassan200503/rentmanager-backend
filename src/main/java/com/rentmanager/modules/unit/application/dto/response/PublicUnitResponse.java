package com.rentmanager.modules.unit.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class PublicUnitResponse {
    private UUID id;
    private UUID propertyId;
    private String unitNumber;
    private String label;
    private String floor;
    private String description;
    private BigDecimal rentAmount;
    private BigDecimal depositAmount;
    private String occupancyStatus; // always "VACANT" for this endpoint, but kept for clarity

    public PublicUnitResponse() {}

    // getters/setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPropertyId() { return propertyId; }
    public void setPropertyId(UUID propertyId) { this.propertyId = propertyId; }

    public String getUnitNumber() { return unitNumber; }
    public void setUnitNumber(String unitNumber) { this.unitNumber = unitNumber; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getRentAmount() { return rentAmount; }
    public void setRentAmount(BigDecimal rentAmount) { this.rentAmount = rentAmount; }

    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }

    public String getOccupancyStatus() { return occupancyStatus; }
    public void setOccupancyStatus(String occupancyStatus) { this.occupancyStatus = occupancyStatus; }



    private List<String> images = List.of();

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }



    private String propertyName;
    private String propertyArea;
    private LocalDateTime vacatedAt;
    private boolean landlordVerified;

    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }

    public String getPropertyArea() { return propertyArea; }
    public void setPropertyArea(String propertyArea) { this.propertyArea = propertyArea; }

    public LocalDateTime getVacatedAt() { return vacatedAt; }
    public void setVacatedAt(LocalDateTime vacatedAt) { this.vacatedAt = vacatedAt; }

    public boolean isLandlordVerified() { return landlordVerified; }
    public void setLandlordVerified(boolean landlordVerified) { this.landlordVerified = landlordVerified; }

}