package com.sc2006group5.car_one_stop.service.map;

import com.sc2006group5.car_one_stop.common.external.lta.LtaClient;
import com.sc2006group5.car_one_stop.common.external.maps.RoutePlanResult;
import com.sc2006group5.car_one_stop.common.external.maps.RoutingClient;
import com.sc2006group5.car_one_stop.dto.map.EVChargerDto;
import com.sc2006group5.car_one_stop.dto.map.EVRecommendationItemDto;
import com.sc2006group5.car_one_stop.entity.auth.User;
import com.sc2006group5.car_one_stop.entity.map.Facility;
import com.sc2006group5.car_one_stop.entity.map.UserPreference;
import com.sc2006group5.car_one_stop.enums.auth.ChargingType;
import com.sc2006group5.car_one_stop.enums.map.FacilityType;
import com.sc2006group5.car_one_stop.enums.map.RecommendationPreference;
import com.sc2006group5.car_one_stop.repository.auth.UserRepository;
import com.sc2006group5.car_one_stop.repository.booking.BookingRepository;
import com.sc2006group5.car_one_stop.repository.map.FacilityRepository;
import com.sc2006group5.car_one_stop.repository.map.UserPreferenceRepository;
import com.sc2006group5.car_one_stop.service.pricing.EffectiveCostCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Slf4j
@Service
@RequiredArgsConstructor
public class EVRecommendationService {

    private static final int DEFAULT_DURATION_HOURS = 1;
    private static final int MIN_DURATION_HOURS = 1;
    private static final int MAX_DURATION_HOURS = 4;
    private static final double CARPARK_SEARCH_RADIUS_METERS = 150.0;

    private final LtaClient ltaClient;
    private final com.sc2006group5.car_one_stop.common.external.ocm.OcmClient ocmClient;
    private final FacilityRepository facilityRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final RoutingClient routingClient;
    private final EffectiveCostCalculator effectiveCostCalculator;
    private final BookingRepository bookingRepository;

    private static final double DEDUP_RADIUS_METERS = 50.0;

    // ── Public API ──────────────────────────────────────────────────────────

    public List<EVRecommendationItemDto> recommendEVChargers(
            RecommendationPreference preference,
            double lat,
            double lng,
            int radiusMeters
    ) {
        User user = getAuthenticatedUser();
        UserPreference pref = getOrCreatePreferences(user);
        int durationHours = normalizeDurationHours(pref.getEvParkingDurationHours());
        int effectiveRadius = Math.min(Math.max(radiusMeters, 100), 2000);

        ChargingType chargingType = user.getChargingType();
        boolean hasChargingType = chargingType != null && chargingType != ChargingType.NOT_APPLICABLE;

        Set<String> occupiedIds = new HashSet<>(bookingRepository.findOccupiedSlotIds());

        // Merge LTA (live) + OCM (static), deduplicate OCM within 50m of any LTA station
        List<EVChargerDto> ltaAll = ltaClient.fetchAllEVChargers();
        List<EVChargerDto> ltaNearby = ltaAll.stream()
                .filter(ev -> ev.getLatitude() != 0 && ev.getLongitude() != 0)
                .filter(ev -> haversineMeters(lat, lng, ev.getLatitude(), ev.getLongitude()) <= effectiveRadius)
                .toList();
        List<EVChargerDto> ocmNearby = ocmClient.fetchAllEVChargers().stream()
                .filter(ev -> haversineMeters(lat, lng, ev.getLatitude(), ev.getLongitude()) <= effectiveRadius)
                .filter(ev -> ltaNearby.stream().noneMatch(lta ->
                        haversineMeters(ev.getLatitude(), ev.getLongitude(), lta.getLatitude(), lta.getLongitude()) <= DEDUP_RADIUS_METERS))
                .toList();

        List<EVChargerDto> allChargers = new java.util.ArrayList<>(ltaNearby.size() + ocmNearby.size());
        allChargers.addAll(ltaNearby);
        allChargers.addAll(ocmNearby);

        List<EVChargerDto> nearby = allChargers.stream()
                .filter(ev -> ev.getLatitude() != 0 && ev.getLongitude() != 0)
                .toList();

        List<EVChargerDto> compatible = hasChargingType
                ? nearby.stream().filter(ev -> hasCompatiblePlug(ev, chargingType)).toList()
                : nearby;

        if (compatible.isEmpty()) {
            log.info("EV recommendation: no compatible chargers found within {}m", effectiveRadius);
            return List.of();
        }

        log.info("EV recommendation: {} stations in radius, {} after compatibility filter, preference={}, duration={}h",
                nearby.size(), compatible.size(), preference, durationHours);

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        List<EVCandidate> candidates;
        try {
            int finalDuration = durationHours;
            List<CompletableFuture<Optional<EVCandidate>>> futures = compatible.stream()
                    .map(ev -> CompletableFuture.supplyAsync(
                            () -> toCandidate(ev, lat, lng, finalDuration, chargingType, hasChargingType, occupiedIds), pool))
                    .toList();
            candidates = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(Optional::stream)
                    .toList();
        } finally {
            pool.shutdown();
        }

        if (candidates.isEmpty()) {
            log.warn("EV recommendation: routing failed for all candidates, using fallback");
            List<EVRecommendationItemDto> fallbackResults = fallback(
                    compatible, lat, lng, preference, pref, durationHours, chargingType, hasChargingType, occupiedIds);
            logWinner(fallbackResults, preference, "fallback");
            return fallbackResults;
        }

        List<EVRecommendationItemDto> scoredResults = score(candidates, preference, pref);
        logWinner(scoredResults, preference, "scored");
        return scoredResults;
    }

