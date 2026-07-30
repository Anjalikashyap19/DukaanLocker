package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.entity.ShopInsuranceDocument;
import com.shoplocker.fssai.repository.ShopInsuranceDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ShopInsuranceDocumentService {

    private final ShopInsuranceDocumentRepository shopInsuranceDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public ShopInsuranceDocumentService(ShopInsuranceDocumentRepository repository,
                                        ShopService shopService,
                                        S3Service s3Service,
                                        TextractService textractService,
                                        DocumentValidationService documentValidationService) {
        this.shopInsuranceDocumentRepository = repository;
        this.shopService = shopService;
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.documentValidationService = documentValidationService;
    }

    public void uploadShopInsurance(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Shop Insurance");
        byte[] fileBytes = documentValidationService.readBytes(file);
        documentValidationService.assertPdfMagicBytes(fileBytes, "Shop Insurance");

        String extractedText = textractService.extractText(fileBytes, file.getOriginalFilename());
        documentValidationService.validate(DocumentType.SHOP_INSURANCE, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<ShopInsuranceDocument> existing = shopInsuranceDocumentRepository.findByShop(shop);
        ShopInsuranceDocument document = existing.orElseGet(() -> {
            ShopInsuranceDocument d = new ShopInsuranceDocument();
            d.setShop(shop);
            return d;
        });

        String fileKey = getFileKey(shopId);
        String fileUrl = s3Service.uploadFile(fileBytes, file.getContentType(), fileKey);

        document.setOriginalFileName(file.getOriginalFilename());
        document.setUploadedFileName(fileKey);
        document.setFileUrl(fileUrl);
        document.setUploadedAt(LocalDateTime.now());

        shopInsuranceDocumentRepository.save(document);
    }

    private String getFileKey(Long shopId) {
        return "shop-insurance/shop_" + shopId + "/shop_insurance.pdf";
    }
}
