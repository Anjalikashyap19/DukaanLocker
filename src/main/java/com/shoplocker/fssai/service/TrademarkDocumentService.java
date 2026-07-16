package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.entity.TrademarkDocument;
import com.shoplocker.fssai.repository.TrademarkDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TrademarkDocumentService {

    private final TrademarkDocumentRepository trademarkDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public TrademarkDocumentService(TrademarkDocumentRepository trademarkDocumentRepository,
                                    ShopService shopService,
                                    S3Service s3Service,
                                    TextractService textractService,
                                    DocumentValidationService documentValidationService) {
        this.trademarkDocumentRepository = trademarkDocumentRepository;
        this.shopService = shopService;
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.documentValidationService = documentValidationService;
    }

    public void uploadTrademark(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Trademark Certificate");
        byte[] fileBytes = documentValidationService.readBytes(file);
        documentValidationService.assertPdfMagicBytes(fileBytes, "Trademark Certificate");

        String extractedText = textractService.extractText(fileBytes, file.getOriginalFilename());
        documentValidationService.validate(DocumentType.TRADEMARK, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<TrademarkDocument> existing = trademarkDocumentRepository.findByShop(shop);
        TrademarkDocument document = existing.orElseGet(() -> {
            TrademarkDocument d = new TrademarkDocument();
            d.setShop(shop);
            return d;
        });

        String fileKey = getFileKey(shopId);
        String fileUrl = s3Service.uploadFile(fileBytes, file.getContentType(), fileKey);

        document.setOriginalFileName(file.getOriginalFilename());
        document.setUploadedFileName(fileKey);
        document.setFileUrl(fileUrl);
        document.setUploadedAt(LocalDateTime.now());

        trademarkDocumentRepository.save(document);
    }

    private String getFileKey(Long shopId) {
        return "trademark/shop_" + shopId + "/trademark_certificate.pdf";
    }
}
