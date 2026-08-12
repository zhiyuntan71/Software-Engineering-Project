package com.sc2006group5.car_one_stop.service.pricing;

import com.sc2006group5.car_one_stop.model.pricing.PricingBand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingBandParserTest {

    private final PricingBandParser parser = new PricingBandParser();

    @Test
    void parse_thirtyMinuteRateBand() {
        List<PricingBand> bands = parser.parse("7am-5pm: $1.20/30 min");

        assertEquals(1, bands.size());
        PricingBand band = bands.getFirst();
        assertEquals(LocalTime.of(7, 0), band.getStart());
        assertEquals(LocalTime.of(17, 0), band.getEnd());
        assertEquals(new BigDecimal("2.40"), band.getRatePerHour());
        assertFalse(band.isOvernight());
        assertFalse(band.isFlatRate());
    }

    @Test
    void parse_thirtyMinuteRateBandWithHalfHourTime() {
        List<PricingBand> bands = parser.parse("5pm-10.30pm: $0.60/30 min");

        assertEquals(1, bands.size());
        PricingBand band = bands.getFirst();
        assertEquals(LocalTime.of(17, 0), band.getStart());
        assertEquals(LocalTime.of(22, 30), band.getEnd());
        assertEquals(new BigDecimal("1.20"), band.getRatePerHour());
    }

    @Test
    void parse_flatOvernightBand() {
        List<PricingBand> bands = parser.parse("10.30pm-7am: Flat $5.00");

        assertEquals(1, bands.size());
        PricingBand band = bands.getFirst();
        assertEquals(LocalTime.of(22, 30), band.getStart());
        assertEquals(LocalTime.of(7, 0), band.getEnd());
        assertEquals(new BigDecimal("5.00"), band.getRatePerHour());
        assertTrue(band.isOvernight());
        assertTrue(band.isFlatRate());
    }

    @Test
    void parse_freeParkingFullDay() {
        List<PricingBand> bands = parser.parse("Free parking");

        assertEquals(1, bands.size());
        PricingBand band = bands.getFirst();
        assertEquals(LocalTime.MIDNIGHT, band.getStart());
        assertEquals(LocalTime.of(23, 59), band.getEnd());
        assertEquals(new BigDecimal("0"), band.getRatePerHour().stripTrailingZeros());
        assertFalse(band.isFlatRate());
    }

    @Test
    void parse_perEntryMappedToZeroPerHour() {
        List<PricingBand> bands = parser.parse("$0.60 per entry");

        assertEquals(1, bands.size());
        PricingBand band = bands.getFirst();
        assertEquals(LocalTime.MIDNIGHT, band.getStart());
        assertEquals(LocalTime.of(23, 59), band.getEnd());
        assertEquals(new BigDecimal("0"), band.getRatePerHour().stripTrailingZeros());
        assertFalse(band.isFlatRate());
    }

    @Test
    void parse_multipleBands() {
        List<PricingBand> bands = parser.parse("7am-10pm: $1.20/30 min; 10pm-7am: Free");

        assertEquals(2, bands.size());
        PricingBand dayBand = bands.get(0);
        PricingBand nightBand = bands.get(1);

        assertEquals(LocalTime.of(7, 0), dayBand.getStart());
        assertEquals(LocalTime.of(22, 0), dayBand.getEnd());
        assertEquals(new BigDecimal("2.40"), dayBand.getRatePerHour());

        assertEquals(LocalTime.of(22, 0), nightBand.getStart());
        assertEquals(LocalTime.of(7, 0), nightBand.getEnd());
        assertEquals(new BigDecimal("0"), nightBand.getRatePerHour().stripTrailingZeros());
        assertTrue(nightBand.isOvernight());
    }

    @Test
    void parse_nullInputReturnsUnknown() {
        List<PricingBand> bands = parser.parse(null);

        assertEquals(1, bands.size());
        assertTrue(bands.getFirst().isUnknown());
    }

    @Test
    void parse_emptyInputReturnsUnknown() {
        List<PricingBand> bands = parser.parse("");

        assertEquals(1, bands.size());
        assertTrue(bands.getFirst().isUnknown());
    }
}
