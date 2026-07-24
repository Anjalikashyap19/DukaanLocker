package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.BusinessProfileRequest;
import com.shoplocker.fssai.dto.BusinessProfileResponse;
import com.shoplocker.fssai.service.BusinessProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/business-profile")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Business Profile", description = "Business profile management — create and retrieve wizard data")
public class BusinessProfileController {

    private final BusinessProfileService businessProfileService;

    public BusinessProfileController(BusinessProfileService businessProfileService) {
        this.businessProfileService = businessProfileService;
    }

    @Operation(
            summary = "Create or update business profile",
            description = "Saves the wizard screen data. If a profile already exists for the user, it will be updated."
    )
    @PostMapping
    public ResponseEntity<BusinessProfileResponse> createOrUpdateProfile(
            @Valid @RequestBody BusinessProfileRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        BusinessProfileResponse response = businessProfileService.createOrUpdateProfile(request, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Get my business profile",
            description = "Returns the business profile for the authenticated user."
    )
    @GetMapping
    public ResponseEntity<BusinessProfileResponse> getMyProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        BusinessProfileResponse response = businessProfileService.getProfile(email);
        return ResponseEntity.ok(response);
    }
}
