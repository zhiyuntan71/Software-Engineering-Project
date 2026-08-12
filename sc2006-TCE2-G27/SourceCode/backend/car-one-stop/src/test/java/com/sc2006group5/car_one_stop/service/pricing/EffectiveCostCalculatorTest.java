package com.sc2006group5.car_one_stop.service.pricing;

import com.sc2006group5.car_one_stop.entity.map.Facility;
import com.sc2006group5.car_one_stop.model.pricing.CarParkPricingProfile;
import com.sc2006group5.car_one_stop.model.pricing.PricingBand;
import com.sc2006group5.car_one_stop.repository.map.FacilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EffectiveCostCalculatorTest {

    private static final Long FACILITY_ID = 100L;

    private PricingProfileStore pricingProfileStore;
    private FacilityRepository facilityRepository;
    private EffectiveCostCalculator calculator;

    @BeforeEach
    void setUp() {
        pricingProfileStore = new PricingProfileStore();
        facilityRepository = mock(FacilityRepository.class);
        calculator = new EffectiveCostCalculator(pricingProfileStore, facilityRepository);

        when(facilityRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0, Long.class);
            return Optional.of(Facility.builder().id(id).hourlyRate(new BigDecimal("2.00")).build());
        });
    }

    @Test
    void calculate_singleBandCoversFullSession() {
        putProfile(FACILITY_ID, List.of(
                new PricingBand(LocalTime.of(9, 0), LocalTime.of(17, 0), new BigDecimal("2.00"), false, false)
        ));

        BigDecimal result = calculator.calculate(FACILITY_ID, LocalTime.of(14, 0), 60);

        assertEquals(new BigDecimal("2.00"), result);
    }

    @Test
    void calculate_sessionSpansTwoBands() {
        putProfile(FACILITY_ID, List.of(
                new PricingBand(LocalTime.of(13, 0), LocalTime.of(15, 0), new BigDecimal("1.00"), false, false),
                PricingBand.free(LocalTime.of(15, 0), LocalTime.of(17, 0))
        ));

        BigDecimal result = calculator.calculate(FACILITY_ID, LocalTime.of(14, 0), 180);

        assertEquals(new BigDecimal("1.00"), result);
    }

    @Test
    void calculate_sessionSpansThreeBands() {
        putProfile(FACILITY_ID, List.of(
                new PricingBand(LocalTime.of(7, 0), LocalTime.of(10, 0), new BigDecimal("0.60"), false, false),
                PricingBand.free(LocalTime.of(10, 0), LocalTime.of(11, 0)),
                new PricingBand(LocalTime.of(11, 0), LocalTime.of(17, 0), new BigDecimal("2.40"), false, false)
        ));

        BigDecimal result = calculator.calculate(FACILITY_ID, LocalTime.of(9, 0), 180);

        assertEquals(new BigDecimal("3.00"), result);
    }

    @Test
    void calculate_overnightSession() {
        putProfile(FACILITY_ID, List.of(
                new PricingBand(LocalTime.of(22, 0), LocalTime.of(7, 0), new BigDecimal("1.00"), true, false)
        ));

        BigDecimal result = calculator.calculate(FACILITY_ID, LocalTime.of(22, 0), 120);

        assertEquals(new BigDecimal("2.00"), result);
    }

    @Test
    void calculate_flatRateBandAppliedOnce() {
        putProfile(FACILITY_ID, List.of(
                new PricingBand(LocalTime.of(22, 30), LocalTime.of(7, 0), new BigDecimal("5.00"), true, true)
        ));

        BigDecimal result = calculator.calculate(FACILITY_ID, LocalTime.of(22, 30), 60);

        assertEquals(new BigDecimal("5.00"), result);
    }

    @Test
    void calculate_noProfileFallsBackToHourlyRate() {
        BigDecimal result = calculator.calculate(999L, LocalTime.of(10, 0), 90);

        assertEquals(new BigDecimal("3.00"), result);
    }

    @Test
    void calculate_freeParkingReturnsZero() {
        putProfile(FACILITY_ID, List.of(
                PricingBand.free(LocalTime.MIDNIGHT, LocalTime.of(23, 59))
        ));

        BigDecimal result = calculator.calculate(FACILITY_ID, LocalTime.of(10, 0), 60);

        assertEquals(new BigDecimal("0.00"), result);
    }

    private void putProfile(Long facilityId, List<PricingBand> bands) {
        CarParkPricingProfile profile = new CarParkPricingProfile(facilityId, bands, bands, bands);
        pricingProfileStore.put(facilityId, profile);
    }
}
