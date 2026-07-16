package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.LabourLicenseDocument;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.repository.LabourLicenseDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class LabourLicenseDocumentService {

    private final LabourLicenseDocumentRepository labourLicenseDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public LabourLicenseDocumentService(LabourLicenseDocumentRepository repository,
                                        ShopService shopService,
                                        S3Service s3Service,
                                        TextractService textractService,
                                        DocumentValidationService documentValidationService) {
        this.labourLicenseDocumentRepository = repository;
        this.shopService = shopService;
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.documentValidationService = documentValidationService;
    }

    public void uploadLabourLicense(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Labour License / Workmen Compensation Policy");
        byte[] fileBytes = documentValidationService.readBytes(file);
        documentValidationService.assertPdfMagicBytes(fileBytes, "Labour License / Workmen Compensation Policy");

        String extractedText = textractService.extractText(fileBytes, file.getOriginalFilename());
        documentValidationService.validate(DocumentType.LABOUR_LICENSE, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<LabourLicenseDocument> existing = labourLicenseDocumentRepository.findByShop(shop);
        LabourLicenseDocument document = existing.orElseGet(() -> {
            LabourLicenseDocument d = new LabourLicenseDocument();
            d.setShop(shop);
            return d;
        });

        String fileKey = getFileKey(shopId);
        String fileUrl = s3Service.uploadFile(fileBytes, file.getContentType(), fileKey);

        document.setOriginalFileName(file.getOriginalFilename());
        document.setUploadedFileName(fileKey);
        document.setFileUrl(fileUrl);
        document.setUploadedAt(LocalDateTime.now());

        labourLicenseDocumentRepository.save(document);
    }

    private String getFileKey(Long shopId) {
        return "labour-license/shop_" + shopId + "/labour_license.pdf";
    }
}
