package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.ShopEstablishmentDocument;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.repository.ShopEstablishmentDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ShopEstablishmentDocumentService {

    private final ShopEstablishmentDocumentRepository repository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public ShopEstablishmentDocumentService(ShopEstablishmentDocumentRepository repository,
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

    public void uploadShopEstablishment(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Shop & Establishment License");

        // Extract text via AWS Textract and validate document content
        String extractedText = textractService.extractText(file);
        documentValidationService.validate(DocumentType.SHOP_ESTABLISHMENT, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<ShopEstablishmentDocument> existing = repository.findByShop(shop);

        ShopEstablishmentDocument doc;
        if (existing.isPresent()) {
            doc = existing.get();
        } else {
            doc = new ShopEstablishmentDocument();
            doc.setShop(shop);
        }

        String fileKey = "shop-establishment/shop_" + shopId + "/shop_establishment.pdf";
        String fileUrl = s3Service.uploadFile(file, fileKey);

        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setUploadedFileName(fileKey);
        doc.setFileUrl(fileUrl);
        doc.setUploadedAt(LocalDateTime.now());

        repository.save(doc);
    }
}
