package com.sc2006group5.car_one_stop.service.map;

import com.sc2006group5.car_one_stop.common.external.maps.RoutePlanResult;
import com.sc2006group5.car_one_stop.common.external.maps.RoutingClient;
import com.sc2006group5.car_one_stop.dto.map.CandidateItemDto;
import com.sc2006group5.car_one_stop.dto.map.CarParkRecommendationResult;
import com.sc2006group5.car_one_stop.dto.map.PreferenceUpsertRequest;
import com.sc2006group5.car_one_stop.dto.map.RecommendationItemDto;
import com.sc2006group5.car_one_stop.entity.auth.User;
import com.sc2006group5.car_one_stop.entity.map.Facility;
import com.sc2006group5.car_one_stop.entity.map.UserPreference;
import com.sc2006group5.car_one_stop.enums.map.FacilityType;
import com.sc2006group5.car_one_stop.enums.map.RecommendationPreference;
import com.sc2006group5.car_one_stop.repository.auth.UserRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int DEFAULT_DURATION_HOURS = 1;
    private static final int MIN_DURATION_HOURS = 1;
    private static final int MAX_DURATION_HOURS = 4;
    private static final int AVAILABILITY_EFFECT_CAP_LOTS = 200;

    private final FacilityRepository facilityRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final RoutingClient routingClient;
    private final EffectiveCostCalculator effectiveCostCalculator;

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authentication required");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof Number number)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid authentication principal");
        }

        Long userId = number.longValue();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Authenticated user not found"));
    }

    public UserPreference upsertPreferences(PreferenceUpsertRequest req) {
        User user = getAuthenticatedUser();

        UserPreference pref = preferenceRepository.findByUser(user).orElseGet(() ->
                UserPreference.builder()
                        .user(user)
                        .weightDistance(0.5)
                        .weightPrice(0.3)
                        .weightAvailability(0.2)
                        .parkingDurationHours(DEFAULT_DURATION_HOURS)
                        .build()
        );

        pref.setMaxHourlyRate(req.maxHourlyRate());
        pref.setMinAvailableLots(req.minAvailableLots());
        pref.setRequireSheltered(req.requireSheltered());

        boolean anyWeightProvided = req.weightDistance() != null
                || req.weightPrice() != null
                || req.weightAvailability() != null;

        if (anyWeightProvided) {
            if (req.weightDistance() == null || req.weightPrice() == null || req.weightAvailability() == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Custom preference weights must be provided together.");
            }

            validateCustomWeights(req.weightPrice(), req.weightDistance(), req.weightAvailability());
            pref.setWeightDistance(req.weightDistance());
            pref.setWeightPrice(req.weightPrice());
            pref.setWeightAvailability(req.weightAvailability());
        }

        if (req.parkingDurationHours() != null) {
            validateDurationHours(req.parkingDurationHours());
            pref.setParkingDurationHours(req.parkingDurationHours());
        }

        if (req.evParkingDurationHours() != null) {
            validateDurationHours(req.evParkingDurationHours());
            pref.setEvParkingDurationHours(req.evParkingDurationHours());
        }

        return preferenceRepository.save(pref);
    }

    public UserPreference getPreferences() {
        User user = getAuthenticatedUser();
        return preferenceRepository.findByUser(user).orElseGet(() ->
                preferenceRepository.save(UserPreference.builder()
                        .user(user)
                        .weightDistance(0.5)
                        .weightPrice(0.3)
                        .weightAvailability(0.2)
                        .parkingDurationHours(DEFAULT_DURATION_HOURS)
                        .build())
        );
    }

    /** Returns every carpark that survived hard filtering (blue pins) plus the
     *  top 3 scored recommendations (rank pins). Candidates are collected
     *  before the routing step so failures in the Google Maps call never hide a
     *  carpark from the blue-pin layer. */
    public CarParkRecommendationResult recommendCarparks(
            RecommendationPreference preference,
            double lat,
            double lng,
            int radiusMeters
    ) {
        UserPreference pref = getPreferences();
        int effectiveRadiusMeters = Math.min(Math.max(radiusMeters, 100), 1000);
        LocalTime now = LocalTime.now();

        double radiusKm = effectiveRadiusMeters / 1000.0;
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));

        // Candidates = ALL carparks within radius — ensures every carpark gets a
        // purple pin regardless of price/availability/shelter status.
        List<CandidateItemDto> candidates = facilityRepository
                .findInBox(FacilityType.CARPARK, lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta)
                .stream()
                .filter(f -> haversineMeters(lat, lng, f.getLatitude(), f.getLongitude()) <= effectiveRadiusMeters)
                .map(f -> new CandidateItemDto(f.getId(), f.getName(), f.getLatitude(), f.getLongitude(), f.getAvailableLots(), f.getHourlyRate()))
                .toList();

        if (log.isDebugEnabled()) {
            candidates.forEach(c -> log.debug("PIN candidate id={} name=\"{}\" lat={} lng={}", c.id(), c.name(), c.lat(), c.lng()));
        } else {
            log.info("PIN candidates count={} for user lat={} lng={} radius={}m", candidates.size(), lat, lng, effectiveRadiusMeters);
            candidates.stream().limit(5).forEach(c -> log.info("  sample pin: id={} name=\"{}\" lat={} lng={}", c.id(), c.name(), c.lat(), c.lng()));
        }

        // Run the full scoring pipeline — top 3 for rank pins
        List<RecommendationItemDto> scored = recommend(FacilityType.CARPARK, preference, lat, lng, radiusMeters, 3);

        // Guard: ensure every ranked recommendation is also present in candidates
        for (RecommendationItemDto rec : scored) {
            boolean inCandidates = candidates.stream().anyMatch(c -> rec.id().equals(c.id()));
            if (!inCandidates) {
                log.warn("Recommendation id={} not in candidates — adding to ensure pin is shown", rec.id());
                List<CandidateItemDto> mutable = new ArrayList<>(candidates);
                mutable.add(new CandidateItemDto(rec.id(), rec.name(), rec.lat(), rec.lng(), rec.availableLots(), rec.hourlyRate()));
                candidates = mutable;
            }
        }

        for (int i = 0; i < scored.size(); i++) {
            RecommendationItemDto rec = scored.get(i);
            log.info("CARPARK RANK {}: id={} name=\"{}\" score={} eta={}min dist={}m",
                    i + 1, rec.id(), rec.name(), rec.score(),
                    rec.etaMinutes() != null ? String.format("%.1f", rec.etaMinutes()) : "n/a",
                    Math.round(rec.distanceMeters()));
        }

        return new CarParkRecommendationResult(candidates, scored);
    }

    public List<RecommendationItemDto> recommend(
            FacilityType type,
            RecommendationPreference preference,
            double lat,
            double lng,
            int radiusMeters,
            int limit
    ) {
        UserPreference pref = getPreferences();
        int effectiveDurationHours = normalizeDurationHours(pref.getParkingDurationHours());
        int effectiveRadiusMeters = Math.min(Math.max(radiusMeters, 100), 1000);
        int effectiveLimit = Math.min(Math.max(limit, 1), 20);
        LocalTime now = LocalTime.now();

        // bounding box
        double radiusKm = effectiveRadiusMeters / 1000.0;
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        double minLat = lat - latDelta;
        double maxLat = lat + latDelta;
        double minLng = lng - lngDelta;
        double maxLng = lng + lngDelta;

        List<Facility> eligible = facilityRepository.findInBox(type, minLat, maxLat, minLng, maxLng).stream()
                .filter(f -> haversineMeters(lat, lng, f.getLatitude(), f.getLongitude()) <= effectiveRadiusMeters)
                .filter(this::hasRecommendationData)
                .filter(f -> isOpenAt(now, f))
                .toList();

        log.info("Routing calls planned={} for recommendation request (preference={}, lat={}, lng={})",
                eligible.size(), preference, lat, lng);

        // Fire routing API calls in parallel
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        List<EligibleCandidate> candidates;
        try {
            List<CompletableFuture<java.util.Optional<EligibleCandidate>>> futures = eligible.stream()
                    .map(f -> CompletableFuture.supplyAsync(
                            () -> toCandidate(f, lat, lng, effectiveDurationHours), pool))
                    .toList();
            candidates = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(java.util.Optional::stream)
                    .toList();
        } finally {
            pool.shutdown();
        }

        int eligibleCount = eligible.size();
        int routingFailures = eligibleCount - candidates.size();
        log.info("Routing calls summary: attempted={}, succeeded={}, failed={}",
                eligibleCount, candidates.size(), routingFailures);
        if (eligibleCount > 0 && routingFailures > eligibleCount * 0.2) {
            int failPct = (int) Math.round(100.0 * routingFailures / eligibleCount);
            log.warn("Routing failures for lat={} lng={}: {}/{} calls failed ({}%)",
                    lat, lng, routingFailures, eligibleCount, failPct);
        }

        if (candidates.isEmpty()) {
            log.warn("Using fallback recommendations for lat={} lng={} — {} carparks passed filters but none had full routing data",
                    lat, lng, eligibleCount);
            return fallbackRecommendations(preference, lat, lng, effectiveRadiusMeters, effectiveLimit);
        }

        BigDecimal maxCost = candidates.stream()
                .map(EligibleCandidate::effectiveCost)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        BigDecimal minCost = candidates.stream()
                .map(EligibleCandidate::effectiveCost)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        double maxEtaMinutes = candidates.stream()
                .mapToDouble(EligibleCandidate::etaMinutes)
                .max()
                .orElse(0.0);
        double minEtaMinutes = candidates.stream()
                .mapToDouble(EligibleCandidate::etaMinutes)
                .min()
                .orElse(0.0);
        int maxAvailableLots = candidates.stream()
                .mapToInt(c -> cappedAvailabilityLots(c.facility().getAvailableLots() != null ? c.facility().getAvailableLots() : 0))
                .max()
                .orElse(0);
        int minAvailableLots = candidates.stream()
                .mapToInt(c -> cappedAvailabilityLots(c.facility().getAvailableLots() != null ? c.facility().getAvailableLots() : 0))
                .min()
                .orElse(0);

        // CUSTOM: use user weights directly — no dynamic adjustment
        // CHEAPEST/NEAREST/MOST_AVAILABLE: use preset weights, dynamically adjusted
        int availDelta = maxAvailableLots - minAvailableLots;
        WeightSet weights = preference == RecommendationPreference.CUSTOM
                ? baseWeights(RecommendationPreference.CUSTOM, pref)
                : adjustWeights(baseWeights(preference, pref), preference,
                        maxCost.subtract(minCost), maxEtaMinutes - minEtaMinutes, availDelta);

        log.debug("Weights — preference={}, price={}, eta={}, availability={}",
                preference, roundScore(weights.price()), roundScore(weights.eta()),
                roundScore(weights.availability()));

        record Scored(RecommendationItemDto dto, double normAvail, double normCost, double normEta) {}

        List<RecommendationItemDto> results = candidates.stream()
                .map(candidate -> {
                    double normCost  = priceScore(candidate.effectiveCost(), minCost, maxCost);
                    double normEta   = etaScore(candidate.etaMinutes(), minEtaMinutes, maxEtaMinutes);
                    int lots = candidate.facility().getAvailableLots() != null ? candidate.facility().getAvailableLots() : 0;
                    double normAvail = blendedAvailabilityScore(lots, candidate.facility().getTotalLots(), maxAvailableLots);
                    double score = weights.price() * normCost
                                 + weights.eta()   * normEta
                                 + weights.availability() * normAvail;
                    RecommendationItemDto dto = new RecommendationItemDto(
                            candidate.facility().getId(), candidate.facility().getType(),
                            candidate.facility().getName(), candidate.facility().getAddress(),
                            candidate.facility().getLatitude(), candidate.facility().getLongitude(),
                            candidate.facility().getHourlyRate(), candidate.facility().getAvailableLots(),
                            candidate.facility().getSheltered(),
                            (double) candidate.routePlan().getDistanceMeters(),
                            candidate.etaMinutes(),
                            roundScore(score));
                    return new Scored(dto, normAvail, normCost, normEta);
                })
                .sorted(Comparator.comparingDouble((Scored s) -> s.dto().score()).reversed()
                        .thenComparingDouble((Scored s) -> -s.normAvail())
                        .thenComparingDouble((Scored s) -> -s.normCost())
                        .thenComparingDouble((Scored s) -> -s.normEta())
                        .thenComparing(s -> s.dto().name().toLowerCase(Locale.ROOT)))
                .map(Scored::dto)
                .limit(effectiveLimit)
                .toList();

        for (int i = 0; i < results.size(); i++) {
            RecommendationItemDto rec = results.get(i);
            log.info("CARPARK RANK {}: id={} name=\"{}\" score={} eta={}min dist={}m price=${} avail={}",
                    i + 1, rec.id(), rec.name(), rec.score(),
                    rec.etaMinutes() != null ? String.format("%.1f", rec.etaMinutes()) : "n/a",
                    Math.round(rec.distanceMeters()),
                    rec.hourlyRate(), rec.availableLots());
        }
        return results;
    }

    private List<RecommendationItemDto> fallbackRecommendations(
            RecommendationPreference preference,
            double lat,
            double lng,
            int radiusMeters,
            int limit
    ) {
        // Use DB data — never hit live APIs in a fallback path
        UserPreference pref = getPreferences();
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Singapore"));
        double radiusKm = radiusMeters / 1000.0;
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        List<Facility> nearby = facilityRepository.findInBox(
                FacilityType.CARPARK,
                lat - latDelta, lat + latDelta,
                lng - lngDelta, lng + lngDelta
        ).stream()
                .filter(f -> haversineMeters(lat, lng, f.getLatitude(), f.getLongitude()) <= radiusMeters)
                .filter(this::hasRecommendationData)
                .filter(f -> isOpenAt(now, f))
                .toList();

        if (nearby.isEmpty()) {
            return List.of();
        }

        List<FallbackCandidate> candidates = nearby.stream()
                .map(f -> new FallbackCandidate(
                        f,
                        haversineMeters(lat, lng, f.getLatitude(), f.getLongitude()),
                        f.getAvailableLots() != null ? Math.max(f.getAvailableLots(), 0) : 0
                ))
                .toList();

        double maxDistance = candidates.stream().mapToDouble(FallbackCandidate::distanceMeters).max().orElse(1.0);
        double minDistance = candidates.stream().mapToDouble(FallbackCandidate::distanceMeters).min().orElse(0.0);
        int maxAvailableLots = candidates.stream().mapToInt(FallbackCandidate::availableLots).max().orElse(1);
        int minAvailableLots = candidates.stream().mapToInt(FallbackCandidate::availableLots).min().orElse(0);
        BigDecimal maxHourlyRate = candidates.stream()
                .map(c -> c.facility().getHourlyRate())
                .filter(r -> r != null)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        BigDecimal minHourlyRate = candidates.stream()
                .map(c -> c.facility().getHourlyRate())
                .filter(r -> r != null)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        return candidates.stream()
                .sorted(fallbackComparator(preference, minDistance, maxDistance, minAvailableLots, maxAvailableLots, minHourlyRate, maxHourlyRate))
                .limit(limit)
                .map(candidate -> {
                    double distanceScore = distanceScore(candidate.distanceMeters(), minDistance, maxDistance);
                    double availabilityScore = blendedAvailabilityScore(candidate.availableLots(), candidate.facility().getTotalLots(), maxAvailableLots);
                    double score = switch (preference) {
                        case CHEAPEST -> priceScore(candidate.facility().getHourlyRate() != null ? candidate.facility().getHourlyRate() : maxHourlyRate, minHourlyRate, maxHourlyRate);
                        case NEAREST -> distanceScore;
                        case MOST_AVAILABLE -> availabilityScore;
                        case CUSTOM -> (distanceScore + availabilityScore) / 2.0;
                    };
                    Facility f = candidate.facility();
                    return new RecommendationItemDto(
                            f.getId(),
                            FacilityType.CARPARK,
                            f.getName(),
                            f.getAddress(),
                            f.getLatitude(),
                            f.getLongitude(),
                            f.getHourlyRate(),
                            f.getAvailableLots(),
                            f.getSheltered(),
                            candidate.distanceMeters(),
                            null,
                            roundScore(score)
                    );
                })
                .toList();
    }

    private java.util.Optional<EligibleCandidate> toCandidate(Facility facility, double originLat, double originLng, int durationHours) {
        BigDecimal effectiveCost = effectiveCostCalculator.calculate(facility.getId(), facility.getHourlyRate(), LocalTime.now(ZoneId.of("Asia/Singapore")), durationHours * 60);
        return routingClient.plan(originLat, originLng, facility.getLatitude(), facility.getLongitude())
                .map(route -> new EligibleCandidate(
                        facility,
                        haversineMeters(originLat, originLng, facility.getLatitude(), facility.getLongitude()),
                        route,
                        effectiveCost
                ));
    }

    private static int normalizeDurationHours(Integer durationHours) {
        if (durationHours == null || durationHours <= MIN_DURATION_HOURS) {
            return MIN_DURATION_HOURS;
        }
        if (durationHours >= MAX_DURATION_HOURS) {
            return MAX_DURATION_HOURS;
        }
        return durationHours;
    }

    private static void validateDurationHours(int durationHours) {
        if (durationHours < MIN_DURATION_HOURS || durationHours > MAX_DURATION_HOURS) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "parkingDurationHours must be between 1 and 4 hours"
            );
        }
    }

    private static boolean hardFilter(UserPreference pref, Facility f) {
        if (pref.getMaxHourlyRate() != null && f.getHourlyRate() != null) {
            if (f.getHourlyRate().compareTo(pref.getMaxHourlyRate()) > 0) return false;
        }
        if (pref.getMinAvailableLots() != null && f.getAvailableLots() != null) {
            if (f.getAvailableLots() < pref.getMinAvailableLots()) return false;
        }
        if (Boolean.TRUE.equals(pref.getRequireSheltered())) {
            if (!Boolean.TRUE.equals(f.getSheltered())) return false;
        }
        return true;
    }

    private boolean hasRecommendationData(Facility facility) {
        if (facility.getType() == FacilityType.EV) {
            // EV chargers have no pricing or live availability — always eligible if they have coordinates
            return true;
        }
        // availableLots == null means "unknown" (e.g. URA carparks not in HDB availability feed)
        // availableLots == 0 means genuinely full — exclude those
        return facility.getHourlyRate() != null
                && (facility.getAvailableLots() == null || facility.getAvailableLots() > 0);
    }

    private boolean isOpenAt(LocalTime now, Facility facility) {
        // Unknown hours (null) → assume open rather than silently exclude
        if (facility.getOpeningTime() == null || facility.getClosingTime() == null) {
            return true;
        }
        return isWithinOperatingHours(now, facility.getOpeningTime(), facility.getClosingTime());
    }

    private static boolean isWithinOperatingHours(LocalTime now, LocalTime openingTime, LocalTime closingTime) {
        if (openingTime.equals(closingTime)) {
            return true;
        }
        if (openingTime.isBefore(closingTime)) {
            return !now.isBefore(openingTime) && !now.isAfter(closingTime);
        }
        return !now.isBefore(openingTime) || !now.isAfter(closingTime);
    }

    private static WeightSet baseWeights(RecommendationPreference preference, UserPreference pref) {
        return switch (preference) {
            case CHEAPEST -> new WeightSet(0.7, 0.2, 0.1);
            case NEAREST -> new WeightSet(0.2, 0.7, 0.1);
            case MOST_AVAILABLE -> new WeightSet(0.2, 0.1, 0.7);
            case CUSTOM -> new WeightSet(pref.getWeightPrice(), pref.getWeightDistance(), pref.getWeightAvailability());
        };
    }

    private static WeightSet adjustWeights(
            WeightSet weights,
            RecommendationPreference preference,
            BigDecimal costDelta,
            double etaDeltaMinutes,
            int availDelta
    ) {
        return switch (preference) {
            // CHEAPEST: price spread matters most → reduce price weight if low spread, then eta
            case CHEAPEST -> adjustEtaWeight(adjustPriceWeight(weights, costDelta), etaDeltaMinutes);
            // NEAREST: eta spread matters most → when ETA spread is low, redistribute ONLY to
            // availability (never to price) so price never overtakes ETA as the dominant factor.
            case NEAREST -> adjustPriceWeight(adjustEtaWeightToAvail(weights, etaDeltaMinutes), costDelta);
            // MOST_AVAILABLE: avail first, then price, then eta
            case MOST_AVAILABLE -> adjustEtaWeight(adjustPriceWeight(adjustAvailabilityWeight(weights, availDelta), costDelta), etaDeltaMinutes);
            case CUSTOM -> weights;
        };
    }

    /**
     * Like adjustEtaWeight but redistributes freed weight to availability only, keeping price
     * fixed. Used for NEAREST so that a low ETA spread never inflates price above ETA.
     */
    private static WeightSet adjustEtaWeightToAvail(WeightSet weights, double etaDeltaMinutes) {
        double multiplier = etaDeltaMinutes <= 2.0 ? 0.5 : etaDeltaMinutes <= 5.0 ? 0.8 : 1.0;
        double adjustedEta = weights.eta() * multiplier;
        double adjustedAvail = 1.0 - adjustedEta - weights.price();
        return new WeightSet(weights.price(), adjustedEta, adjustedAvail);
    }

    private static WeightSet adjustPriceWeight(WeightSet weights, BigDecimal costDelta) {
        double multiplier;
        if (costDelta.compareTo(new BigDecimal("0.40")) <= 0) {
            multiplier = 0.5;
        } else if (costDelta.compareTo(new BigDecimal("1.00")) <= 0) {
            multiplier = 0.8;
        } else {
            multiplier = 1.0;
        }

        double adjustedPrice = weights.price() * multiplier;
        double remaining = 1.0 - adjustedPrice;
        double denominator = weights.eta() + weights.availability();
        double adjustedEta = denominator == 0.0 ? 0.0 : remaining * (weights.eta() / denominator);
        double adjustedAvailability = 1.0 - adjustedPrice - adjustedEta;
        return new WeightSet(adjustedPrice, adjustedEta, adjustedAvailability);
    }

    private static WeightSet adjustEtaWeight(WeightSet weights, double etaDeltaMinutes) {
        double multiplier;
        if (etaDeltaMinutes <= 2.0) {
            multiplier = 0.5;
        } else if (etaDeltaMinutes <= 5.0) {
            multiplier = 0.8;
        } else {
            multiplier = 1.0;
        }

        double adjustedEta = weights.eta() * multiplier;
        double remaining = 1.0 - adjustedEta;
        double denominator = weights.price() + weights.availability();
        double adjustedPrice = denominator == 0.0 ? 0.0 : remaining * (weights.price() / denominator);
        double adjustedAvailability = 1.0 - adjustedEta - adjustedPrice;
        return new WeightSet(adjustedPrice, adjustedEta, adjustedAvailability);
    }

    private static WeightSet adjustAvailabilityWeight(WeightSet weights, int availDelta) {
        double multiplier = availDelta <= 5 ? 0.5 : availDelta <= 20 ? 0.8 : 1.0;
        double adjustedAvail = weights.availability() * multiplier;
        double remaining = 1.0 - adjustedAvail;
        double denominator = weights.price() + weights.eta();
        double adjustedPrice = denominator == 0.0 ? 0.0 : remaining * (weights.price() / denominator);
        double adjustedEta = 1.0 - adjustedAvail - adjustedPrice;
        return new WeightSet(adjustedPrice, adjustedEta, adjustedAvail);
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

    private static double priceScore(BigDecimal effectiveCost, BigDecimal minCost, BigDecimal maxCost) {
        BigDecimal range = maxCost.subtract(minCost);
        if (range.compareTo(BigDecimal.ZERO) <= 0) return 1.0;
        return clamp01(1.0 - effectiveCost.subtract(minCost)
                .divide(range, 8, RoundingMode.HALF_UP).doubleValue());
    }

    private static double etaScore(double etaMinutes, double minEtaMinutes, double maxEtaMinutes) {
        double range = maxEtaMinutes - minEtaMinutes;
        if (range <= 0.0) return 1.0;
        return clamp01(1.0 - ((etaMinutes - minEtaMinutes) / range));
    }

    private static double availabilityScore(int availableLots, int minAvailableLots, int maxAvailableLots) {
        int range = maxAvailableLots - minAvailableLots;
        if (range <= 0) {
            return 1.0;
        }
        return clamp01((double) (availableLots - minAvailableLots) / range);
    }

    /**
     * Blended availability score: 0.5 × (slots/totalLots) + 0.5 × (slots/maxAbsoluteSlots)
     * - ratioScore reflects how full the carpark is relative to its own capacity
     * - absoluteScore reflects how it compares to the best-available carpark in the candidate set
     * When totalLots is unknown (null), ratioScore falls back to absoluteScore.
     */
    private static double blendedAvailabilityScore(int availableLots, Integer totalLots, int maxAbsoluteSlots) {
        int cappedAvailableLots = cappedAvailabilityLots(availableLots);
        int cappedMaxAbsoluteSlots = cappedAvailabilityLots(maxAbsoluteSlots);

        double absoluteScore = cappedMaxAbsoluteSlots > 0
                ? clamp01((double) cappedAvailableLots / cappedMaxAbsoluteSlots)
                : 1.0;
        double ratioScore = (totalLots != null && totalLots > 0)
                ? clamp01((double) cappedAvailableLots / totalLots)
                : absoluteScore;
        return 0.5 * ratioScore + 0.5 * absoluteScore;
    }

    private static int cappedAvailabilityLots(int availableLots) {
        return Math.min(Math.max(availableLots, 0), AVAILABILITY_EFFECT_CAP_LOTS);
    }

    private static double distanceScore(double distanceMeters, double minDistanceMeters, double maxDistanceMeters) {
        double range = maxDistanceMeters - minDistanceMeters;
        if (range <= 0.0) {
            return 1.0;
        }
        return clamp01(1.0 - ((distanceMeters - minDistanceMeters) / range));
    }

    private static Comparator<FallbackCandidate> fallbackComparator(
            RecommendationPreference preference,
            double minDistance,
            double maxDistance,
            int minAvailableLots,
            int maxAvailableLots,
            BigDecimal minHourlyRate,
            BigDecimal maxHourlyRate
    ) {
        return Comparator
                .comparingDouble((FallbackCandidate candidate) ->
                        fallbackScore(candidate, preference, minDistance, maxDistance, minAvailableLots, maxAvailableLots, minHourlyRate, maxHourlyRate))
                .reversed()
                .thenComparingDouble(FallbackCandidate::distanceMeters)
                .thenComparing((left, right) -> Integer.compare(right.availableLots(), left.availableLots()))
                .thenComparing(candidate -> candidate.facility().getName().toLowerCase(Locale.ROOT));
    }

    private static double fallbackScore(
            FallbackCandidate candidate,
            RecommendationPreference preference,
            double minDistance,
            double maxDistance,
            int minAvailableLots,
            int maxAvailableLots,
            BigDecimal minHourlyRate,
            BigDecimal maxHourlyRate
    ) {
        double distanceScore = distanceScore(candidate.distanceMeters(), minDistance, maxDistance);
        double availabilityScore = blendedAvailabilityScore(candidate.availableLots(), candidate.facility().getTotalLots(), maxAvailableLots);
        double cheapestScore = priceScore(candidate.facility().getHourlyRate() != null ? candidate.facility().getHourlyRate() : maxHourlyRate, minHourlyRate, maxHourlyRate);

        return switch (preference) {
            case CHEAPEST -> cheapestScore;
            case NEAREST -> distanceScore;
            case MOST_AVAILABLE -> availabilityScore;
            case CUSTOM -> (distanceScore + availabilityScore) / 2.0;
        };
    }

    private static double finalScore(WeightSet weights, double priceScore, double etaScore, double availabilityScore) {
        return weights.price() * priceScore
                + weights.eta() * etaScore
                + weights.availability() * availabilityScore;
    }

    private static double roundScore(double score) {
        return BigDecimal.valueOf(score)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private static void validateCustomWeights(double price, double eta, double availability) {
        if (price <= 0.0 || eta <= 0.0 || availability <= 0.0) {
            throw new ResponseStatusException(BAD_REQUEST, "Each custom weight must be greater than 0.");
        }

        BigDecimal sum = BigDecimal.valueOf(price)
                .add(BigDecimal.valueOf(eta))
                .add(BigDecimal.valueOf(availability));

        // Allow tiny floating-point rounding error (star-derived weights use 4 d.p.)
        if (sum.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.001")) > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Custom weights must sum to 1.0.");
        }
    }

    private record WeightSet(double price, double eta, double availability) {}

    private record EligibleCandidate(
            Facility facility,
            double distanceMeters,
            RoutePlanResult routePlan,
            BigDecimal effectiveCost
    ) {
        double etaMinutes() {
            return routePlan.getDurationSeconds() / 60.0;
        }
    }

    private record FallbackCandidate(
            Facility facility,
            double distanceMeters,
            int availableLots
    ) {}
}

//REDO! include first layer filtering (shelter / ev charger type)
