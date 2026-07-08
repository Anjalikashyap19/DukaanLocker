package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.MSMEDocument;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.repository.MSMEDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class MSMEDocumentService {

    private final MSMEDocumentRepository repository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public MSMEDocumentService(MSMEDocumentRepository repository,
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

    public void uploadMSME(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Udyam MSME Registration");

        // Extract text via AWS Textract and validate document content
        String extractedText = textractService.extractText(file);
        documentValidationService.validate(DocumentType.MSME, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<MSMEDocument> existing = repository.findByShop(shop);

        MSMEDocument doc;
        if (existing.isPresent()) {
            doc = existing.get();
        } else {
            doc = new MSMEDocument();
            doc.setShop(shop);
        }

        String fileKey = "msme/shop_" + shopId + "/msme_certificate.pdf";
        String fileUrl = s3Service.uploadFile(file, fileKey);

        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setUploadedFileName(fileKey);
        doc.setFileUrl(fileUrl);
        doc.setUploadedAt(LocalDateTime.now());

        repository.save(doc);
    }
}