    // ── Candidate building ──────────────────────────────────────────────────

    private Optional<EVCandidate> toCandidate(
            EVChargerDto ev,
            double originLat,
            double originLng,
            int durationHours,
            ChargingType chargingType,
            boolean hasChargingType,
            Set<String> occupiedIds
    ) {
        PlugInfo cheapest = findCheapestCompatiblePlug(ev, chargingType, hasChargingType);
        if (cheapest == null) return Optional.empty();

        int availablePoints = countAvailableCompatiblePoints(ev, chargingType, hasChargingType, occupiedIds);
        // Hard filter: LTA stations with 0 compatible available points are excluded (like carpark 0-lot filter).
        // OCM stations (availablePoints == -1) are unaffected — availability is unknown, not confirmed zero.
        if (ev.isHasLiveStatus() && availablePoints == 0) return Optional.empty();

        BigDecimal evCost = calculateEVCost(cheapest.pricePerKwh(), cheapest.powerRatingKw(), durationHours);

        BigDecimal carparkCost = BigDecimal.ZERO;
        String carparkName = null;
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Singapore"));
        Optional<Facility> nearestCarpark = findNearestCarparkWithin150m(ev.getLatitude(), ev.getLongitude());
        if (nearestCarpark.isPresent()) {
            Facility cp = nearestCarpark.get();
            carparkCost = effectiveCostCalculator.calculate(cp.getId(), cp.getHourlyRate(), now, durationHours * 60);
            carparkName = cp.getName();
        }

        BigDecimal totalCost = evCost.add(carparkCost).setScale(2, RoundingMode.HALF_UP);
        double distanceMeters = haversineMeters(originLat, originLng, ev.getLatitude(), ev.getLongitude());

        Optional<RoutePlanResult> route = routingClient.plan(originLat, originLng, ev.getLatitude(), ev.getLongitude());
        if (route.isEmpty()) return Optional.empty();

        int totalCompatiblePoints = countCompatibleTotalPoints(ev, chargingType, hasChargingType);
        return Optional.of(new EVCandidate(ev, distanceMeters, route.get(), totalCost, evCost, carparkCost, carparkName, availablePoints, totalCompatiblePoints));
    }

    // ── Scoring ──────────────────────────────────────────────────────────────

