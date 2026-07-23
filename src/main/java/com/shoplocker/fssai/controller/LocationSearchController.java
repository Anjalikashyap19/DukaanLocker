package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.LocationSearchResponse;
import com.shoplocker.fssai.service.LocationSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for location autocomplete / search.
 *
 * <p>Proxies requests to the Ola Maps Autocomplete API so the API key
 * stays server-side. Returns a clean {@link LocationSearchResponse}
 * instead of the raw third-party payload.</p>
 *
 * <p>This endpoint is <b>public</b> (no JWT required) so the frontend can
 * call it during the shop-creation wizard before the user has logged in.</p>
 */
@RestController
@RequestMapping("/api/location")
@Tag(name = "Location Search", description = "Location autocomplete powered by Ola Maps")
public class LocationSearchController {

    private final LocationSearchService locationSearchService;

    public LocationSearchController(LocationSearchService locationSearchService) {
        this.locationSearchService = locationSearchService;
    }

    /**
     * Search for location suggestions based on a user-typed keyword.
     *
     * <p>Example: {@code GET /api/location/search?query=MG Road Bangalore}</p>
     *
     * @param query the location keyword typed by the user (minimum 2 characters recommended)
     * @return a list of matching location suggestions
     */
    @Operation(
            summary = "Search locations",
            description = "Returns location autocomplete suggestions from Ola Maps based on the provided keyword. "
                    + "Use this to power location search in forms and wizards.",
            security = {}  // No JWT required — this is a public endpoint
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully returned location suggestions"),
            @ApiResponse(responseCode = "400", description = "Missing or empty query parameter"),
            @ApiResponse(responseCode = "502", description = "Ola Maps API is unreachable or returned an error")
    })
    @GetMapping("/search")
    public ResponseEntity<LocationSearchResponse> searchLocations(
            @Parameter(description = "Location keyword to search for (e.g. \"MG Road Bangalore\")", required = true, example = "MG Road Bangalore")
            @RequestParam(name = "query") String query) {

        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        LocationSearchResponse response = locationSearchService.searchLocations(query.trim());
        return ResponseEntity.ok(response);
    }
}
