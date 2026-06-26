package com.rentmanager.modules.unit.application.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public class PublicUnitResponse {
    private UUID id;
    private UUID propertyId;
    private String unitNumber;
    private String description;
    private BigDecimal rentAmount;
    private String occupancyStatus; // always "VACANT" for this endpoint, but kept for clarity

    public PublicUnitResponse() {}

    // getters/setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPropertyId() { return propertyId; }
    public void setPropertyId(UUID propertyId) { this.propertyId = propertyId; }

    public String getUnitNumber() { return unitNumber; }
    public void setUnitNumber(String unitNumber) { this.unitNumber = unitNumber; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getRentAmount() { return rentAmount; }
    public void setRentAmount(BigDecimal rentAmount) { this.rentAmount = rentAmount; }

    public String getOccupancyStatus() { return occupancyStatus; }
    public void setOccupancyStatus(String occupancyStatus) { this.occupancyStatus = occupancyStatus; }


}