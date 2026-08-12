package com.sc2006group5.car_one_stop.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LtaRateRecord {

    private final String carPark;
    private final String weekdaysRate1;
    private final String weekdaysRate2;
    private final String saturdayRate;
    private final String sunPhRate;
    private final String perEntry;
    private final String category;

    @JsonCreator
    public LtaRateRecord(
            @JsonProperty("carpark") String carPark,
            @JsonProperty("weekdays_rate_1") String weekdaysRate1,
            @JsonProperty("weekdays_rate_2") String weekdaysRate2,
            @JsonProperty("saturday_rate") String saturdayRate,
            @JsonProperty("sunday_publicholiday_rate") String sunPhRate,
            @JsonProperty("per_entry") String perEntry,
            @JsonProperty("category") String category
    ) {
        this.carPark = carPark;
        this.weekdaysRate1 = weekdaysRate1;
        this.weekdaysRate2 = weekdaysRate2;
        this.saturdayRate = saturdayRate;
        this.sunPhRate = sunPhRate;
        this.perEntry = perEntry;
        this.category = category;
    }

    public String getCarPark() {
        return carPark;
    }

    public String getWeekdaysRate1() {
        return weekdaysRate1;
    }

    public String getWeekdaysRate2() {
        return weekdaysRate2;
    }

    public String getSaturdayRate() {
        return saturdayRate;
    }

    public String getSunPhRate() {
        return sunPhRate;
    }

    public String getPerEntry() {
        return perEntry;
    }

    public String getCategory() {
        return category;
    }
}
