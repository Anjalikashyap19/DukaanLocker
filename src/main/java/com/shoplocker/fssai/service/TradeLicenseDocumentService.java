package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.TradeLicenseDocument;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.repository.TradeLicenseDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TradeLicenseDocumentService {

    private final TradeLicenseDocumentRepository repository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public TradeLicenseDocumentService(TradeLicenseDocumentRepository repository,
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

    public void uploadTradeLicense(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Trade License");

        // Extract text via AWS Textract and validate document content
        String extractedText = textractService.extractText(file);
        documentValidationService.validate(DocumentType.TRADE_LICENSE, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<TradeLicenseDocument> existing = repository.findByShop(shop);

        TradeLicenseDocument doc;
        if (existing.isPresent()) {
            doc = existing.get();
        } else {
            doc = new TradeLicenseDocument();
            doc.setShop(shop);
        }

        String fileKey = "trade-license/shop_" + shopId + "/trade_license.pdf";
        String fileUrl = s3Service.uploadFile(file, fileKey);

        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setUploadedFileName(fileKey);
        doc.setFileUrl(fileUrl);
        doc.setUploadedAt(LocalDateTime.now());

        repository.save(doc);
    }
}
