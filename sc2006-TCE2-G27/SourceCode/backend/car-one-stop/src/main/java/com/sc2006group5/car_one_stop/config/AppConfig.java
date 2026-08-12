package com.sc2006group5.car_one_stop.config;

import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableCaching
@EnableScheduling
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        // Apache HttpClient 5 uses a different TLS fingerprint than Java's default
        // HttpURLConnection, which is required to pass WAF checks on some government
        // APIs (e.g. URA DataService at www.ura.gov.sg).
        var factory = new HttpComponentsClientHttpRequestFactory(HttpClients.createDefault());
        return new RestTemplate(factory);
    }

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("hdbCarparkCoords");
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

}
