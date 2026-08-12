package com.sc2006group5.car_one_stop.service.pricing;

import com.sc2006group5.car_one_stop.entity.map.Facility;
import com.sc2006group5.car_one_stop.model.pricing.CarParkPricingProfile;
import com.sc2006group5.car_one_stop.model.pricing.DayType;
import com.sc2006group5.car_one_stop.model.pricing.PricingBand;
import com.sc2006group5.car_one_stop.repository.map.FacilityRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class EffectiveCostCalculator {

    private static final ZoneId SINGAPORE_ZONE = ZoneId.of("Asia/Singapore");
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final int MINUTES_PER_DAY = 24 * 60;

    private final PricingProfileStore pricingProfileStore;
    private final FacilityRepository facilityRepository;

    public EffectiveCostCalculator(
            PricingProfileStore pricingProfileStore,
            FacilityRepository facilityRepository
    ) {
        this.pricingProfileStore = pricingProfileStore;
        this.facilityRepository = facilityRepository;
    }

    /** Convenience overload — looks up hourlyRate from DB. Use only when Facility is not already loaded. */
    public BigDecimal calculate(Long facilityId, LocalTime entryTime, int durationMinutes) {
        BigDecimal hourlyRate = facilityRepository.findById(facilityId)
                .map(Facility::getHourlyRate)
                .orElse(null);
        return calculate(facilityId, hourlyRate, entryTime, durationMinutes);
    }

    /**
     * Calculate effective cost using the caller-supplied fallback rate — avoids a DB roundtrip
     * when the Facility object is already in scope (e.g. inside a parallel recommendation loop).
     */
    public BigDecimal calculate(Long facilityId, BigDecimal knownHourlyRate, LocalTime entryTime, int durationMinutes) {
        if (facilityId == null || durationMinutes <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        LocalTime sessionEntryTime = entryTime == null ? LocalTime.now(SINGAPORE_ZONE) : entryTime;
        BigDecimal fallbackHourlyRate = knownHourlyRate;

        Optional<CarParkPricingProfile> profileOptional = pricingProfileStore.get(facilityId);
        if (profileOptional.isEmpty()) {
            return fallbackCost(fallbackHourlyRate, durationMinutes);
        }

        LocalTime currentTime = sessionEntryTime;
        LocalDate currentDate = LocalDate.now(SINGAPORE_ZONE);
        int remainingMinutes = durationMinutes;
        BigDecimal totalCost = BigDecimal.ZERO;
        Set<PricingBand> chargedFlatBands = Collections.newSetFromMap(new IdentityHashMap<>());

        while (remainingMinutes > 0) {
            DayType currentDayType = resolveDayType(currentDate);
            List<PricingBand> bands = profileOptional.get().bandsFor(currentDayType);
            Optional<PricingBand> activeBand = findActiveBand(bands, currentTime);
            if (activeBand.isEmpty() || activeBand.get().isUnknown()) {
                int minutesInSegment = minutesUntilNextBandStart(bands, currentTime);
                int minutesUntilMidnight = minutesUntilMidnight(currentTime);
                if (minutesInSegment <= 0) {
                    minutesInSegment = Math.min(remainingMinutes, minutesUntilMidnight);
                } else {
                    minutesInSegment = Math.min(Math.min(minutesInSegment, remainingMinutes), minutesUntilMidnight);
                }

                totalCost = totalCost.add(prorated(fallbackHourlyRate, minutesInSegment));
                TimeCursor nextCursor = advanceCursor(currentDate, currentTime, minutesInSegment);
                currentDate = nextCursor.date();
                currentTime = nextCursor.time();
                remainingMinutes -= minutesInSegment;
                continue;
            }

            PricingBand band = activeBand.get();
            int minutesUntilBandEnds = minutesBetween(currentTime, band.getEnd());
            int minutesUntilMidnight = minutesUntilMidnight(currentTime);
            int minutesInSegment = Math.min(
                    remainingMinutes,
                    Math.min(Math.max(minutesUntilBandEnds, 0), minutesUntilMidnight)
            );
            if (minutesInSegment <= 0) {
                minutesInSegment = Math.min(remainingMinutes, minutesUntilMidnight);
            }

            if (band.isFlatRate()) {
                if (!chargedFlatBands.contains(band)) {
                    totalCost = totalCost.add(band.getRatePerHour());
                    chargedFlatBands.add(band);
                }
            } else {
                totalCost = totalCost.add(prorated(band.getRatePerHour(), minutesInSegment));
            }

            TimeCursor nextCursor = advanceCursor(currentDate, currentTime, minutesInSegment);
            currentDate = nextCursor.date();
            currentTime = nextCursor.time();
            remainingMinutes -= minutesInSegment;
        }

        return totalCost.setScale(2, RoundingMode.HALF_UP);
    }

    private DayType resolveDayType(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        // TODO: Add public holiday detection and map PH to SUNDAY_PH.
        if (dayOfWeek == DayOfWeek.SATURDAY) {
            return DayType.SATURDAY;
        }
        if (dayOfWeek == DayOfWeek.SUNDAY) {
            return DayType.SUNDAY_PH;
        }
        return DayType.WEEKDAY;
    }

    private BigDecimal fallbackCost(BigDecimal hourlyRate, int durationMinutes) {
        return prorated(hourlyRate, durationMinutes).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal prorated(BigDecimal hourlyRate, int minutes) {
        if (hourlyRate == null || minutes <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal hourFraction = BigDecimal.valueOf(minutes).divide(SIXTY, 8, RoundingMode.HALF_UP);
        return hourlyRate.multiply(hourFraction);
    }

    private int minutesUntilNextBandStart(List<PricingBand> bands, LocalTime currentTime) {
        if (bands == null || bands.isEmpty()) {
            return 0;
        }

        int minDelta = Integer.MAX_VALUE;
        boolean found = false;

        for (PricingBand band : bands) {
            if (band == null || band.isUnknown() || band.getStart() == null) {
                continue;
            }

            int delta = forwardMinutes(currentTime, band.getStart());
            if (delta > 0 && delta < minDelta) {
                minDelta = delta;
                found = true;
            }
        }

        return found ? minDelta : 0;
    }

    private int forwardMinutes(LocalTime from, LocalTime to) {
        int fromMinutes = from.getHour() * 60 + from.getMinute();
        int toMinutes = to.getHour() * 60 + to.getMinute();
        if (toMinutes >= fromMinutes) {
            return toMinutes - fromMinutes;
        }
        return (MINUTES_PER_DAY - fromMinutes) + toMinutes;
    }

    private Optional<PricingBand> findActiveBand(List<PricingBand> bands, LocalTime t) {
        if (bands == null || bands.isEmpty() || t == null) {
            return Optional.empty();
        }

        for (PricingBand band : bands) {
            if (band == null || band.isUnknown() || band.getStart() == null || band.getEnd() == null) {
                continue;
            }

            LocalTime start = band.getStart();
            LocalTime end = band.getEnd();
            boolean active;

            if (band.isOvernight()) {
                active = !t.isBefore(start) || t.isBefore(end);
            } else if (end.equals(LocalTime.MIDNIGHT)) {
                active = !t.isBefore(start);
            } else if (start.equals(end)) {
                active = true;
            } else {
                active = !t.isBefore(start) && t.isBefore(end);
            }

            if (active) {
                return Optional.of(band);
            }
        }
        return Optional.empty();
    }

    private int minutesBetween(LocalTime from, LocalTime to) {
        int fromMinutes = from.getHour() * 60 + from.getMinute();
        int toMinutes = to.getHour() * 60 + to.getMinute();

        if (to.equals(from) || to.equals(LocalTime.MIDNIGHT)) {
            return MINUTES_PER_DAY - fromMinutes;
        }
        if (toMinutes > fromMinutes) {
            return toMinutes - fromMinutes;
        }
        return (MINUTES_PER_DAY - fromMinutes) + toMinutes;
    }

    private int minutesUntilMidnight(LocalTime time) {
        int fromMinutes = time.getHour() * 60 + time.getMinute();
        return MINUTES_PER_DAY - fromMinutes;
    }

    private TimeCursor advanceCursor(LocalDate date, LocalTime time, int minutesToAdvance) {
        LocalDateTime dateTime = LocalDateTime.of(date, time).plusMinutes(minutesToAdvance);
        return new TimeCursor(dateTime.toLocalDate(), dateTime.toLocalTime());
    }

    private record TimeCursor(LocalDate date, LocalTime time) {
    }
}
