package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.FireSafetyDocument;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.repository.FireSafetyDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class FireSafetyDocumentService {

    private final FireSafetyDocumentRepository repository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public FireSafetyDocumentService(FireSafetyDocumentRepository repository,
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

    public void uploadFireSafety(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Fire Safety Certificate");

        // Extract text via AWS Textract and validate document content
        String extractedText = textractService.extractText(file);
        documentValidationService.validate(DocumentType.FIRE_SAFETY, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<FireSafetyDocument> existing = repository.findByShop(shop);

        FireSafetyDocument doc;
        if (existing.isPresent()) {
            doc = existing.get();
        } else {
            doc = new FireSafetyDocument();
            doc.setShop(shop);
        }

        String fileKey = "fire-safety/shop_" + shopId + "/fire_safety.pdf";
        String fileUrl = s3Service.uploadFile(file, fileKey);

        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setUploadedFileName(fileKey);
        doc.setFileUrl(fileUrl);
        doc.setUploadedAt(LocalDateTime.now());

        repository.save(doc);
    }
}