    private List<EVRecommendationItemDto> score(
            List<EVCandidate> candidates,
            RecommendationPreference preference,
            UserPreference pref
    ) {
        BigDecimal maxCost = candidates.stream().map(EVCandidate::totalCost).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal minCost = candidates.stream().map(EVCandidate::totalCost).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        double maxEta = candidates.stream().mapToDouble(c -> c.route().getDurationSeconds() / 60.0).max().orElse(0.0);
        double minEta = candidates.stream().mapToDouble(c -> c.route().getDurationSeconds() / 60.0).min().orElse(0.0);
        // Exclude -1 (unknown) from min/max so live stations score correctly
        int maxAvail = candidates.stream().mapToInt(EVCandidate::availablePoints).filter(a -> a >= 0).max().orElse(0);
        int minAvail = candidates.stream().mapToInt(EVCandidate::availablePoints).filter(a -> a >= 0).min().orElse(0);

        WeightSet weights = resolveWeights(preference, pref, maxCost.subtract(minCost), maxEta - minEta, maxAvail - minAvail);

        record RawNorm(EVCandidate c, double normCost, double normEta, double normAvail) {}
        record ScoredEV(EVRecommendationItemDto dto, double normAvail, double normCost, double normEta) {}

        // Pass 1: compute normalized scores for every candidate
        List<RawNorm> rawNorms = candidates.stream().map(c -> {
            double eta     = c.route().getDurationSeconds() / 60.0;
            double normCost = priceScore(c.totalCost(), minCost, maxCost);
            double normEta  = etaScore(eta, minEta, maxEta);
            // LTA (live): blended ratio + absolute formula. OCM (unknown): neutral 0.5 sentinel.
            double normAvail = c.availablePoints() < 0 ? 0.5
                    : blendedEvAvailabilityScore(c.availablePoints(), c.totalCompatiblePoints(), maxAvail);
            return new RawNorm(c, normCost, normEta, normAvail);
        }).toList();

        // Pass 2 (Fix 2): Under MOST_AVAILABLE, cap every OCM station's availability score to
        // min(0.5, lowestLTAAvailScore) so unknown-availability stations cannot outrank confirmed live ones.
        double ocmAvailCap = 0.5;
        if (preference == RecommendationPreference.MOST_AVAILABLE) {
            OptionalDouble lowestLTA = rawNorms.stream()
                    .filter(r -> r.c().availablePoints() >= 0)
                    .mapToDouble(RawNorm::normAvail)
                    .min();
            if (lowestLTA.isPresent()) {
                ocmAvailCap = Math.min(0.5, lowestLTA.getAsDouble());
            }
        }
        final double finalOcmCap = ocmAvailCap;

        return rawNorms.stream()
                .map(r -> {
                    double normAvail = r.c().availablePoints() < 0
                            ? Math.min(r.normAvail(), finalOcmCap)
                            : r.normAvail();
                    double score = weights.price() * r.normCost()
                                 + weights.eta()   * r.normEta()
                                 + weights.availability() * normAvail;
                    return new ScoredEV(toDto(r.c(), roundScore(score)), normAvail, r.normCost(), r.normEta());
                })
                .sorted(Comparator.comparingDouble((ScoredEV s) -> s.dto().score()).reversed()
                        .thenComparingDouble((ScoredEV s) -> -s.normAvail())
                        .thenComparingDouble((ScoredEV s) -> -s.normCost())
                        .thenComparingDouble((ScoredEV s) -> -s.normEta())
                        .thenComparing(s -> s.dto().stationName()))
                .map(ScoredEV::dto)
                .toList();
    }

    private WeightSet resolveWeights(
            RecommendationPreference preference,
            UserPreference pref,
            BigDecimal costDelta,
            double etaDelta,
            int availDelta
    ) {
        // CUSTOM: use user-defined weights as-is (no dynamic adjustment)
        if (preference == RecommendationPreference.CUSTOM) {
            return new WeightSet(pref.getWeightPrice(), pref.getWeightDistance(), pref.getWeightAvailability());
        }
        WeightSet base = switch (preference) {
            case CHEAPEST       -> new WeightSet(0.7, 0.2, 0.1);
            case NEAREST        -> new WeightSet(0.05, 0.9, 0.05);
            case MOST_AVAILABLE -> new WeightSet(0.2, 0.1, 0.7);
            default             -> new WeightSet(0.34, 0.33, 0.33);
        };
        return adjustWeights(base, preference, costDelta, etaDelta, availDelta);
    }

