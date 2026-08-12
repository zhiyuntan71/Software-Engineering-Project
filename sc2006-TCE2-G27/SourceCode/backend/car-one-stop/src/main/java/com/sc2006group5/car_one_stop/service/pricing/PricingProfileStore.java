package com.sc2006group5.car_one_stop.service.pricing;

import com.sc2006group5.car_one_stop.model.pricing.CarParkPricingProfile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PricingProfileStore {

    private final ConcurrentHashMap<Long, CarParkPricingProfile> store = new ConcurrentHashMap<>();

    public void put(Long facilityId, CarParkPricingProfile profile) {
        if (facilityId == null || profile == null) {
            return;
        }
        store.put(facilityId, profile);
    }

    public Optional<CarParkPricingProfile> get(Long facilityId) {
        if (facilityId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(facilityId));
    }
}
