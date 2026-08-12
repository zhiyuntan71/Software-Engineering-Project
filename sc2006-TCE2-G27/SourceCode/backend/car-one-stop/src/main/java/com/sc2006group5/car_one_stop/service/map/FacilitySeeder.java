package com.sc2006group5.car_one_stop.service.map;

import com.sc2006group5.car_one_stop.common.external.hdb.HdbClient;
import com.sc2006group5.car_one_stop.common.external.lta.LtaClient;
import com.sc2006group5.car_one_stop.common.external.ura.UraClient;
import com.sc2006group5.car_one_stop.dto.map.CarparkAvailabilityDto;
import com.sc2006group5.car_one_stop.dto.map.CarparkInfoDto;
import com.sc2006group5.car_one_stop.dto.map.EVChargerDto;
import com.sc2006group5.car_one_stop.dto.map.UraCarParkDto;
import com.sc2006group5.car_one_stop.entity.map.Facility;
import com.sc2006group5.car_one_stop.enums.map.FacilityType;
import com.sc2006group5.car_one_stop.repository.map.FacilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class FacilitySeeder implements ApplicationRunner {

    // Standard HDB short-term parking rate (uniform across all HDB carparks)
    private static final BigDecimal HDB_HOURLY_RATE = new BigDecimal("1.20");

    private final FacilityRepository facilityRepository;
    private final HdbClient hdbClient;
    private final UraClient uraClient;
    private final LtaClient ltaClient;

    /** Set facility.force-reseed=true in application-local.yml to wipe and re-seed all
     *  facility data on the next startup (e.g. after a coordinate conversion fix).
     *  Reset to false after the first restart or the DB will be wiped every time. */
    @Value("${facility.force-reseed:false}")
    private boolean forceReseed;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (forceReseed) {
            log.warn("facility.force-reseed=true — deleting all facility data and re-seeding from scratch");
            facilityRepository.deleteAll();
        }
        seedCarparks();
        seedEvChargers();
    }

    private void seedCarparks() {
        long existing = facilityRepository.countByType(FacilityType.CARPARK);
        if (existing > 0) {
            log.info("HDB carpark info already seeded ({} records) — skipping re-fetch", existing);
            return;
        }

        // URA token fetch: runs first (triggers token internally), no delay
        List<UraCarParkDto> uraCarParks = uraClient.fetchAllCarParks();

        // HDB carpark info: 3s after app start
        sleepMs(3_000);
        List<CarparkInfoDto> hdbInfos = hdbClient.fetchAllCarparkInfo();

        // HDB availability: 5s after app start
        sleepMs(2_000);
        List<CarparkAvailabilityDto> hdbAvail = hdbClient.fetchAvailability();

        // URA availability: 7s after app start
        sleepMs(2_000);
        List<CarparkAvailabilityDto> uraAvail = uraClient.fetchAvailability();

        // Build combined availability map (HDB wins on conflict)
        Map<String, Integer> availMap = new HashMap<>();
        hdbAvail.forEach(d -> availMap.put(d.getCarparkNumber(), d.getLotsAvailable()));
        uraAvail.forEach(d -> availMap.putIfAbsent(d.getCarparkNumber(), d.getLotsAvailable()));

        List<Facility> hdbFacilities = buildHdbFacilities(hdbInfos, availMap);
        List<Facility> uraFacilities = buildUraFacilities(uraCarParks, availMap);

        List<Facility> all = new ArrayList<>();
        all.addAll(hdbFacilities);
        all.addAll(uraFacilities);

        if (all.isEmpty()) {
            log.warn("Both HDB and URA returned no data — skipping facility seed");
            return;
        }

        facilityRepository.deleteAllByType(FacilityType.CARPARK);
        facilityRepository.saveAll(all);
        log.info("Seeded {} carpark facilities ({} HDB + {} URA)",
                all.size(), hdbFacilities.size(), uraFacilities.size());
    }

    private void seedEvChargers() {
        long existing = facilityRepository.countByType(FacilityType.EV);
        if (existing > 0) {
            log.info("EV charger facilities already seeded ({} records) — skipping", existing);
            return;
        }

        List<EVChargerDto> evChargers = ltaClient.fetchAllEVChargers();
        if (evChargers.isEmpty()) {
            log.warn("LTA EV charger fetch returned no data — skipping EV seed");
            return;
        }

        List<Facility> evFacilities = evChargers.stream()
                .filter(ev -> ev.getLatitude() != 0.0 && ev.getLongitude() != 0.0)
                .filter(ev -> isValidSingaporeCoord(ev.getLatitude(), ev.getLongitude()))
                .filter(ev -> ev.getName() != null && !ev.getName().isBlank())
                .map(ev -> Facility.builder()
                        .type(FacilityType.EV)
                        .name(ev.getName())
                        .address(ev.getAddress() != null ? ev.getAddress() : ev.getName())
                        .latitude(ev.getLatitude())
                        .longitude(ev.getLongitude())
                        .build())
                .toList();

        if (evFacilities.isEmpty()) {
            log.warn("All EV charger records had missing coordinates — skipping EV seed");
            return;
        }

        facilityRepository.saveAll(evFacilities);
        log.info("Seeded {} EV charger facilities", evFacilities.size());
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── HDB ───────────────────────────────────────────────────────────────

    private List<Facility> buildHdbFacilities(List<CarparkInfoDto> infos, Map<String, Integer> availability) {
        if (infos.isEmpty()) {
            log.warn("HDB carpark info returned empty — skipping HDB facilities");
            return List.of();
        }

        List<Facility> facilities = infos.stream()
                .filter(c -> !"NO".equalsIgnoreCase(c.getShortTermParking()))
                .map(c -> buildHdbFacility(c, availability.getOrDefault(c.getCarparkNo(), 0)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("Prepared {} HDB carpark facilities", facilities.size());
        return facilities;
    }

    private static boolean isValidSingaporeCoord(double lat, double lng) {
        return lat >= 1.2 && lat <= 1.5 && lng >= 103.6 && lng <= 104.0;
    }

    private Facility buildHdbFacility(CarparkInfoDto info, int availableLots) {
        LocalTime[] times = parseOpeningHours(info.getShortTermParking());
        if (times == null) return null;

        double lat = info.getLatitude();
        double lng = info.getLongitude();
        if (!isValidSingaporeCoord(lat, lng)) {
            log.warn("HDB carpark {} has out-of-bounds coordinates ({}, {}) — skipping", info.getCarparkNo(), lat, lng);
            return null;
        }

        BigDecimal rate = isFreeNow(info.getFreeParkingInfo()) ? BigDecimal.ZERO : HDB_HOURLY_RATE;

        return Facility.builder()
                .type(FacilityType.CARPARK)
                .name(info.getAddress())
                .address(info.getAddress())
                .latitude(lat)
                .longitude(lng)
                .hourlyRate(rate)
                .availableLots(availableLots > 0 ? availableLots : null)
                .sheltered(null)
                .openingTime(times[0])
                .closingTime(times[1])
                .carparkNo(info.getCarparkNo())
                .build();
    }

    // ── URA ───────────────────────────────────────────────────────────────

    private List<Facility> buildUraFacilities(List<UraCarParkDto> carParks, Map<String, Integer> availability) {
        if (carParks.isEmpty()) {
            log.warn("URA carpark data returned empty — skipping URA facilities");
            return List.of();
        }

        List<Facility> facilities = carParks.stream()
                .map(cp -> buildUraFacility(cp, availability.getOrDefault(cp.getPpCode(), 0)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("Prepared {} URA carpark facilities", facilities.size());
        return facilities;
    }

    private Facility buildUraFacility(UraCarParkDto cp, int availableLots) {
        // URA carparks with no parseable opening hours are treated as 24h
        LocalTime open  = cp.getStartTime() != null ? cp.getStartTime() : LocalTime.MIDNIGHT;
        LocalTime close = cp.getEndTime()   != null ? cp.getEndTime()   : LocalTime.MIDNIGHT;

        double lat = cp.getLatitude();
        double lng = cp.getLongitude();
        if (!isValidSingaporeCoord(lat, lng)) {
            log.warn("URA carpark {} has out-of-bounds coordinates ({}, {}) — skipping", cp.getPpCode(), lat, lng);
            return null;
        }

        return Facility.builder()
                .type(FacilityType.CARPARK)
                .name(cp.getPpName())
                .address(cp.getPpName())
                .latitude(lat)
                .longitude(lng)
                .hourlyRate(cp.getHourlyRate())
                .availableLots(availableLots > 0 ? availableLots : null)
                .sheltered(null)
                .openingTime(open)
                .closingTime(close)
                .carparkNo(cp.getPpCode())
                .build();
    }

    /**
     * Parses the HDB short_term_parking field into [openingTime, closingTime].
     *
     * "WHOLE DAY"   → [00:00, 00:00]  (equal times = always open in isWithinOperatingHours)
     * "7AM-10.30PM" → [07:00, 22:30]
     * "NO"          → null  (skip)
     */
    static LocalTime[] parseOpeningHours(String shortTermParking) {
        if (shortTermParking == null || shortTermParking.isBlank()) return null;
        String trimmed = shortTermParking.trim();

        if ("NO".equalsIgnoreCase(trimmed)) return null;

        if ("WHOLE DAY".equalsIgnoreCase(trimmed)) {
            return new LocalTime[]{ LocalTime.MIDNIGHT, LocalTime.MIDNIGHT };
        }

        String[] parts = trimmed.split("-");
        if (parts.length != 2) return null;

        LocalTime open  = parseTime(parts[0].trim());
        LocalTime close = parseTime(parts[1].trim());
        if (open == null || close == null) return null;

        return new LocalTime[]{ open, close };
    }

    //so we check Sundays only — which covers the vast majority of free-parking cases.
    static boolean isFreeNow(String freeParkingInfo) {
        if (freeParkingInfo == null || freeParkingInfo.isBlank()) return false;
        String s = freeParkingInfo.trim().toUpperCase();
        if ("NO".equals(s)) return false;
        if (!s.contains("SUN")) return false;

        DayOfWeek today = LocalDate.now().getDayOfWeek();
        if (today != DayOfWeek.SUNDAY) return false;

        // Extract the time range after "FR " (or "FROM ")
        int frIdx = s.indexOf(" FR ");
        if (frIdx < 0) return true; // "SUN" mentioned but no window — treat as all-day free

        String timeRange = s.substring(frIdx + 4).trim();
        String[] parts = timeRange.split("-");
        if (parts.length != 2) return true;

        LocalTime open  = parseTime(parts[0].trim());
        LocalTime close = parseTime(parts[1].trim());
        if (open == null || close == null) return true;

        LocalTime now = LocalTime.now();
        if (open.equals(close)) return true; // same = whole day
        if (open.isBefore(close)) return !now.isBefore(open) && now.isBefore(close);
        // crosses midnight
        return !now.isBefore(open) || now.isBefore(close);
    }

    // Parses "7AM", "10.30PM" → LocalTime
    private static LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toUpperCase();

        boolean pm = s.endsWith("PM");
        boolean am = s.endsWith("AM");
        if (!pm && !am) return null;

        String timePart = s.substring(0, s.length() - 2).trim();
        int hour, minute = 0;
        try {
            if (timePart.contains(".")) {
                String[] hm = timePart.split("\\.");
                hour = Integer.parseInt(hm[0]);
                minute = Integer.parseInt(hm[1]);
            } else {
                hour = Integer.parseInt(timePart);
            }
        } catch (NumberFormatException e) {
            return null;
        }

        if (pm && hour != 12) hour += 12;
        if (am && hour == 12) hour = 0;

        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
        return LocalTime.of(hour, minute);
    }
}
