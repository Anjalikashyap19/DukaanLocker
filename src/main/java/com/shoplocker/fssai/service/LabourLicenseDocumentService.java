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

    private final LabourLicenseDocumentRepository repository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public LabourLicenseDocumentService(LabourLicenseDocumentRepository repository,
                                        ShopService shopService,
                                        S3Service s3Service,
                                        TextractService textractService,
                                        DocumentValidationService documentValidationService) {
        this.repository = repository;
        this.shopService = shopService;
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.documentValidationService = documentValidationService;
    }

    public void uploadLabourLicense(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Labour License / Workmen Compensation Policy");

        // Extract text via AWS Textract and validate document content
        String extractedText = textractService.extractText(file);
        documentValidationService.validate(DocumentType.LABOUR_LICENSE, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<LabourLicenseDocument> existing = repository.findByShop(shop);

        LabourLicenseDocument doc;
        if (existing.isPresent()) {
            doc = existing.get();
        } else {
            doc = new LabourLicenseDocument();
            doc.setShop(shop);
        }

        String fileKey = "labour-license/shop_" + shopId + "/labour_license.pdf";
        String fileUrl = s3Service.uploadFile(file, fileKey);

        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setUploadedFileName(fileKey);
        doc.setFileUrl(fileUrl);
        doc.setUploadedAt(LocalDateTime.now());

        repository.save(doc);
    }
}
