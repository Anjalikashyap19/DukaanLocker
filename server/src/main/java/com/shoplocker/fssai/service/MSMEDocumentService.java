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

    private final MSMEDocumentRepository msmeDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public MSMEDocumentService(MSMEDocumentRepository msmeDocumentRepository,
                               ShopService shopService,
                               S3Service s3Service,
                               TextractService textractService,
                               DocumentValidationService documentValidationService) {
        this.msmeDocumentRepository = msmeDocumentRepository;
        this.shopService = shopService;
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.documentValidationService = documentValidationService;
    }

    public void uploadMSME(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Udyam MSME Registration");
        byte[] fileBytes = documentValidationService.readBytes(file);
        documentValidationService.assertPdfMagicBytes(fileBytes, "Udyam MSME Registration");

        String extractedText = textractService.extractText(fileBytes, file.getOriginalFilename());
        documentValidationService.validate(DocumentType.MSME_CERTIFICATE, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<MSMEDocument> existing = msmeDocumentRepository.findByShop(shop);
        MSMEDocument document;
        if (existing.isPresent()) {
            document = existing.get();
        } else {
            document = new MSMEDocument();
            document.setShop(shop);
        }

        String fileKey = getFileKey(shopId);
        String fileUrl = s3Service.uploadFile(fileBytes, file.getContentType(), fileKey);

        document.setOriginalFileName(file.getOriginalFilename());
        document.setUploadedFileName(fileKey);
        document.setFileUrl(fileUrl);
        document.setUploadedAt(LocalDateTime.now());

        msmeDocumentRepository.save(document);
    }

    private String getFileKey(Long shopId) {
        return "msme/shop_" + shopId + "/msme_registration.pdf";
    }
}
