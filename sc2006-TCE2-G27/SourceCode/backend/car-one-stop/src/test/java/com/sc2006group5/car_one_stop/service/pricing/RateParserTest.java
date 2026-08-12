package com.sc2006group5.car_one_stop.service.pricing;

import com.sc2006group5.car_one_stop.model.LtaRateRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RateParserTest {

    private final RateParser rateParser = new RateParser();

    @Test
    void computeWeekdayHourlyRate_parsesThirtyMinuteRate() {
        LtaRateRecord record = recordWithWeekdayRates("7am-5pm: $1.20/30 min", null);

        BigDecimal result = rateParser.computeWeekdayHourlyRate(record);

        assertEquals(new BigDecimal("2.40"), result);
    }

    @Test
    void computeWeekdayHourlyRate_parsesFreeParking() {
        LtaRateRecord record = recordWithWeekdayRates("Free parking", null);

        BigDecimal result = rateParser.computeWeekdayHourlyRate(record);

        assertEquals(new BigDecimal("0.00"), result);
    }

    @Test
    void computeWeekdayHourlyRate_mapsPerEntryToEquivalentHourlyRate() {
        LtaRateRecord record = recordWithWeekdayRates("$0.60 per entry", null);

        BigDecimal result = rateParser.computeWeekdayHourlyRate(record);

        assertEquals(new BigDecimal("2.00"), result);
    }

    @Test
    void computeWeekdayHourlyRate_mapsFlatRateToEquivalentHourlyRate() {
        LtaRateRecord record = recordWithWeekdayRates("10.30pm-7am: Flat $5.00", null);

        BigDecimal result = rateParser.computeWeekdayHourlyRate(record);

        assertEquals(new BigDecimal("1.00"), result);
    }

    @Test
    void computeWeekdayHourlyRate_returnsNullForNullRecord() {
        BigDecimal result = rateParser.computeWeekdayHourlyRate(null);

        assertNull(result);
    }

    private LtaRateRecord recordWithWeekdayRates(String weekdaysRate1, String weekdaysRate2) {
        return new LtaRateRecord(
                "Ang Mo Kio Ave 4",
                weekdaysRate1,
                weekdaysRate2,
                null,
                null,
                null,
                "Surface Car Park"
        );
    }
}
