package com.sc2006group5.car_one_stop.service.pricing;

import com.sc2006group5.car_one_stop.client.LtaRatesClient;
import com.sc2006group5.car_one_stop.entity.map.Facility;
import com.sc2006group5.car_one_stop.enums.map.FacilityType;
import com.sc2006group5.car_one_stop.model.LtaRateRecord;
import com.sc2006group5.car_one_stop.model.pricing.CarParkPricingProfile;
import com.sc2006group5.car_one_stop.model.pricing.PricingBand;
import com.sc2006group5.car_one_stop.repository.map.FacilityRepository;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class PricingHydrationService {

    private static final Logger log = LoggerFactory.getLogger(PricingHydrationService.class);
    private static final ZoneId SINGAPORE_ZONE = ZoneId.of("Asia/Singapore");
    private static final double MATCH_THRESHOLD = 0.75;
    private static final Pattern NOISE_WORDS = Pattern.compile("\\b(blk|block|singapore)\\b");
    private static final Pattern PUNCTUATION = Pattern.compile("[^a-z0-9\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final BigDecimal SURFACE_DEFAULT_RATE = new BigDecimal("1.20");
    private static final BigDecimal MULTI_STOREY_DEFAULT_RATE = new BigDecimal("2.40");
    private static final BigDecimal BASEMENT_DEFAULT_RATE = new BigDecimal("2.40");

    private final LtaRatesClient ltaRatesClient;
    private final CarParkMappingService carParkMappingService;
    private final FacilityRepository facilityRepository;
    private final RateParser rateParser;
    private final PricingProfileStore pricingProfileStore;
    private final PricingBandParser pricingBandParser;
    private final LevenshteinDistance levenshteinDistance;

    public PricingHydrationService(
            LtaRatesClient ltaRatesClient,
            CarParkMappingService carParkMappingService,
            FacilityRepository facilityRepository,
            RateParser rateParser,
            PricingProfileStore pricingProfileStore,
            PricingBandParser pricingBandParser
    ) {
        this.ltaRatesClient = ltaRatesClient;
        this.carParkMappingService = carParkMappingService;
        this.facilityRepository = facilityRepository;
        this.rateParser = rateParser;
        this.pricingProfileStore = pricingProfileStore;
        this.pricingBandParser = pricingBandParser;
        this.levenshteinDistance = new LevenshteinDistance();
    }

    @PostConstruct
    public void hydrateOnStartup() {
        hydrateMissingHourlyRates();
    }

    @Transactional
    public void hydrateMissingHourlyRates() {
        ZonedDateTime startedAt = ZonedDateTime.now(SINGAPORE_ZONE);
        List<LtaRateRecord> rateRecords = ltaRatesClient.fetchAllRates();
        List<Facility> facilities = facilityRepository.findAll();
        Map<Long, LtaRateRecord> resolvedRateRecords = resolveRateRecords(rateRecords, facilities);

        carParkMappingService.rebuildMappings(rateRecords, facilities, rateParser);

        int resolved = 0;
        int defaulted = 0;
        int unresolved = 0;

        List<Facility> facilitiesToUpdate = new ArrayList<>();
        for (Facility facility : facilities) {
            LtaRateRecord matchedRateRecord = resolvedRateRecords.get(facility.getId());
            if (matchedRateRecord != null) {
                pricingProfileStore.put(facility.getId(), buildPricingProfile(facility.getId(), matchedRateRecord));
            }

            Optional<BigDecimal> resolvedRate = carParkMappingService.resolveRate(facility.getId());
            if (resolvedRate.isPresent()) {
                facility.setHourlyRate(normalizeRate(resolvedRate.get()));
                facilitiesToUpdate.add(facility);
                resolved++;
                continue;
            }

            Optional<BigDecimal> defaultRate = resolveDefaultRate(facility);
            if (defaultRate.isPresent()) {
                facility.setHourlyRate(normalizeRate(defaultRate.get()));
                facilitiesToUpdate.add(facility);
                defaulted++;
            } else {
                unresolved++;
            }
        }

        if (!facilitiesToUpdate.isEmpty()) {
            facilityRepository.saveAll(facilitiesToUpdate);
        }

        log.info(
                "Pricing hydration complete at {}: {} resolved, {} defaulted, {} unresolved",
                startedAt,
                resolved,
                defaulted,
                unresolved
        );
    }

    private Optional<BigDecimal> resolveDefaultRate(Facility facility) {
        if (facility == null || !FacilityType.CARPARK.equals(facility.getType())) {
            return Optional.empty();
        }

        String descriptor = (safeText(facility.getName()) + " " + safeText(facility.getAddress()))
                .toLowerCase(Locale.ROOT);

        if (descriptor.contains("multi-storey car park")
                || descriptor.contains("multi storey")
                || descriptor.contains("multistorey")) {
            return Optional.of(MULTI_STOREY_DEFAULT_RATE);
        }
        if (descriptor.contains("basement car park") || descriptor.contains("basement")) {
            return Optional.of(BASEMENT_DEFAULT_RATE);
        }
        if (descriptor.contains("surface car park") || descriptor.contains("surface")) {
            return Optional.of(SURFACE_DEFAULT_RATE);
        }

        // Catch-all: all remaining carparks (e.g. HDB block addresses with no type keyword)
        // default to the standard surface rate
        return Optional.of(SURFACE_DEFAULT_RATE);
    }

    private BigDecimal normalizeRate(BigDecimal rate) {
        return rate.setScale(2, RoundingMode.HALF_UP);
    }

    private Map<Long, LtaRateRecord> resolveRateRecords(List<LtaRateRecord> rateRecords, List<Facility> facilities) {
        Map<Long, LtaRateRecord> resolved = new HashMap<>();
        Map<Long, Double> similarityByFacility = new HashMap<>();

        List<Facility> carparkFacilities = facilities.stream()
                .filter(facility -> facility != null && FacilityType.CARPARK.equals(facility.getType()))
                .toList();

        Map<Long, String> normalizedFacilityKeys = new HashMap<>();
        for (Facility facility : carparkFacilities) {
            normalizedFacilityKeys.put(facility.getId(), normalizeFacilityKey(facility));
        }

        for (LtaRateRecord record : rateRecords) {
            if (record == null) {
                continue;
            }

            String normalizedCarPark = normalizeText(record.getCarPark());
            if (normalizedCarPark.isBlank()) {
                continue;
            }

            Facility bestFacility = null;
            double bestSimilarity = -1.0;
            for (Facility facility : carparkFacilities) {
                String normalizedFacility = normalizedFacilityKeys.get(facility.getId());
                if (normalizedFacility == null || normalizedFacility.isBlank()) {
                    continue;
                }

                double similarity = similarity(normalizedCarPark, normalizedFacility);
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestFacility = facility;
                }
            }

            if (bestFacility != null && bestSimilarity >= MATCH_THRESHOLD) {
                Long facilityId = bestFacility.getId();
                Double existingSimilarity = similarityByFacility.get(facilityId);
                if (existingSimilarity == null || bestSimilarity > existingSimilarity) {
                    resolved.put(facilityId, record);
                    similarityByFacility.put(facilityId, bestSimilarity);
                }
            }
        }

        return resolved;
    }

    private CarParkPricingProfile buildPricingProfile(Long facilityId, LtaRateRecord record) {
        List<PricingBand> weekdayBands = new ArrayList<>();
        weekdayBands.addAll(pricingBandParser.parse(record.getWeekdaysRate1()));
        weekdayBands.addAll(pricingBandParser.parse(record.getWeekdaysRate2()));

        return new CarParkPricingProfile(
                facilityId,
                weekdayBands,
                pricingBandParser.parse(record.getSaturdayRate()),
                pricingBandParser.parse(record.getSunPhRate())
        );
    }

    private String normalizeFacilityKey(Facility facility) {
        String address = normalizeText(facility.getAddress());
        if (!address.isBlank()) {
            return address;
        }
        return normalizeText(facility.getName());
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

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
