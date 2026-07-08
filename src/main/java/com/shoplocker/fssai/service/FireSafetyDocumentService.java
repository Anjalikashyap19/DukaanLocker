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

    private final FireSafetyDocumentRepository fireSafetyDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public FireSafetyDocumentService(FireSafetyDocumentRepository repository,
                                     ShopService shopService,
                                     S3Service s3Service,
                                     TextractService textractService,
                                     DocumentValidationService documentValidationService) {
        this.fireSafetyDocumentRepository = repository;
        this.shopService = shopService;
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.documentValidationService = documentValidationService;
    }

    public void uploadFireSafety(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Fire Safety Certificate");
        byte[] fileBytes = documentValidationService.readBytes(file);
        documentValidationService.assertPdfMagicBytes(fileBytes, "Fire Safety Certificate");

        String extractedText = textractService.extractText(fileBytes, file.getOriginalFilename());
        documentValidationService.validate(DocumentType.FIRE_SAFETY, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<FireSafetyDocument> existing = fireSafetyDocumentRepository.findByShop(shop);
        FireSafetyDocument document = existing.orElseGet(() -> {
            FireSafetyDocument d = new FireSafetyDocument();
            d.setShop(shop);
            return d;
        });

        String fileKey = getFileKey(shopId);
        String fileUrl = s3Service.uploadFile(fileBytes, file.getContentType(), fileKey);

        document.setOriginalFileName(file.getOriginalFilename());
        document.setUploadedFileName(fileKey);
        document.setFileUrl(fileUrl);
        document.setUploadedAt(LocalDateTime.now());

        fireSafetyDocumentRepository.save(document);
    }

    private String getFileKey(Long shopId) {
        return "fire-safety/shop_" + shopId + "/fire_safety_certificate.pdf";
    }
}
