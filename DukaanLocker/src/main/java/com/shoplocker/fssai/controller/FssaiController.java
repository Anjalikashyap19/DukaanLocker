package com.shoplocker.fssai.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shoplocker.fssai.dto.FssaiRequest;
import com.shoplocker.fssai.dto.FssaiResponse;
import com.shoplocker.fssai.service.FssaiService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/fssai")
@Tag(name = "FSSAI License", description = "APIs for fetching and retrieving FSSAI license information")
public class FssaiController {

    private final FssaiService fssaiService;

    public FssaiController(FssaiService fssaiService) {
        this.fssaiService = fssaiService;
    }

    @PostMapping("/fetch")
    @Operation(summary = "Fetch FSSAI license", description = "Scrapes the FSSAI portal for a given license number and saves the data to the database")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "License fetched and saved successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FssaiResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid license number or fetch failed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    public ResponseEntity<FssaiResponse> fetchLicense(@Valid @RequestBody FssaiRequest request) {
        FssaiResponse response = fssaiService.fetchAndSaveLicense(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{licenseNumber}")
    @Operation(summary = "Get FSSAI license", description = "Retrieves a previously fetched FSSAI license from the database")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "License found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FssaiResponse.class))),
        @ApiResponse(responseCode = "400", description = "License not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    public ResponseEntity<FssaiResponse> getLicense(
            @Parameter(description = "14-digit FSSAI license number") @PathVariable String licenseNumber) {
        FssaiResponse response = fssaiService.getLicense(licenseNumber);
        return ResponseEntity.ok(response);
    }
}
