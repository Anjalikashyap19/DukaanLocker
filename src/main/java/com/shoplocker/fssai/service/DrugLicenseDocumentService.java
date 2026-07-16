package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.DrugLicenseDocument;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.repository.DrugLicenseDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class DrugLicenseDocumentService {

    private final DrugLicenseDocumentRepository drugLicenseDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public DrugLicenseDocumentService(DrugLicenseDocumentRepository repository,
                                      ShopService shopService,
                                      S3Service s3Service,
                                      TextractService textractService,
                                      DocumentValidationService documentValidationService) {
        this.drugLicenseDocumentRepository = repository;
        this.shopService = shopService;
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.documentValidationService = documentValidationService;
    }

    public void uploadDrugLicense(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Drug License");
        byte[] fileBytes = documentValidationService.readBytes(file);
        documentValidationService.assertPdfMagicBytes(fileBytes, "Drug License");

        String extractedText = textractService.extractText(fileBytes, file.getOriginalFilename());
        documentValidationService.validate(DocumentType.DRUG_LICENSE, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<DrugLicenseDocument> existing = drugLicenseDocumentRepository.findByShop(shop);
        DrugLicenseDocument document = existing.orElseGet(() -> {
            DrugLicenseDocument d = new DrugLicenseDocument();
            d.setShop(shop);
            return d;
        });

        String fileKey = getFileKey(shopId);
        String fileUrl = s3Service.uploadFile(fileBytes, file.getContentType(), fileKey);

        document.setOriginalFileName(file.getOriginalFilename());
        document.setUploadedFileName(fileKey);
        document.setFileUrl(fileUrl);
        document.setUploadedAt(LocalDateTime.now());

        drugLicenseDocumentRepository.save(document);
    }

    private String getFileKey(Long shopId) {
        return "drug-license/shop_" + shopId + "/drug_license.pdf";
    }
}
