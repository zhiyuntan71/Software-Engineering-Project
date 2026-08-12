package com.sc2006group5.car_one_stop.model.pricing;

import java.util.List;

public class CarParkPricingProfile {

    private final Long facilityId;
    private final List<PricingBand> weekdayBands;
    private final List<PricingBand> saturdayBands;
    private final List<PricingBand> sundayPhBands;

    public CarParkPricingProfile(
            Long facilityId,
            List<PricingBand> weekdayBands,
            List<PricingBand> saturdayBands,
            List<PricingBand> sundayPhBands
    ) {
        this.facilityId = facilityId;
        this.weekdayBands = weekdayBands;
        this.saturdayBands = saturdayBands;
        this.sundayPhBands = sundayPhBands;
    }

    public Long getFacilityId() {
        return facilityId;
    }

    public List<PricingBand> getWeekdayBands() {
        return weekdayBands;
    }

    public List<PricingBand> getSaturdayBands() {
        return saturdayBands;
    }

    public List<PricingBand> getSundayPhBands() {
        return sundayPhBands;
    }

    public List<PricingBand> bandsFor(DayType dayType) {
        if (dayType == null) {
            return weekdayBands;
        }
        return switch (dayType) {
            case WEEKDAY -> weekdayBands;
            case SATURDAY -> saturdayBands;
            case SUNDAY_PH -> sundayPhBands;
        };
    }
}
