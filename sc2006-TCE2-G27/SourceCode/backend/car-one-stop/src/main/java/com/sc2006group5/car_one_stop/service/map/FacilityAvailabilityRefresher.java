package com.sc2006group5.car_one_stop.service.map;

import com.sc2006group5.car_one_stop.common.external.hdb.HdbClient;
import com.sc2006group5.car_one_stop.common.external.ura.UraClient;
import com.sc2006group5.car_one_stop.dto.map.CarparkAvailabilityDto;
import com.sc2006group5.car_one_stop.entity.map.Facility;
import com.sc2006group5.car_one_stop.enums.map.FacilityType;
import com.sc2006group5.car_one_stop.repository.map.FacilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FacilityAvailabilityRefresher {

    private final FacilityRepository facilityRepository;
    private final HdbClient hdbClient;
    private final UraClient uraClient;

//Subsequent runs are every 2 minutes.
    @Scheduled(initialDelay = 130_000, fixedDelay = 120_000)
    @Transactional
    public void refresh() {
        List<CarparkAvailabilityDto> hdbData = hdbClient.fetchAvailability();
        List<CarparkAvailabilityDto> uraData = uraClient.fetchAvailability();
        if (hdbData.isEmpty() && uraData.isEmpty()) return;

        // HDB takes priority; URA fills in the rest
        Map<String, Integer> availMap = new HashMap<>();
        hdbData.forEach(d -> availMap.put(d.getCarparkNumber(), d.getLotsAvailable()));
        uraData.forEach(d -> availMap.putIfAbsent(d.getCarparkNumber(), d.getLotsAvailable()));

        List<Facility> carparks = facilityRepository.findAllByType(FacilityType.CARPARK);
        List<Facility> toUpdate = new ArrayList<>();

        for (Facility f : carparks) {
            if (f.getCarparkNo() == null) continue;
            Integer lots = availMap.get(f.getCarparkNo());
            if (lots != null && !lots.equals(f.getAvailableLots())) {
                f.setAvailableLots(lots);
                toUpdate.add(f);
            }
        }

        int totalKnown = (int) carparks.stream().filter(f -> f.getCarparkNo() != null).count();
        if (!toUpdate.isEmpty()) {
            facilityRepository.saveAll(toUpdate);
            log.info("Refreshed availability for {}/{} carparks (HDB feed: {}, URA feed: {})",
                    toUpdate.size(), totalKnown, hdbData.size(), uraData.size());
        } else {
            log.debug("Availability refresh: no changes ({}/{} carparks checked, HDB feed: {}, URA feed: {})",
                    totalKnown, carparks.size(), hdbData.size(), uraData.size());
        }
    }
}
