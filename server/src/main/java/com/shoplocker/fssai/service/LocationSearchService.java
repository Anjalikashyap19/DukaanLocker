package com.shoplocker.fssai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplocker.fssai.config.OlaMapsConfig;
import com.shoplocker.fssai.dto.LocationSearchResponse;
import com.shoplocker.fssai.dto.LocationSuggestion;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service that proxies location autocomplete requests to the Ola Maps API.
 *
 * <p>The Ola Maps API key is read from the environment (never logged or exposed).
 * The raw third-party response is mapped to {@link LocationSuggestion} DTOs
 * so the frontend never sees internal Ola Maps response structures.</p>
 *
 * <h3>API Reference</h3>
 * <ul>
 *   <li>Endpoint: {@code GET /places/v1/autocomplete}</li>
 *   <li>Auth: {@code api_key} query parameter</li>
 *   <li>Docs: <a href="https://maps.olakrutrim.com/docs/places-apis/autocomplete-api">Ola Maps Autocomplete</a></li>
 * </ul>
 */
@Service
public class LocationSearchService {

    private static final Logger log = LoggerFactory.getLogger(LocationSearchService.class);

    private final RestClient restClient;
    private final OlaMapsConfig olaMapsConfig;
    private final ObjectMapper objectMapper;

    public LocationSearchService(RestClient olaMapsRestClient,
                                  OlaMapsConfig olaMapsConfig,
                                  ObjectMapper objectMapper) {
        this.restClient = olaMapsRestClient;
        this.olaMapsConfig = olaMapsConfig;
        this.objectMapper = objectMapper;
    }

    /**
     * Search for location suggestions using the Ola Maps Autocomplete API.
     *
     * @param query the user-typed location keyword (e.g. "MG Road Bangalore")
     * @return clean response with list of location suggestions
     * @throws FssaiException if the API key is missing, the API call fails, or the response cannot be parsed
     */
    public LocationSearchResponse searchLocations(String query) {
        // Validate API key
        String apiKey = olaMapsConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.error("OLA_MAPS_API_KEY is not configured. Cannot serve location search requests.");
            throw new FssaiException(
                    "Location search service is not configured. Please set the OLA_MAPS_API_KEY environment variable.",
                    FailureCode.OLA_MAPS_API_FAILURE);
        }

        log.debug("Calling Ola Maps Autocomplete API with query: {}", query);

        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/places/v1/autocomplete")
                            .queryParam("input", query)
                            .queryParam("api_key", apiKey)
                            .build())
                    .retrieve()
                    .body(String.class);

            if (body == null) {
                log.warn("Ola Maps API returned null body for query: {}", query);
                throw new FssaiException(
                        "Location search service returned an unexpected response. Please try again later.",
                        FailureCode.OLA_MAPS_API_FAILURE);
            }

            return parseOlaMapsResponse(query, body);

        } catch (FssaiException ex) {
            // Re-throw our own exceptions as-is
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to call Ola Maps Autocomplete API: {}", ex.getMessage(), ex);
            throw new FssaiException(
                    "Unable to reach the location search service. Please try again later.",
                    FailureCode.OLA_MAPS_API_FAILURE, ex);
        }
    }

    /**
     * Parse the raw JSON response from Ola Maps and map it to clean DTOs.
     *
     * <p>Ola Maps response shape (approximate):</p>
     * <pre>{@code
     * {
     *   "status": "ok",
     *   "predictions": [
     *     {
     *       "description": "MG Road, Bengaluru, Karnataka, India",
     *       "place_id": "...",
     *       "terms": [...],
     *       "structured_formatting": {
     *         "main_text": "MG Road",
     *         "secondary_text": "Bengaluru, Karnataka, India"
     *       },
     *       "geometry": {
     *         "location": { "lat": 12.9752, "lng": 77.6084 }
     *       }
     *     }
     *   ]
     * }
     * }</pre>
     */
    private LocationSearchResponse parseOlaMapsResponse(String query, String body) {
        try {
            JsonNode root = objectMapper.readTree(body);

            // Check if the API returned an error status
            JsonNode statusNode = root.get("status");
            if (statusNode != null && !"ok".equalsIgnoreCase(statusNode.asText())) {
                String errorMsg = root.has("error_message") ? root.get("error_message").asText() : "Unknown Ola Maps error";
                log.warn("Ola Maps API error status: {} message: {}", statusNode.asText(), errorMsg);
                throw new FssaiException(
                        "Location search service returned an error: " + errorMsg,
                        FailureCode.OLA_MAPS_API_FAILURE);
            }

            // Extract predictions array
            JsonNode predictionsNode = root.get("predictions");
            if (predictionsNode == null || !predictionsNode.isArray()) {
                log.warn("Ola Maps response missing 'predictions' array. Response: {}", body);
                return new LocationSearchResponse(query, Collections.emptyList());
            }

            List<LocationSuggestion> suggestions = new ArrayList<>();
            for (JsonNode prediction : predictionsNode) {
                LocationSuggestion suggestion = mapPredictionToSuggestion(prediction);
                if (suggestion != null) {
                    suggestions.add(suggestion);
                }
            }

            log.debug("Ola Maps returned {} suggestions for query '{}'", suggestions.size(), query);
            return new LocationSearchResponse(query, suggestions);

        } catch (FssaiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to parse Ola Maps response: {}", ex.getMessage(), ex);
            throw new FssaiException(
                    "Failed to parse location search results. Please try again later.",
                    FailureCode.OLA_MAPS_API_FAILURE, ex);
        }
    }

    /**
     * Map a single Ola Maps prediction object to a clean {@link LocationSuggestion}.
     */
    private LocationSuggestion mapPredictionToSuggestion(JsonNode prediction) {
        String displayName = prediction.has("description")
                ? prediction.get("description").asText()
                : null;

        // Extract geometry/location
        Double latitude = null;
        Double longitude = null;
        JsonNode geometry = prediction.get("geometry");
        if (geometry != null && geometry.has("location")) {
            JsonNode location = geometry.get("location");
            if (location.has("lat")) latitude = location.get("lat").asDouble();
            if (location.has("lng")) longitude = location.get("lng").asDouble();
        }

        // Extract city, state, country, pincode from address_components if available
        String city = null;
        String state = null;
        String country = null;
        String pincode = null;
        JsonNode addressComponents = prediction.get("address_components");
        if (addressComponents != null && addressComponents.isArray()) {
            for (JsonNode component : addressComponents) {
                JsonNode types = component.get("types");
                if (types != null && types.isArray()) {
                    String value = component.has("long_name") ? component.get("long_name").asText() : null;
                    for (JsonNode type : types) {
                        String typeStr = type.asText();
                        switch (typeStr) {
                            case "locality" -> city = value;
                            case "administrative_area_level_1" -> state = value;
                            case "country" -> country = value;
                            case "postal_code" -> pincode = value;
                        }
                    }
                }
            }
        }

        String fullAddress = displayName;

        return new LocationSuggestion(
                displayName,
                fullAddress,
                latitude,
                longitude,
                city,
                state,
                country,
                pincode
        );
    }
}
