package com.shoplocker.fssai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single location suggestion from Ola Maps autocomplete.
 * Exposes only the fields the frontend needs — raw third-party response is never leaked.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A single location suggestion from the autocomplete service")
public class LocationSuggestion {

    @Schema(description = "Display name of the place (e.g. \"MG Road, Bengaluru\")", example = "MG Road, Bengaluru, Karnataka, India")
    private String displayName;

    @Schema(description = "Full formatted address", example = "MG Road, Bengaluru, Karnataka 560001, India")
    private String fullAddress;

    @Schema(description = "Latitude of the place", example = "12.9752")
    private Double latitude;

    @Schema(description = "Longitude of the place", example = "77.6084")
    private Double longitude;

    @Schema(description = "City name", example = "Bengaluru")
    private String city;

    @Schema(description = "State name", example = "Karnataka")
    private String state;

    @Schema(description = "Country name", example = "India")
    private String country;

    @Schema(description = "Pincode / postal code", example = "560001")
    private String pincode;

    public LocationSuggestion() {}

    public LocationSuggestion(String displayName, String fullAddress,
                              Double latitude, Double longitude,
                              String city, String state, String country, String pincode) {
        this.displayName = displayName;
        this.fullAddress = fullAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.city = city;
        this.state = state;
        this.country = country;
        this.pincode = pincode;
    }

    // Getters and Setters

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getFullAddress() { return fullAddress; }
    public void setFullAddress(String fullAddress) { this.fullAddress = fullAddress; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
}
