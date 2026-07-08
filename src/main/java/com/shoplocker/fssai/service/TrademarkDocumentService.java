package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.TrademarkDocument;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.repository.TrademarkDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TrademarkDocumentService {

    private final TrademarkDocumentRepository repository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public TrademarkDocumentService(TrademarkDocumentRepository repository,
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

    public void uploadTrademark(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Trademark");

        // Extract text via AWS Textract and validate document content
        String extractedText = textractService.extractText(file);
        documentValidationService.validate(DocumentType.TRADEMARK, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<TrademarkDocument> existing = repository.findByShop(shop);

        TrademarkDocument doc;
        if (existing.isPresent()) {
            doc = existing.get();
        } else {
            doc = new TrademarkDocument();
            doc.setShop(shop);
        }

        String fileKey = "trademark/shop_" + shopId + "/trademark.pdf";
        String fileUrl = s3Service.uploadFile(file, fileKey);

        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setUploadedFileName(fileKey);
        doc.setFileUrl(fileUrl);
        doc.setUploadedAt(LocalDateTime.now());

        repository.save(doc);
    }
}
