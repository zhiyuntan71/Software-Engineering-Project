package com.sc2006group5.car_one_stop.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc2006group5.car_one_stop.model.LtaRateRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
public class LtaRatesClient {

    private static final Logger log = LoggerFactory.getLogger(LtaRatesClient.class);
    private static final int PAGE_SIZE = 500;
    private static final int MAX_RETRIES = 3;
    private static final long RATE_LIMIT_BACKOFF_MS = 15_000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final String ratesUrl;

    public LtaRatesClient(
            RestTemplate restTemplate,
            @Value("${lta.rates.url}") String ratesUrl
    ) {
        this.restTemplate = restTemplate;
        this.ratesUrl = ratesUrl;
    }

    public List<LtaRateRecord> fetchAllRates() {
        List<LtaRateRecord> records = new ArrayList<>();
        int offset = 0;

        while (true) {
            URI uri = UriComponentsBuilder.fromUriString(ratesUrl)
                    .replaceQueryParam("limit", PAGE_SIZE)
                    .replaceQueryParam("offset", offset)
                    .build(true)
                    .toUri();

            boolean pageSuccess = false;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
                    String rawBody = response.getBody();
                    JsonNode body = rawBody == null ? null : OBJECT_MAPPER.readTree(rawBody);
                    JsonNode items = body == null
                            ? null
                            : body.path("result").path("records");

                    if (items == null || !items.isArray() || items.isEmpty()) {
                        return records;
                    }

                    for (JsonNode item : items) {
                        try {
                            records.add(OBJECT_MAPPER.treeToValue(item, LtaRateRecord.class));
                        } catch (Exception parseEx) {
                            log.warn("Skipping malformed LTA rate record at offset {}: {}", offset, parseEx.getMessage());
                        }
                    }

                    if (items.size() < PAGE_SIZE) {
                        return records;
                    }
                    offset += PAGE_SIZE;
                    pageSuccess = true;
                    break;
                } catch (HttpClientErrorException.TooManyRequests ex) {
                    if (attempt < MAX_RETRIES) {
                        log.warn("LTA rates rate-limited at offset {}; retrying in {}s (attempt {}/{})",
                                offset, RATE_LIMIT_BACKOFF_MS / 1000, attempt, MAX_RETRIES);
                        try {
                            Thread.sleep(RATE_LIMIT_BACKOFF_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return records;
                        }
                    } else {
                        log.warn("LTA rates rate-limited at offset {} after {} attempts; giving up", offset, MAX_RETRIES);
                    }
                } catch (Exception ex) {
                    log.warn("Failed to fetch LTA rates at offset {}: {}", offset, ex.getMessage());
                    break;
                }
            }
            if (!pageSuccess) {
                break;
            }
        }

        return records;
    }
}
