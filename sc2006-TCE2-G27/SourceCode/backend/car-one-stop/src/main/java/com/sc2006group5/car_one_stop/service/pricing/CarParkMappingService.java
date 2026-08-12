package com.sc2006group5.car_one_stop.service.pricing;

import com.sc2006group5.car_one_stop.entity.map.Facility;
import com.sc2006group5.car_one_stop.enums.map.FacilityType;
import com.sc2006group5.car_one_stop.model.LtaRateRecord;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class CarParkMappingService {

    private static final Logger log = LoggerFactory.getLogger(CarParkMappingService.class);
    private static final double MATCH_THRESHOLD = 0.75;
    private static final Pattern NOISE_WORDS = Pattern.compile("\\b(blk|block|singapore)\\b");
    private static final Pattern PUNCTUATION = Pattern.compile("[^a-z0-9\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
    private volatile Map<Long, BigDecimal> facilityIdToRate = Map.of();

    public synchronized void rebuildMappings(
            List<LtaRateRecord> rateRecords,
            List<Facility> facilities,
            RateParser rateParser
    ) {
        List<Facility> carparkFacilities = facilities == null
                ? List.of()
                : facilities.stream()
                .filter(facility -> facility != null && FacilityType.CARPARK.equals(facility.getType()))
                .toList();

        if (rateRecords == null || rateRecords.isEmpty() || carparkFacilities.isEmpty() || rateParser == null) {
            facilityIdToRate = Map.of();
            warnUnmatchedFacilities(carparkFacilities, Map.of());
            return;
        }

        Map<Long, BigDecimal> nextRates = new HashMap<>();
        Map<Long, Double> nextSimilarities = new HashMap<>();
        Map<Long, String> normalizedFacilityKeys = new HashMap<>();

        for (Facility facility : carparkFacilities) {
            normalizedFacilityKeys.put(facility.getId(), normalizeFacilityKey(facility));
        }

        for (LtaRateRecord record : rateRecords) {
            if (record == null) {
                continue;
            }

            BigDecimal rate = rateParser.computeWeekdayHourlyRate(record);
            if (rate == null) {
                continue;
            }

            String normalizedCarPark = normalizeText(record.getCarPark());
            if (normalizedCarPark.isBlank()) {
                continue;
            }

            Facility bestMatch = null;
            double bestSimilarity = -1.0;

            for (Facility facility : carparkFacilities) {
                String normalizedFacility = normalizedFacilityKeys.get(facility.getId());
                if (normalizedFacility == null || normalizedFacility.isBlank()) {
                    continue;
                }

                double similarity = similarity(normalizedCarPark, normalizedFacility);
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestMatch = facility;
                }
            }

            if (bestMatch != null && bestSimilarity >= MATCH_THRESHOLD) {
                Long facilityId = bestMatch.getId();
                Double previousSimilarity = nextSimilarities.get(facilityId);
                if (previousSimilarity == null || bestSimilarity > previousSimilarity) {
                    nextRates.put(facilityId, rate);
                    nextSimilarities.put(facilityId, bestSimilarity);
                }
            }
        }

        facilityIdToRate = Map.copyOf(nextRates);
        warnUnmatchedFacilities(carparkFacilities, nextRates);
    }

    public Optional<BigDecimal> resolveRate(Long facilityId) {
        if (facilityId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(facilityIdToRate.get(facilityId));
    }

    private void warnUnmatchedFacilities(List<Facility> facilities, Map<Long, BigDecimal> rateMap) {
        long unmatched = facilities.stream()
                .filter(f -> f != null && !rateMap.containsKey(f.getId()))
                .count();
        if (unmatched > 0) {
            log.info("LTA rate mapping: {} matched, {} unmatched (will use default rates)",
                    rateMap.size(), unmatched);
        }
    }

    private String normalizeFacilityKey(Facility facility) {
        List<String> candidates = new ArrayList<>();
        candidates.add(normalizeText(facility.getAddress()));
        candidates.add(normalizeText(facility.getName()));

        for (String candidate : candidates) {
            if (!candidate.isBlank()) {
                return candidate;
            }
        }

        return "";
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.toLowerCase(Locale.ROOT);
        normalized = NOISE_WORDS.matcher(normalized).replaceAll(" ");
        normalized = PUNCTUATION.matcher(normalized).replaceAll(" ");
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ").trim();
        return normalized;
    }

    private double similarity(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return 0.0;
        }

        int maxLength = Math.max(left.length(), right.length());
        if (maxLength == 0) {
            return 0.0;
        }

        Integer distance = levenshteinDistance.apply(left, right);
        if (distance == null) {
            return 0.0;
        }

        double similarity = 1.0 - ((double) distance / (double) maxLength);
        return Math.max(0.0, Math.min(1.0, similarity));
    }
}
