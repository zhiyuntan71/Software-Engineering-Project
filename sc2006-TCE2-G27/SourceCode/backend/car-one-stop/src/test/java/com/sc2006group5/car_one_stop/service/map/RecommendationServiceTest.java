package com.sc2006group5.car_one_stop.service.map;

import com.sc2006group5.car_one_stop.common.external.maps.RoutePlanResult;
import com.sc2006group5.car_one_stop.common.external.maps.RoutingClient;
import com.sc2006group5.car_one_stop.dto.map.RecommendationItemDto;
import com.sc2006group5.car_one_stop.entity.auth.User;
import com.sc2006group5.car_one_stop.entity.map.Facility;
import com.sc2006group5.car_one_stop.entity.map.UserPreference;
import com.sc2006group5.car_one_stop.enums.auth.UserRole;
import com.sc2006group5.car_one_stop.enums.map.FacilityType;
import com.sc2006group5.car_one_stop.enums.map.RecommendationPreference;
import com.sc2006group5.car_one_stop.repository.auth.UserRepository;
import com.sc2006group5.car_one_stop.repository.map.FacilityRepository;
import com.sc2006group5.car_one_stop.repository.map.UserPreferenceRepository;
import com.sc2006group5.car_one_stop.service.pricing.EffectiveCostCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {

    private static final double EPSILON = 1e-9;

    private FacilityRepository facilityRepository;
    private UserPreferenceRepository preferenceRepository;
    private UserRepository userRepository;
    private RoutingClient routingClient;
    private EffectiveCostCalculator effectiveCostCalculator;
    private RecommendationService recommendationService;
    private User user;
    private UserPreference preference;

    @BeforeEach
    void setUp() {
        facilityRepository = mock(FacilityRepository.class);
        preferenceRepository = mock(UserPreferenceRepository.class);
        userRepository = mock(UserRepository.class);
        routingClient = mock(RoutingClient.class);
        effectiveCostCalculator = mock(EffectiveCostCalculator.class);

        recommendationService = new RecommendationService(
                facilityRepository,
                preferenceRepository,
                userRepository,
                routingClient,
                effectiveCostCalculator
        );

        user = User.builder()
                .id(1L)
                .email("user@example.com")
                .username("user")
                .passwordHash("hash")
                .verified(true)
                .role(UserRole.USER)
                .build();

        preference = UserPreference.builder()
                .id(1L)
                .user(user)
                .weightDistance(0.5)
                .weightPrice(0.3)
                .weightAvailability(0.2)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(preferenceRepository.findByUser(user)).thenReturn(Optional.of(preference));
        when(effectiveCostCalculator.calculate(anyLong(), any(BigDecimal.class), any(LocalTime.class), anyInt())).thenReturn(BigDecimal.ONE);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of())
        );
    }

    @Test
    void adjustPriceWeight_reducesPriceHeavily_whenCostDeltaIsMinor() throws Exception {
        Object weights = newWeightSet(0.7, 0.2, 0.1);

        Object adjusted = invokeAdjustPriceWeight(weights, "0.40");

        assertWeights(adjusted, 0.35, 0.43333333333333335, 0.21666666666666667);
    }

    @Test
    void adjustEtaWeight_reducesEtaSlightly_whenEtaDeltaIsModerate() throws Exception {
        Object weights = newWeightSet(0.2, 0.7, 0.1);

        Object adjusted = invokeAdjustEtaWeight(weights, 5.0);

        assertWeights(adjusted, 0.29333333333333333, 0.5599999999999999, 0.14666666666666672);
    }

    @Test
    void adjustWeights_usesPriceThenEta_forCustomModeWhenPriceWeightIsHigher() throws Exception {
        Object weights = newWeightSet(0.6, 0.3, 0.1);

        Object adjusted = invokeAdjustWeights(weights, RecommendationPreference.CUSTOM, "0.30", 3.0);

        assertWeights(adjusted, 0.3663157894736842, 0.42000000000000004, 0.2136842105263158);
    }

    @Test
    void adjustWeights_usesEtaThenPrice_forCustomModeWhenEtaWeightIsHigher() throws Exception {
        Object weights = newWeightSet(0.2, 0.7, 0.1);

        Object adjusted = invokeAdjustWeights(weights, RecommendationPreference.CUSTOM, "0.30", 3.0);

        assertWeights(adjusted, 0.14666666666666667, 0.6762264150943396, 0.1771069182389937);
    }

    @Test
    void recommend_treatsAnyDurationAboveFourAsFourHours() {
        Facility carparkA = facility(1L, "Alpha", 1.3000, 103.8000, "1.00", 20);
        Facility carparkB = facility(2L, "Beta", 1.3005, 103.8005, "1.15", 20);

        when(facilityRepository.findInBox(eq(FacilityType.CARPARK), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(carparkA, carparkB));

        when(routingClient.plan(anyDouble(), anyDouble(), eq(carparkA.getLatitude()), eq(carparkA.getLongitude())))
                .thenReturn(Optional.of(routePlan(600)));
        when(routingClient.plan(anyDouble(), anyDouble(), eq(carparkB.getLatitude()), eq(carparkB.getLongitude())))
                .thenReturn(Optional.of(routePlan(540)));

        preference.setParkingDurationHours(4);
        List<RecommendationItemDto> durationFour = recommendationService.recommend(
                FacilityType.CARPARK,
                RecommendationPreference.CHEAPEST,
                1.3000,
                103.8000,
                2000,
                3
        );

        preference.setParkingDurationHours(7);
        List<RecommendationItemDto> durationSeven = recommendationService.recommend(
                FacilityType.CARPARK,
                RecommendationPreference.CHEAPEST,
                1.3000,
                103.8000,
                2000,
                3
        );

        assertEquals(extractIds(durationFour), extractIds(durationSeven));
        assertEquals(extractScores(durationFour), extractScores(durationSeven));
    }

    @Test
    void recommend_nearest_prioritizesLowerEta_whenOtherSignalsAreEqual() {
        Facility slowRoute = facility(1L, "Slow", 1.3000, 103.8000, "1.00", 20);
        Facility fastRoute = facility(2L, "Fast", 1.3005, 103.8005, "1.00", 20);

        when(facilityRepository.findInBox(eq(FacilityType.CARPARK), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(slowRoute, fastRoute));

        when(routingClient.plan(anyDouble(), anyDouble(), eq(slowRoute.getLatitude()), eq(slowRoute.getLongitude())))
                .thenReturn(Optional.of(routePlan(600)));
        when(routingClient.plan(anyDouble(), anyDouble(), eq(fastRoute.getLatitude()), eq(fastRoute.getLongitude())))
                .thenReturn(Optional.of(routePlan(120)));

        List<RecommendationItemDto> items = recommendationService.recommend(
                FacilityType.CARPARK,
                RecommendationPreference.NEAREST,
                1.3000,
                103.8000,
                2000,
                3
        );

        assertEquals(2L, items.getFirst().id());
    }

    private static Facility facility(Long id, String name, double lat, double lng, String hourlyRate, int availableLots) {
        return Facility.builder()
                .id(id)
                .type(FacilityType.CARPARK)
                .name(name)
                .address(name + " address")
                .latitude(lat)
                .longitude(lng)
                .hourlyRate(new BigDecimal(hourlyRate))
                .availableLots(availableLots)
                .totalLots(availableLots + 20)
                .sheltered(false)
                .openingTime(LocalTime.MIDNIGHT)
                .closingTime(LocalTime.MIDNIGHT)
                .build();
    }

    private static RoutePlanResult routePlan(int durationSeconds) {
        return RoutePlanResult.builder()
                .distanceMeters(1000)
                .durationSeconds(durationSeconds)
                .polyline("polyline")
                .build();
    }

    private static List<Long> extractIds(List<RecommendationItemDto> items) {
        return items.stream().map(RecommendationItemDto::id).toList();
    }

    private static List<Double> extractScores(List<RecommendationItemDto> items) {
        return items.stream().map(RecommendationItemDto::score).toList();
    }

    private static void assertWeights(Object weightSet, double expectedPrice, double expectedEta, double expectedAvailability) throws Exception {
        assertEquals(expectedPrice, readWeight(weightSet, "price"), EPSILON);
        assertEquals(expectedEta, readWeight(weightSet, "eta"), EPSILON);
        assertEquals(expectedAvailability, readWeight(weightSet, "availability"), EPSILON);
    }

    private static Object invokeAdjustPriceWeight(Object weightSet, String costDelta) throws Exception {
        Method method = RecommendationService.class.getDeclaredMethod(
                "adjustPriceWeight",
                weightSetClass(),
                BigDecimal.class
        );
        method.setAccessible(true);
        return method.invoke(null, weightSet, new BigDecimal(costDelta));
    }

    private static Object invokeAdjustEtaWeight(Object weightSet, double etaDeltaMinutes) throws Exception {
        Method method = RecommendationService.class.getDeclaredMethod(
                "adjustEtaWeight",
                weightSetClass(),
                double.class
        );
        method.setAccessible(true);
        return method.invoke(null, weightSet, etaDeltaMinutes);
    }

    private static Object invokeAdjustWeights(
            Object weightSet,
            RecommendationPreference preference,
            String costDelta,
            double etaDeltaMinutes
    ) throws Exception {
        Method method = RecommendationService.class.getDeclaredMethod(
                "adjustWeights",
                weightSetClass(),
                RecommendationPreference.class,
                BigDecimal.class,
                double.class
        );
        method.setAccessible(true);
        return method.invoke(null, weightSet, preference, new BigDecimal(costDelta), etaDeltaMinutes);
    }

    private static Object newWeightSet(double price, double eta, double availability) throws Exception {
        Constructor<?> constructor = weightSetClass().getDeclaredConstructor(double.class, double.class, double.class);
        constructor.setAccessible(true);
        return constructor.newInstance(price, eta, availability);
    }

    private static double readWeight(Object weightSet, String accessor) throws Exception {
        Method method = weightSetClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return (double) method.invoke(weightSet);
    }

    private static Class<?> weightSetClass() throws ClassNotFoundException {
        return Class.forName(RecommendationService.class.getName() + "$WeightSet");
    }
}
