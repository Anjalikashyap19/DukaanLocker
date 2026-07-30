package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.entity.TradeLicenseDocument;
import com.shoplocker.fssai.repository.TradeLicenseDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TradeLicenseDocumentService {

    private final TradeLicenseDocumentRepository tradeLicenseDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public TradeLicenseDocumentService(TradeLicenseDocumentRepository repository,
                                       ShopService shopService,
                                       S3Service s3Service,
                                       TextractService textractService,
                                       DocumentValidationService documentValidationService) {
        this.tradeLicenseDocumentRepository = repository;
        this.shopService = shopService;
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.documentValidationService = documentValidationService;
    }

    public void uploadTradeLicense(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Trade License");
        byte[] fileBytes = documentValidationService.readBytes(file);
        documentValidationService.assertPdfMagicBytes(fileBytes, "Trade License");

        String extractedText = textractService.extractText(fileBytes, file.getOriginalFilename());
        documentValidationService.validate(DocumentType.TRADE_LICENSE, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<TradeLicenseDocument> existing = tradeLicenseDocumentRepository.findByShop(shop);
        TradeLicenseDocument document = existing.orElseGet(() -> {
            TradeLicenseDocument d = new TradeLicenseDocument();
            d.setShop(shop);
            return d;
        });

        String fileKey = getFileKey(shopId);
        String fileUrl = s3Service.uploadFile(fileBytes, file.getContentType(), fileKey);

        document.setOriginalFileName(file.getOriginalFilename());
        document.setUploadedFileName(fileKey);
        document.setFileUrl(fileUrl);
        document.setUploadedAt(LocalDateTime.now());

        tradeLicenseDocumentRepository.save(document);
    }

    private String getFileKey(Long shopId) {
        return "trade-license/shop_" + shopId + "/trade_license.pdf";
    }
}
