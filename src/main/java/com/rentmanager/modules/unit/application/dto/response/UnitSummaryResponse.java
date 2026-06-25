package com.rentmanager.modules.unit.application.dto.response;

public class UnitSummaryResponse {
    private long totalUnits;
    private long vacantUnits;
    private long occupiedUnits;
    private long reservedUnits;

    public UnitSummaryResponse() {}

    public UnitSummaryResponse(long totalUnits, long vacantUnits, long occupiedUnits, long reservedUnits) {
        this.totalUnits = totalUnits;
        this.vacantUnits = vacantUnits;
        this.occupiedUnits = occupiedUnits;
        this.reservedUnits = reservedUnits;
    }

    public long getTotalUnits() { return totalUnits; }
    public void setTotalUnits(long totalUnits) { this.totalUnits = totalUnits; }

    public long getVacantUnits() { return vacantUnits; }
    public void setVacantUnits(long vacantUnits) { this.vacantUnits = vacantUnits; }

    public long getOccupiedUnits() { return occupiedUnits; }
    public void setOccupiedUnits(long occupiedUnits) { this.occupiedUnits = occupiedUnits; }

    public long getReservedUnits() { return reservedUnits; }
    public void setReservedUnits(long reservedUnits) { this.reservedUnits = reservedUnits; }
}