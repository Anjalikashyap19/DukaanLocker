package com.shoplocker.fssai.service;

import com.shoplocker.fssai.dto.FssaiRequest;
import com.shoplocker.fssai.dto.FssaiResponse;
import com.shoplocker.fssai.entity.FssaiLicense;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.FssaiLicenseRepository;
import com.shoplocker.fssai.scraper.FssaiScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class FssaiService {

    private static final Logger log = LoggerFactory.getLogger(FssaiService.class);

    private final FssaiLicenseRepository repository;
    private final FssaiScraper scraper;

    public FssaiService(FssaiLicenseRepository repository, FssaiScraper scraper) {
        this.repository = repository;
        this.scraper = scraper;
    }

    @Transactional
    public FssaiResponse fetchAndSaveLicense(FssaiRequest request) {
        String licenseNumber = request.getLicenseNumber().trim();

        Optional<FssaiLicense> existing = repository.findByLicenseNumber(licenseNumber);
        if (existing.isPresent()) {
            log.info("License {} found in database, returning cached result", licenseNumber);
            FssaiLicense lic = existing.get();
            return mapToResponse(lic);
        }

        log.info("License {} not in database, scraping from portal", licenseNumber);
        FssaiResponse scraped = scraper.fetchLicenseData(licenseNumber);

        if (!scraped.isSuccess()) {
            throw new FssaiException("Failed to fetch data from FSSAI portal");
        }

        FssaiLicense entity = FssaiLicense.builder()
                .licenseNumber(licenseNumber)
                .businessName(scraped.getBusinessName())
                .address(scraped.getAddress())
                .status(scraped.getStatus())
                .validityDate(scraped.getValidityDate())
                .licenseType(scraped.getLicenseType())
                .pdfPath(scraped.getPdfPath())
                .build();

        repository.save(entity);
        log.info("License {} saved to database", licenseNumber);

        return scraped;
    }

    @Transactional(readOnly = true)
    public FssaiResponse getLicense(String licenseNumber) {
        FssaiLicense lic = repository.findByLicenseNumber(licenseNumber)
                .orElseThrow(() -> new FssaiException("License not found: " + licenseNumber));
        return mapToResponse(lic);
    }

    private FssaiResponse mapToResponse(FssaiLicense lic) {
        return FssaiResponse.builder()
                .success(true)
                .licenseNumber(lic.getLicenseNumber())
                .businessName(lic.getBusinessName())
                .address(lic.getAddress())
                .status(lic.getStatus())
                .validityDate(lic.getValidityDate())
                .licenseType(lic.getLicenseType())
                .pdfPath(lic.getPdfPath())
                .build();
    }
}
