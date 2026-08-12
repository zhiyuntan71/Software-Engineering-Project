package com.sc2006group5.car_one_stop.service.map;

import com.sc2006group5.car_one_stop.common.external.hdb.HdbClient;
import com.sc2006group5.car_one_stop.dto.map.CarparkAvailabilityDto;
import com.sc2006group5.car_one_stop.entity.map.Facility;
import com.sc2006group5.car_one_stop.enums.map.FacilityType;
import com.sc2006group5.car_one_stop.repository.map.FacilityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AvailabilitySyncService {

    private static final Logger log = LoggerFactory.getLogger(AvailabilitySyncService.class);

    private final HdbClient hdbClient;

    private final FacilityRepository facilityRepository;

    private volatile Map<String, Long> carparkNumberToFacilityId = Map.of();
    private final Set<String> unmatchedCarparksLogged = ConcurrentHashMap.newKeySet();

    public AvailabilitySyncService(
            HdbClient hdbClient,
            FacilityRepository facilityRepository
    ) {
        this.hdbClient = hdbClient;
        this.facilityRepository = facilityRepository;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 240_000)
    @Transactional
    public void syncAvailability() {
        if (carparkNumberToFacilityId.isEmpty()) {
            rebuildMapping();
        }

        List<CarparkAvailabilityDto> availabilityList = hdbClient.fetchAvailability();
        if (availabilityList.isEmpty()) {
            log.warn("HDB availability sync updated 0 rows; no availability records fetched.");
            return;
        }

        Map<String, Long> mapping = carparkNumberToFacilityId;
        List<Facility> facilitiesToUpdate = new ArrayList<>();
        int matchedCount = 0;

        for (CarparkAvailabilityDto availability : availabilityList) {
            if (availability == null || availability.getCarparkNumber() == null) {
                continue;
            }

            Long facilityId = mapping.get(availability.getCarparkNumber());
            if (facilityId == null) {
                if (unmatchedCarparksLogged.add(availability.getCarparkNumber())) {
                    log.debug("No DB Facility mapping found for HDB carpark '{}'", availability.getCarparkNumber());
                }
                continue;
            }

            int lotsAvailable = availability.getLotsAvailable();
            facilityRepository.findById(facilityId).ifPresent(facility -> {
                if (!Objects.equals(facility.getAvailableLots(), lotsAvailable)) {
                    facility.setAvailableLots(lotsAvailable);
                    facilitiesToUpdate.add(facility);
                }
            });
            matchedCount++;
        }

        if (!facilitiesToUpdate.isEmpty()) {
            facilityRepository.saveAll(facilitiesToUpdate);
        }

        if (matchedCount == 0) {
            log.warn("HDB availability sync updated 0 rows; mapping table is empty or unmatched.");
        }
    }

    private void rebuildMapping() {
        Map<String, Long> nextMapping = new HashMap<>();
        facilityRepository.findAll().stream()
                .filter(f -> f != null && FacilityType.CARPARK.equals(f.getType()) && f.getCarparkNo() != null)
                .forEach(f -> nextMapping.put(f.getCarparkNo(), f.getId()));
        carparkNumberToFacilityId = Map.copyOf(nextMapping);
        log.info("AvailabilitySyncService: rebuilt carpark mapping from DB ({} entries)", nextMapping.size());
    }
}
