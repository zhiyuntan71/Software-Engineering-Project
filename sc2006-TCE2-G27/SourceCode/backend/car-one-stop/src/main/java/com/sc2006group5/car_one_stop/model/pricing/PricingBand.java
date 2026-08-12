package com.sc2006group5.car_one_stop.model.pricing;

import java.math.BigDecimal;
import java.time.LocalTime;

public class PricingBand {

    private final LocalTime start;
    private final LocalTime end;
    private final BigDecimal ratePerHour;
    private final boolean overnight;
    private final boolean flatRate;

    public PricingBand(LocalTime start, LocalTime end, BigDecimal ratePerHour, boolean overnight, boolean flatRate) {
        this.start = start;
        this.end = end;
        this.ratePerHour = ratePerHour;
        this.overnight = overnight;
        this.flatRate = flatRate;
    }

    public LocalTime getStart() {
        return start;
    }

    public LocalTime getEnd() {
        return end;
    }

    public BigDecimal getRatePerHour() {
        return ratePerHour;
    }

    public boolean isOvernight() {
        return overnight;
    }

    public boolean isFlatRate() {
        return flatRate;
    }

    public static PricingBand free(LocalTime start, LocalTime end) {
        return new PricingBand(start, end, BigDecimal.ZERO, start != null && end != null && start.isAfter(end), false);
    }

    public static PricingBand unknown() {
        return new PricingBand(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, null, false, false);
    }

    public boolean isUnknown() {
        return ratePerHour == null;
    }
}
