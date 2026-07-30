package com.shoplocker.fssai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuration for the Ola Maps API integration.
 *
 * <p>The API key is read from the environment variable {@code OLA_MAPS_API_KEY}
 * (or system property). It is never hardcoded or logged.</p>
 *
 * <p>A {@link RestClient} bean is created with the Ola Maps base URL pre-configured
 * so the {@link com.shoplocker.fssai.service.LocationSearchService} can make
 * requests without constructing full URLs.</p>
 */
@Configuration
public class OlaMapsConfig {

    @Value("${ola-maps.api-key:${OLA_MAPS_API_KEY:}}")
    private String apiKey;

    @Value("${ola-maps.base-url:https://api.olamaps.io}")
    private String baseUrl;

    @Bean
    public RestClient olaMapsRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
