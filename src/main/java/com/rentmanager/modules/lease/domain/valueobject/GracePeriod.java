package com.rentmanager.modules.lease.domain.valueobject;

public class GracePeriod {

    private final int days;

    public GracePeriod(Integer days) {

        if (days == null) {
            this.days = 0;
            return;
        }

        if (days < 0 || days > 60) {
            throw new IllegalArgumentException("Grace period must be between 0 and 60 days");
        }

        this.days = days;
    }

    public int getDays() {
        return days;
    }

    public boolean isWithinGrace(int overdueDays) {
        return overdueDays <= days;
    }
}