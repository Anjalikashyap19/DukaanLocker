package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.PANDocument;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.repository.PANDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PANDocumentService {

    private final PANDocumentRepository panDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public PANDocumentService(PANDocumentRepository panDocumentRepository,
                              ShopService shopService,
                              S3Service s3Service,
                              TextractService textractService,
                              DocumentValidationService documentValidationService) {
        this.panDocumentRepository = panDocumentRepository;
        this.shopService = shopService;
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.documentValidationService = documentValidationService;
    }

    public void uploadPAN(Long shopId, MultipartFile file) {
        // 1. Cheap metadata validation (no IO).
        documentValidationService.validateFileFormat(file, "PAN/TAN");

        // 2. Read the file into memory ONCE. This is the only point we touch
        //    the underlying multipart source — everything downstream reuses
        //    the same byte[].
        byte[] fileBytes = documentValidationService.readBytes(file);

        // 3. Confirm the bytes really start with %PDF (operates on the
        //    already-loaded byte[] — zero extra IO cost).
        documentValidationService.assertPdfMagicBytes(fileBytes, "PAN/TAN");

        // 4. OCR directly from the byte[] (no S3 round-trip).
        String extractedText = textractService.extractText(fileBytes, file.getOriginalFilename());

        // 5. Content validation — throws DOCUMENT_VALIDATION_FAILED or
        //    DOCUMENT_TYPE_MISMATCH on failure. Nothing past this point runs
        //    if validation fails.
        documentValidationService.validate(DocumentType.PAN, extractedText, file.getOriginalFilename());

        // 6. Only after every check passed: upload the same byte[] to S3.
        Shop shop = shopService.getShopById(shopId);
        Optional<PANDocument> existingDocument = panDocumentRepository.findByShop(shop);

        PANDocument panDocument;
        if (existingDocument.isPresent()) {
            panDocument = existingDocument.get();
        } else {
            panDocument = new PANDocument();
            panDocument.setShop(shop);
        }

        String fileKey = getFileKey(shopId);
        String fileUrl = s3Service.uploadFile(fileBytes, file.getContentType(), fileKey);

        panDocument.setOriginalFileName(file.getOriginalFilename());
        panDocument.setUploadedFileName(fileKey);
        panDocument.setFileUrl(fileUrl);
        panDocument.setUploadedAt(LocalDateTime.now());

        panDocumentRepository.save(panDocument);
    }

    private String getFileKey(Long shopId) {
        return "pan/shop_" + shopId + "/pan_card.pdf";
    }
}
