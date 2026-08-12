package com.sc2006group5.car_one_stop.service.pricing;

import com.sc2006group5.car_one_stop.model.LtaRateRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RateParser {

    private static final Logger log = LoggerFactory.getLogger(RateParser.class);
    private static final Pattern DOLLAR_AMOUNT_PATTERN = Pattern.compile("\\$(\\d+(?:\\.\\d{1,2})?)");
    private static final BigDecimal PER_ENTRY_EQUIVALENT_HOURLY = new BigDecimal("2.00");
    private static final BigDecimal FLAT_EQUIVALENT_HOURLY = new BigDecimal("1.00");

    public BigDecimal computeWeekdayHourlyRate(LtaRateRecord record) {
        try {
            if (record == null) {
                return null;
            }

            BigDecimal primary = parseRateField(record.getWeekdaysRate1());
            if (primary != null) {
                return primary;
            }

            return parseRateField(record.getWeekdaysRate2());
        } catch (Exception ex) {
            log.warn("Failed to compute weekday hourly rate for '{}': {}",
                    record == null ? "null-record" : record.getCarPark(),
                    ex.getMessage());
            return null;
        }
    }

    private BigDecimal parseRateField(String rawField) {
        try {
            if (rawField == null || rawField.isBlank()) {
                return null;
            }

            String normalized = rawField.toLowerCase(Locale.ROOT);

            if (normalized.contains("free")) {
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            if (normalized.contains("per entry")) {
                return PER_ENTRY_EQUIVALENT_HOURLY;
            }
            if (normalized.contains("flat")) {
                return FLAT_EQUIVALENT_HOURLY;
            }

            Matcher matcher = DOLLAR_AMOUNT_PATTERN.matcher(rawField);
            if (!matcher.find()) {
                return null;
            }

            BigDecimal amount = new BigDecimal(matcher.group(1));
            if (normalized.contains("/30 min") || normalized.contains("/30min")) {
                return amount.multiply(BigDecimal.valueOf(2)).setScale(2, RoundingMode.HALF_UP);
            }

            return null;
        } catch (Exception ex) {
            log.warn("Unable to parse rate field '{}': {}", rawField, ex.getMessage());
            return null;
        }
    }
}
