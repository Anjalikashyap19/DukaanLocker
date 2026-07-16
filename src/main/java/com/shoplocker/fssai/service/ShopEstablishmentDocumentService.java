package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.entity.ShopEstablishmentDocument;
import com.shoplocker.fssai.repository.ShopEstablishmentDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ShopEstablishmentDocumentService {

    private final ShopEstablishmentDocumentRepository shopEstablishmentDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public ShopEstablishmentDocumentService(ShopEstablishmentDocumentRepository repository,
                                            ShopService shopService,
                                            S3Service s3Service,
                                            TextractService textractService,
                                            DocumentValidationService documentValidationService) {
        this.shopEstablishmentDocumentRepository = repository;
        this.shopService = shopService;
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.documentValidationService = documentValidationService;
    }

    public void uploadShopEstablishment(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Shop & Establishment License");
        byte[] fileBytes = documentValidationService.readBytes(file);
        documentValidationService.assertPdfMagicBytes(fileBytes, "Shop & Establishment License");

        String extractedText = textractService.extractText(fileBytes, file.getOriginalFilename());
        documentValidationService.validate(DocumentType.SHOP_ESTABLISHMENT, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<ShopEstablishmentDocument> existing = shopEstablishmentDocumentRepository.findByShop(shop);
        ShopEstablishmentDocument document = existing.orElseGet(() -> {
            ShopEstablishmentDocument d = new ShopEstablishmentDocument();
            d.setShop(shop);
            return d;
        });

        String fileKey = getFileKey(shopId);
        String fileUrl = s3Service.uploadFile(fileBytes, file.getContentType(), fileKey);

        document.setOriginalFileName(file.getOriginalFilename());
        document.setUploadedFileName(fileKey);
        document.setFileUrl(fileUrl);
        document.setUploadedAt(LocalDateTime.now());

        shopEstablishmentDocumentRepository.save(document);
    }

    private String getFileKey(Long shopId) {
        return "shop-establishment/shop_" + shopId + "/shop_establishment_license.pdf";
    }
}