    // ── Fallback (no routing data) ───────────────────────────────────────────

    private List<EVRecommendationItemDto> fallback(
            List<EVChargerDto> stations,
            double lat, double lng,
            RecommendationPreference preference,
            UserPreference pref,
            int durationHours,
            ChargingType chargingType,
            boolean hasChargingType,
            Set<String> occupiedIds
    ) {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Singapore"));
        record FallbackItem(EVChargerDto ev, double distMeters, int avail, int totalCompatiblePoints,
                            BigDecimal totalCost, BigDecimal evCost, BigDecimal carparkCost, String carparkName) {}

        List<FallbackItem> items = stations.stream().map(ev -> {
            PlugInfo cheapest = findCheapestCompatiblePlug(ev, chargingType, hasChargingType);
            BigDecimal evCost = cheapest != null
                    ? calculateEVCost(cheapest.pricePerKwh(), cheapest.powerRatingKw(), durationHours)
                    : BigDecimal.ZERO;
            BigDecimal carparkCost = BigDecimal.ZERO;
            String carparkName = null;
            Optional<Facility> cp = findNearestCarparkWithin150m(ev.getLatitude(), ev.getLongitude());
            if (cp.isPresent()) {
                carparkCost = effectiveCostCalculator.calculate(cp.get().getId(), cp.get().getHourlyRate(), now, durationHours * 60);
                carparkName = cp.get().getName();
            }
            BigDecimal total = evCost.add(carparkCost).setScale(2, RoundingMode.HALF_UP);
            int avail = countAvailableCompatiblePoints(ev, chargingType, hasChargingType, occupiedIds);
            int totalCompat = countCompatibleTotalPoints(ev, chargingType, hasChargingType);
            double dist = haversineMeters(lat, lng, ev.getLatitude(), ev.getLongitude());
            return new FallbackItem(ev, dist, avail, totalCompat, total, evCost, carparkCost, carparkName);
        })
        // Fix 1: exclude LTA stations with 0 compatible available points
        .filter(item -> !item.ev().isHasLiveStatus() || item.avail() > 0).toList();

        double maxDist  = items.stream().mapToDouble(FallbackItem::distMeters).max().orElse(1.0);
        double minDist  = items.stream().mapToDouble(FallbackItem::distMeters).min().orElse(0.0);
        BigDecimal maxCost = items.stream().map(FallbackItem::totalCost).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal minCost = items.stream().map(FallbackItem::totalCost).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        int maxAvail = items.stream().mapToInt(FallbackItem::avail).filter(a -> a >= 0).max().orElse(0);
        int minAvail = items.stream().mapToInt(FallbackItem::avail).filter(a -> a >= 0).min().orElse(0);

        WeightSet weights = resolveWeights(preference, pref,
                maxCost.subtract(minCost), maxDist - minDist, maxAvail - minAvail);

        record RawFallback(FallbackItem item, double normCost, double normDist, double normAvail) {}
        record ScoredFallback(EVRecommendationItemDto dto, double normAvail, double normCost, double normDist) {}

        // Pass 1: compute normalized scores
        List<RawFallback> rawFallbacks = items.stream().map(item -> {
            double normCost  = priceScore(item.totalCost(), minCost, maxCost);
            double normDist  = distanceScore(item.distMeters(), minDist, maxDist);
            double normAvail = item.avail() < 0 ? 0.5
                    : blendedEvAvailabilityScore(item.avail(), item.totalCompatiblePoints(), maxAvail);
            return new RawFallback(item, normCost, normDist, normAvail);
        }).toList();

        // Fix 2: cap OCM availability under MOST_AVAILABLE
        double ocmAvailCap = 0.5;
        if (preference == RecommendationPreference.MOST_AVAILABLE) {
            OptionalDouble lowestLTA = rawFallbacks.stream()
                    .filter(r -> r.item().avail() >= 0)
                    .mapToDouble(RawFallback::normAvail)
                    .min();
            if (lowestLTA.isPresent()) {
                ocmAvailCap = Math.min(0.5, lowestLTA.getAsDouble());
            }
        }
        final double finalOcmCap = ocmAvailCap;

        return rawFallbacks.stream()
                .map(r -> {
                    double normAvail = r.item().avail() < 0
                            ? Math.min(r.normAvail(), finalOcmCap)
                            : r.normAvail();
                    double score = weights.price() * r.normCost()
                                 + weights.eta()   * r.normDist()
                                 + weights.availability() * normAvail;
                    EVRecommendationItemDto dto = new EVRecommendationItemDto(
                            r.item().ev().getName(), r.item().ev().getAddress(),
                            r.item().ev().getLatitude(), r.item().ev().getLongitude(),
                            r.item().ev().getPostalCode(),
                            r.item().avail(), r.item().evCost(), r.item().carparkCost(), r.item().totalCost(),
                            r.item().carparkName(), r.item().distMeters(), null, roundScore(score));
                    return new ScoredFallback(dto, normAvail, r.normCost(), r.normDist());
                })
                .sorted(Comparator.comparingDouble((ScoredFallback s) -> s.dto().score()).reversed()
                        .thenComparingDouble((ScoredFallback s) -> -s.normAvail())
                        .thenComparingDouble((ScoredFallback s) -> -s.normCost())
                        .thenComparingDouble((ScoredFallback s) -> -s.normDist())
                        .thenComparing(s -> s.dto().stationName()))
                .map(ScoredFallback::dto)
                .toList();
    }

