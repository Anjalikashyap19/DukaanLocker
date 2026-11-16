package com.shoplocker.fssai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplocker.fssai.dto.DrugLicenseVerificationResponse;
import com.shoplocker.fssai.entity.Document;
import com.shoplocker.fssai.entity.DocumentStatus;
import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.exception.FssaiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class DrugLicenseVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DrugLicenseVerificationService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${DRUG_LICENSE_API_BASE_URL}")
    private String drugLicenseApiBaseUrl;

    @PostConstruct
    public void init() {
        // RestTemplate is ready to use
    }

    /**
     * Verify a drug license number by calling the State Drugs Department API.
     *
     * @param licenseNumber the drug license number (e.g., RLF20AP2026007296)
     * @return DrugLicenseVerificationResponse with license details or error
     */
    public DrugLicenseVerificationResponse verifyLicense(String licenseNumber) {
        if (licenseNumber == null || licenseNumber.trim().isEmpty()) {
            return DrugLicenseVerificationResponse.error("Drug license number is required");
        }

        String normalizedLicense = licenseNumber.trim();
        String url = drugLicenseApiBaseUrl + normalizedLicense;

        log.info("Calling drug license verification API: {}", url);

        try {
            DrugLicenseVerificationResponse response = restTemplate.getForObject(url, DrugLicenseVerificationResponse.class);
            if (response != null && response.isSuccess()) {
                return response;
            }
            return DrugLicenseVerificationResponse.error(
                    "Drug license not found or API returned error for: " + normalizedLicense);
        } catch (Exception e) {
            log.error("Error calling drug license API for {}: {}", normalizedLicense, e.getMessage());
            return DrugLicenseVerificationResponse.error(
                    "Unable to connect to drug license verification service");
        }
    }

    /**
     * Enhance the API response with additional processing if needed.
     */
    private DrugLicenseVerificationResponse enhanceResponse(DrugLicenseVerificationResponse response, String licenseNumber) {
        if (response.isSuccess() && response.getLicenseNumber() != null) {
            // Additional parsing can be done here if needed
        }
        return response;
    }
}