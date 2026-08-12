package com.sc2006group5.car_one_stop.common.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Svy21ConverterTest {

    @Test
    void originMapsToKnownWgs84FundamentalPoint() {
        double[] latLon = Svy21Converter.fromEastingNorthing(28001.642, 38744.572);
        assertEquals(1.366666, latLon[0], 1e-6);
        assertEquals(103.833333, latLon[1], 1e-6);
    }

    @Test
    void jurongWestCarparkJ79MMapsToExpectedLocation() {
        // data.gov.sg HDB record (car_park_no=J79M):
        // x_coord (easting)=12599.8443, y_coord (northing)=36515.2018
        double[] latLon = Svy21Converter.fromEastingNorthing(12599.8443, 36515.2018);

        // Expected from trusted SVY21 conversion implementation (svy21 package / OneMap parity).
        assertEquals(1.3465004, latLon[0], 1e-6);
        assertEquals(103.6949384, latLon[1], 1e-6);
    }
}