    // ── Plug parsing helpers ─────────────────────────────────────────────────

    /** Returns the cheapest compatible plug across all charging points at the station. */
    @SuppressWarnings("unchecked")
    private PlugInfo findCheapestCompatiblePlug(EVChargerDto ev, ChargingType chargingType, boolean hasChargingType) {
        if (ev.getChargingPoints() == null) return null;
        PlugInfo cheapest = null;
        for (Object cpObj : ev.getChargingPoints()) {
            if (!(cpObj instanceof Map<?, ?> cp)) continue;
            Object plugTypesObj = cp.get("plugTypes");
            if (!(plugTypesObj instanceof List<?> plugTypes)) continue;
            for (Object ptObj : plugTypes) {
                if (!(ptObj instanceof Map<?, ?> pt)) continue;
                String plugType = String.valueOf(pt.get("plugType") != null ? pt.get("plugType") : "");
                if (hasChargingType && !matchesChargingType(plugType, chargingType)) continue;
                BigDecimal price = parsePrice(pt.get("price"));
                if (price == null) continue;
                double powerRating = parsePowerRating(pt.get("powerRating"));
                if (cheapest == null || price.compareTo(cheapest.pricePerKwh()) < 0) {
                    cheapest = new PlugInfo(plugType, powerRating, price);
                }
            }
        }
        return cheapest;
    }

