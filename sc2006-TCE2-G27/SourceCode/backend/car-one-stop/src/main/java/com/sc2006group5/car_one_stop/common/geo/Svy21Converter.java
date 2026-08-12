package com.sc2006group5.car_one_stop.common.geo;

/**
 * SVY21 (EPSG:3414) to WGS84 converter.
 *
 * Input:
 *   - easting  = x_coord
 *   - northing = y_coord
 *
 * Notes:
 *   - EPSG:3414 uses Northing, Easting axis order.
 *   - This method accepts x/y in dataset order and converts internally.
 */
public final class Svy21Converter {

    private static final double A = 6378137.0;
    private static final double F = 1.0 / 298.257223563;

    private static final double O_LAT = 1.36666666666667;
    private static final double O_LON = 103.83333333333333;
    private static final double O_N = 38744.572;
    private static final double O_E = 28001.642;
    private static final double K = 1.0;

    private static final double B = A * (1.0 - F);
    private static final double E2 = (2.0 * F) - (F * F);
    private static final double E4 = E2 * E2;
    private static final double E6 = E4 * E2;

    private static final double A0 = 1.0 - (E2 / 4.0) - (3.0 * E4 / 64.0) - (5.0 * E6 / 256.0);
    private static final double A2 = (3.0 / 8.0) * (E2 + (E4 / 4.0) + (15.0 * E6 / 128.0));
    private static final double A4 = (15.0 / 256.0) * (E4 + (3.0 * E6 / 4.0));
    private static final double A6 = 35.0 * E6 / 3072.0;

    private Svy21Converter() {}

    /**
     * Converts SVY21 easting/northing to WGS84 latitude/longitude.
     *
     * @param easting x_coord from HDB/URA dataset
     * @param northing y_coord from HDB/URA dataset
     * @return {latitude, longitude}
     */
    public static double[] fromEastingNorthing(double easting, double northing) {
        return computeLatLon(northing, easting);
    }

    /**
     * Explicit variant if you want to avoid axis-order confusion.
     *
     * @param northing y_coord from dataset
     * @param easting x_coord from dataset
     * @return {latitude, longitude}
     */
    public static double[] fromNorthingEasting(double northing, double easting) {
        return computeLatLon(northing, easting);
    }

    private static double[] computeLatLon(double northing, double easting) {
        double mPrime = calcM(O_LAT) + ((northing - O_N) / K);

        double n = (A - B) / (A + B);
        double n2 = n * n;
        double n3 = n2 * n;
        double n4 = n2 * n2;

        double g = A * (1.0 - n) * (1.0 - n2) * (1.0 + (9.0 * n2 / 4.0) + (225.0 * n4 / 64.0))
                * (Math.PI / 180.0);

        double sigma = (mPrime * Math.PI) / (180.0 * g);

        double latPrime = sigma
                + ((3.0 * n / 2.0) - (27.0 * n3 / 32.0)) * Math.sin(2.0 * sigma)
                + ((21.0 * n2 / 16.0) - (55.0 * n4 / 32.0)) * Math.sin(4.0 * sigma)
                + (151.0 * n3 / 96.0) * Math.sin(6.0 * sigma)
                + (1097.0 * n4 / 512.0) * Math.sin(8.0 * sigma);

        double sinLatPrime = Math.sin(latPrime);
        double sin2LatPrime = sinLatPrime * sinLatPrime;

        double rhoPrime = calcRho(sin2LatPrime);
        double vPrime = calcV(sin2LatPrime);

        double psiPrime = vPrime / rhoPrime;
        double psiPrime2 = psiPrime * psiPrime;
        double psiPrime3 = psiPrime2 * psiPrime;
        double psiPrime4 = psiPrime3 * psiPrime;

        double tPrime = Math.tan(latPrime);
        double tPrime2 = tPrime * tPrime;
        double tPrime4 = tPrime2 * tPrime2;
        double tPrime6 = tPrime4 * tPrime2;

        double ePrime = easting - O_E;

        double x = ePrime / (K * vPrime);
        double x2 = x * x;
        double x3 = x2 * x;
        double x5 = x3 * x2;
        double x7 = x5 * x2;

        double latFactor = tPrime / (K * rhoPrime);
        double latTerm1 = latFactor * ((ePrime * x) / 2.0);
        double latTerm2 = latFactor * ((ePrime * x3) / 24.0)
                * ((-4.0 * psiPrime2) + (9.0 * psiPrime * (1.0 - tPrime2)) + (12.0 * tPrime2));
        double latTerm3 = latFactor * ((ePrime * x5) / 720.0)
                * ((8.0 * psiPrime4) * (11.0 - 24.0 * tPrime2)
                - (12.0 * psiPrime3) * (21.0 - 71.0 * tPrime2)
                + (15.0 * psiPrime2) * (15.0 - 98.0 * tPrime2 + 15.0 * tPrime4)
                + (180.0 * psiPrime) * (5.0 * tPrime2 - 3.0 * tPrime4)
                + 360.0 * tPrime4);
        double latTerm4 = latFactor * ((ePrime * x7) / 40320.0)
                * (1385.0 - 3633.0 * tPrime2 + 4095.0 * tPrime4 + 1575.0 * tPrime6);

        double lat = latPrime - latTerm1 + latTerm2 - latTerm3 + latTerm4;

        double secLat = 1.0 / Math.cos(lat);

        double lonTerm1 = x * secLat;
        double lonTerm2 = ((x3 * secLat) / 6.0) * (psiPrime + (2.0 * tPrime2));
        double lonTerm3 = ((x5 * secLat) / 120.0)
                * ((-4.0 * psiPrime3) * (1.0 - 6.0 * tPrime2)
                + (psiPrime2) * (9.0 - 68.0 * tPrime2)
                + (72.0 * psiPrime * tPrime2)
                + (24.0 * tPrime4));
        double lonTerm4 = ((x7 * secLat) / 5040.0)
                * (61.0 + 662.0 * tPrime2 + 1320.0 * tPrime4 + 720.0 * tPrime6);

        double lon = Math.toRadians(O_LON) + lonTerm1 - lonTerm2 + lonTerm3 - lonTerm4;

        return new double[] { Math.toDegrees(lat), Math.toDegrees(lon) };
    }

    private static double calcM(double latDeg) {
        double latR = Math.toRadians(latDeg);
        return A * ((A0 * latR) - (A2 * Math.sin(2.0 * latR)) + (A4 * Math.sin(4.0 * latR)) - (A6 * Math.sin(6.0 * latR)));
    }

    private static double calcRho(double sin2Lat) {
        return A * (1.0 - E2) / Math.pow(1.0 - E2 * sin2Lat, 1.5);
    }

    private static double calcV(double sin2Lat) {
        return A / Math.sqrt(1.0 - E2 * sin2Lat);
    }
}