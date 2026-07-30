package com.shoplocker.fssai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Wrapper response for the location autocomplete endpoint.
 * Returns a list of {@link LocationSuggestion} objects.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response containing location suggestions from Ola Maps")
public class LocationSearchResponse {

    @Schema(description = "The search query that was used", example = "MG Road Bangalore")
    private String query;

    @Schema(description = "List of matching location suggestions")
    private List<LocationSuggestion> suggestions;

    public LocationSearchResponse() {}

    public LocationSearchResponse(String query, List<LocationSuggestion> suggestions) {
        this.query = query;
        this.suggestions = suggestions;
    }

    // Getters and Setters

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public List<LocationSuggestion> getSuggestions() { return suggestions; }
    public void setSuggestions(List<LocationSuggestion> suggestions) { this.suggestions = suggestions; }
}