    @SuppressWarnings("unchecked")
    private boolean hasCompatiblePlug(EVChargerDto ev, ChargingType chargingType) {
        if (ev.getChargingPoints() == null) return false;
        for (Object cpObj : ev.getChargingPoints()) {
            if (!(cpObj instanceof Map<?, ?> cp)) continue;
            Object plugTypesObj = cp.get("plugTypes");
            if (!(plugTypesObj instanceof List<?> plugTypes)) continue;
            for (Object ptObj : plugTypes) {
                if (!(ptObj instanceof Map<?, ?> pt)) continue;
                String plugType = String.valueOf(pt.get("plugType") != null ? pt.get("plugType") : "");
                if (matchesChargingType(plugType, chargingType)) return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private int countAvailableCompatiblePoints(EVChargerDto ev, ChargingType chargingType, boolean hasChargingType, Set<String> occupiedIds) {
        // -1 = availability unknown (OCM station — no real-time data)
        if (!ev.isHasLiveStatus()) return -1;
        if (ev.getChargingPoints() == null) return 0;
        int count = 0;
        for (Object cpObj : ev.getChargingPoints()) {
            if (!(cpObj instanceof Map<?, ?> cp)) continue;
            String status = String.valueOf(cp.get("status") != null ? cp.get("status") : "0");
            if (!status.equals("1")) continue;
            // check not occupied
            Object plugTypesObj = cp.get("plugTypes");
            if (!(plugTypesObj instanceof List<?> plugTypes)) continue;
            boolean compatible = false;
            boolean occupied   = false;
            for (Object ptObj : plugTypes) {
                if (!(ptObj instanceof Map<?, ?> pt)) continue;
                String plugType = String.valueOf(pt.get("plugType") != null ? pt.get("plugType") : "");
                if (!hasChargingType || matchesChargingType(plugType, chargingType)) {
                    compatible = true;
                }
                Object evIdsObj = pt.get("evIds");
                if (evIdsObj instanceof List<?> evIds) {
                    for (Object evIdObj : evIds) {
                        if (!(evIdObj instanceof Map<?, ?> evId)) continue;
                        String evCpId = String.valueOf(evId.get("evCpId") != null ? evId.get("evCpId") : "").trim().toUpperCase();
                        if (occupiedIds.contains(evCpId)) occupied = true;
                    }
                }
            }
            if (compatible && !occupied) count++;
        }
        return count;
    }

    private static boolean matchesChargingType(String plugType, ChargingType chargingType) {
        String normalized = plugType.toLowerCase().replaceAll("[^a-z0-9]", "");
        return switch (chargingType) {
            case TYPE_1_J1772       -> normalized.contains("type1") || normalized.contains("j1772");
            case TYPE_2_MENNEKES    -> normalized.contains("type2") || normalized.contains("mennekes");
            case CCS                -> normalized.contains("ccs");
            case CHADEMO            -> normalized.contains("chademo");
            case TESLA_SUPERCHARGER -> normalized.contains("tesla") || normalized.contains("supercharger");
            case NOT_APPLICABLE     -> false;
        };
    }

    private static BigDecimal parsePrice(Object priceObj) {
        if (priceObj == null) return null;
        try {
            return new BigDecimal(String.valueOf(priceObj).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double parsePowerRating(Object obj) {
        if (obj == null) return 0.0;
        try {
            return Double.parseDouble(String.valueOf(obj).trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // ── Cost calculation ─────────────────────────────────────────────────────

    /** Typical EV battery capacity used as an upper bound on kWh consumed per session. */
    private static final double MAX_KWH_PER_SESSION = 65.0;

    /**
     * EV charging cost = price/kWh × min(powerRating × durationHours, 65 kWh)
     * The 65 kWh cap prevents DC fast chargers from producing absurd cost estimates
     * (e.g. 150kW × 2h = 300 kWh is physically impossible — no consumer EV has that battery).
     */
    private static BigDecimal calculateEVCost(BigDecimal pricePerKwh, double powerRatingKw, int durationHours) {
        if (pricePerKwh == null || pricePerKwh.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        double kWhConsumed = Math.min(powerRatingKw * durationHours, MAX_KWH_PER_SESSION);
        return pricePerKwh.multiply(BigDecimal.valueOf(kWhConsumed)).setScale(2, RoundingMode.HALF_UP);
    }

    // ── Carpark lookup ────────────────────────────────────────────────────────

    private Optional<Facility> findNearestCarparkWithin150m(double lat, double lng) {
        double radiusKm  = CARPARK_SEARCH_RADIUS_METERS / 1000.0;
        double latDelta  = radiusKm / 111.0;
        double lngDelta  = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        return facilityRepository
                .findInBox(FacilityType.CARPARK, lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta)
                .stream()
                .filter(f -> haversineMeters(lat, lng, f.getLatitude(), f.getLongitude()) <= CARPARK_SEARCH_RADIUS_METERS)
                .min(Comparator.comparingDouble(f -> haversineMeters(lat, lng, f.getLatitude(), f.getLongitude())));
    }

    // ── Scoring math ──────────────────────────────────────────────────────────

    private static double priceScore(BigDecimal cost, BigDecimal min, BigDecimal max) {
        BigDecimal range = max.subtract(min);
        if (range.compareTo(BigDecimal.ZERO) <= 0) return 1.0;
        return clamp01(1.0 - cost.subtract(min).divide(range, 8, RoundingMode.HALF_UP).doubleValue());
    }

    private static double etaScore(double eta, double min, double max) {
        double range = max - min;
        if (range <= 0.0) return 1.0;
        return clamp01(1.0 - ((eta - min) / range));
    }

    private static double availabilityScore(int avail, int min, int max) {
        int range = max - min;
        if (range <= 0) return 1.0;
        return clamp01((double) (avail - min) / range);
    }

    /**
     * Counts chargingPoints that have at least one compatible plug (all statuses included).
     * Used as the capacity denominator so blended availability is not diluted by incompatible slots.
     * OCM stations should not use this — they return 0 and fall back to the 0.5 sentinel.
     */
    @SuppressWarnings("unchecked")
    private int countCompatibleTotalPoints(EVChargerDto ev, ChargingType chargingType, boolean hasChargingType) {
        if (!ev.isHasLiveStatus()) return 0;
        if (ev.getChargingPoints() == null) return 0;
        int count = 0;
        for (Object cpObj : ev.getChargingPoints()) {
            if (!(cpObj instanceof Map<?, ?> cp)) continue;
            Object plugTypesObj = cp.get("plugTypes");
            if (!(plugTypesObj instanceof List<?> plugTypes)) continue;
            for (Object ptObj : plugTypes) {
                if (!(ptObj instanceof Map<?, ?> pt)) continue;
                String plugType = String.valueOf(pt.get("plugType") != null ? pt.get("plugType") : "");
                if (!hasChargingType || matchesChargingType(plugType, chargingType)) {
                    count++;
                    break; // count this charging point once
                }
            }
        }
        return count;
    }

    /**
     * Blended availability score for LTA EV stations (mirrors carpark blended formula):
     *   0.5 × (availablePoints / totalPointsAtStation) + 0.5 × (availablePoints / maxAbsoluteInSet)
     * Falls back to absolute-only when totalPoints == 0 (capacity unknown).
     * OCM stations must NOT use this method — they use the 0.5 neutral sentinel.
     */
    private static double blendedEvAvailabilityScore(int availablePoints, int totalPoints, int maxAbsoluteInSet) {
        double absoluteScore = maxAbsoluteInSet > 0
                ? clamp01((double) availablePoints / maxAbsoluteInSet)
                : 1.0;
        double ratioScore = totalPoints > 0
                ? clamp01((double) availablePoints / totalPoints)
                : absoluteScore;
        return 0.5 * ratioScore + 0.5 * absoluteScore;
    }

    private static double distanceScore(double dist, double min, double max) {
        double range = max - min;
        if (range <= 0.0) return 1.0;
        return clamp01(1.0 - ((dist - min) / range));
    }

    private static WeightSet adjustWeights(WeightSet weights, RecommendationPreference preference, BigDecimal costDelta, double etaDelta, int availDelta) {
        return switch (preference) {
            case CHEAPEST       -> adjustEtaWeight(adjustPriceWeight(weights, costDelta), etaDelta);
            case NEAREST        -> adjustPriceWeight(adjustEtaWeightToAvail(weights, etaDelta), costDelta);
            case MOST_AVAILABLE -> adjustEtaWeight(adjustPriceWeight(adjustAvailabilityWeight(weights, availDelta), costDelta), etaDelta);
            case CUSTOM         -> weights;
        };
    }

    private static WeightSet adjustPriceWeight(WeightSet w, BigDecimal costDelta) {
        double multiplier = costDelta.compareTo(new BigDecimal("0.40")) <= 0 ? 0.5
                : costDelta.compareTo(new BigDecimal("1.00")) <= 0 ? 0.8 : 1.0;
        double adjPrice = w.price() * multiplier;
        double remaining = 1.0 - adjPrice;
        double denom = w.eta() + w.availability();
        double adjEta = denom == 0.0 ? 0.0 : remaining * (w.eta() / denom);
        return new WeightSet(adjPrice, adjEta, 1.0 - adjPrice - adjEta);
    }

    private static WeightSet adjustEtaWeight(WeightSet w, double etaDelta) {
        double multiplier = etaDelta <= 2.0 ? 0.5 : etaDelta <= 5.0 ? 0.8 : 1.0;
        double adjEta = w.eta() * multiplier;
        double remaining = 1.0 - adjEta;
        double denom = w.price() + w.availability();
        double adjPrice = denom == 0.0 ? 0.0 : remaining * (w.price() / denom);
        return new WeightSet(adjPrice, adjEta, 1.0 - adjEta - adjPrice);
    }

    private static WeightSet adjustEtaWeightToAvail(WeightSet weights, double etaDeltaMinutes) {
        double multiplier = etaDeltaMinutes <= 2.0 ? 0.5 : etaDeltaMinutes <= 5.0 ? 0.8 : 1.0;
        double adjustedEta = weights.eta() * multiplier;
        double adjustedAvail = 1.0 - adjustedEta - weights.price();
        return new WeightSet(weights.price(), adjustedEta, adjustedAvail);
    }

    private static WeightSet adjustAvailabilityWeight(WeightSet w, int availDelta) {
        double multiplier = availDelta <= 5 ? 0.5 : availDelta <= 20 ? 0.8 : 1.0;
        double adjAvail = w.availability() * multiplier;
        double remaining = 1.0 - adjAvail;
        double denom = w.price() + w.eta();
        double adjPrice = denom == 0.0 ? 0.0 : remaining * (w.price() / denom);
        return new WeightSet(adjPrice, 1.0 - adjAvail - adjPrice, adjAvail);
    }

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }

    private static double roundScore(double score) {
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    private static int normalizeDurationHours(Integer h) {
        if (h == null || h < MIN_DURATION_HOURS) return DEFAULT_DURATION_HOURS;
        return Math.min(h, MAX_DURATION_HOURS);
    }

    // ── Auth / preferences ───────────────────────────────────────────────────

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Number number)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authentication required");
        }
        return userRepository.findById(number.longValue())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "User not found"));
    }

    private UserPreference getOrCreatePreferences(User user) {
        return preferenceRepository.findByUser(user).orElseGet(() ->
                preferenceRepository.save(UserPreference.builder()
                        .user(user)
                        .weightDistance(0.5)
                        .weightPrice(0.3)
                        .weightAvailability(0.2)
                        .parkingDurationHours(DEFAULT_DURATION_HOURS)
                        .evParkingDurationHours(DEFAULT_DURATION_HOURS)
                        .build()));
    }

    // ── DTO conversion ────────────────────────────────────────────────────────

    private static EVRecommendationItemDto toDto(EVCandidate c, double score) {
        return new EVRecommendationItemDto(
                c.ev().getName(), c.ev().getAddress(),
                c.ev().getLatitude(), c.ev().getLongitude(),
                c.ev().getPostalCode(),
                c.availablePoints(), c.evCost(), c.carparkCost(), c.totalCost(),
                c.carparkName(), c.route().getDistanceMeters(),
                c.route().getDurationSeconds() / 60.0, score);
    }

    private void logWinner(
            List<EVRecommendationItemDto> results,
            RecommendationPreference preference,
            String source
    ) {
        if (results == null || results.isEmpty()) {
            log.info("EV recommendation: none (source={}, preference={})", source, preference);
            return;
        }

        for (int i = 0; i < Math.min(results.size(), 3); i++) {
            EVRecommendationItemDto rec = results.get(i);
            log.info("EV RANK {}: station=\"{}\" score={} source={} preference={} availablePoints={} totalCost=${} eta={}min dist={}m",
                    i + 1, rec.stationName(), rec.score(), source, preference,
                    rec.availablePoints(), rec.totalCost(),
                    rec.etaMinutes() != null ? String.format("%.1f", rec.etaMinutes()) : "n/a",
                    Math.round(rec.distanceMeters()));
        }
    }

    // ── Records ───────────────────────────────────────────────────────────────

    private record WeightSet(double price, double eta, double availability) {}

    private record PlugInfo(String plugType, double powerRatingKw, BigDecimal pricePerKwh) {}

    private record EVCandidate(
            EVChargerDto ev,
            double distanceMeters,
            RoutePlanResult route,
            BigDecimal totalCost,
            BigDecimal evCost,
            BigDecimal carparkCost,
            String carparkName,
            int availablePoints,
            int totalCompatiblePoints
    ) {}
}
