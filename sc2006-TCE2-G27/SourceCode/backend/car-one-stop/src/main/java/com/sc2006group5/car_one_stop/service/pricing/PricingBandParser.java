package com.sc2006group5.car_one_stop.service.pricing;

import com.sc2006group5.car_one_stop.model.pricing.PricingBand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PricingBandParser {

    private static final Logger log = LoggerFactory.getLogger(PricingBandParser.class);
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile(
            "(\\d{1,2}(?:\\.\\d{2})?)\\s*(am|pm)\\s*[-\\u2013]\\s*(\\d{1,2}(?:\\.\\d{2})?)\\s*(am|pm)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DOLLAR_AMOUNT_PATTERN = Pattern.compile("\\$(\\d+(?:\\.\\d{1,2})?)");
    private static final LocalTime FULL_DAY_START = LocalTime.MIDNIGHT;
    private static final LocalTime FULL_DAY_END = LocalTime.of(23, 59);

    public List<PricingBand> parse(String rateField) {
        if (rateField == null || rateField.isBlank()) {
            return List.of(PricingBand.unknown());
        }

        try {
            String[] sections = rateField.split(";");
            List<PricingBand> bands = new ArrayList<>();

            for (String section : sections) {
                if (section == null || section.isBlank()) {
                    continue;
                }
                bands.add(parseSection(section.trim()));
            }

            if (bands.isEmpty()) {
                return List.of(PricingBand.unknown());
            }
            return bands;
        } catch (Exception ex) {
            log.warn("Unable to parse pricing band '{}': {}", rateField, ex.getMessage());
            return List.of(PricingBand.unknown());
        }
    }

    private PricingBand parseSection(String section) {
        String normalized = section.toLowerCase(Locale.ROOT);

        Matcher timeMatcher = TIME_RANGE_PATTERN.matcher(section);
        boolean hasTimeRange = timeMatcher.find();
        LocalTime start;
        LocalTime end;
        boolean overnight;

        if (hasTimeRange) {
            start = parseTime(timeMatcher.group(1), timeMatcher.group(2));
            end = parseTime(timeMatcher.group(3), timeMatcher.group(4));
            overnight = start.isAfter(end);
        } else {
            start = FULL_DAY_START;
            end = FULL_DAY_END;
            overnight = false;
        }

        boolean isFree = normalized.contains("free");
        boolean isPerEntry = normalized.contains("per entry");
        boolean isFlat = normalized.contains("flat");
        boolean isThirtyMinute = normalized.contains("/30 min") || normalized.contains("/30min");
        boolean isHourly = normalized.contains("/hour") || normalized.contains("/hr");

        Matcher amountMatcher = DOLLAR_AMOUNT_PATTERN.matcher(section);
        BigDecimal amount = amountMatcher.find() ? new BigDecimal(amountMatcher.group(1)) : null;

        BigDecimal ratePerHour = null;
        if (isFree && amount == null) {
            ratePerHour = BigDecimal.ZERO;
        } else if (amount != null) {
            if (isThirtyMinute) {
                ratePerHour = amount.multiply(BigDecimal.valueOf(2)).setScale(2, RoundingMode.HALF_UP);
            } else if (isHourly) {
                ratePerHour = amount.setScale(2, RoundingMode.HALF_UP);
            } else if (isPerEntry) {
                ratePerHour = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            } else if (isFlat) {
                ratePerHour = amount.setScale(2, RoundingMode.HALF_UP);
            }
        }

        if (ratePerHour == null) {
            if (isFree && amount == null) {
                return PricingBand.free(start, end);
            }
            return PricingBand.unknown();
        }

        return new PricingBand(start, end, ratePerHour, overnight, isFlat);
    }

    private LocalTime parseTime(String rawValue, String meridiem) {
        String[] parts = rawValue.split("\\.");
        int hour = Integer.parseInt(parts[0]);
        int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

        String meridiemLower = meridiem.toLowerCase(Locale.ROOT);
        if ("pm".equals(meridiemLower) && hour != 12) {
            hour += 12;
        }
        if ("am".equals(meridiemLower) && hour == 12) {
            hour = 0;
        }

        return LocalTime.of(hour, minute);
    }
}
