package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.GSTDocument;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.repository.GSTDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class GSTDocumentService {

    private final GSTDocumentRepository gstDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public GSTDocumentService(GSTDocumentRepository gstDocumentRepository,
                              ShopService shopService,
                              S3Service s3Service,
                              TextractService textractService,
                              DocumentValidationService documentValidationService) {
        this.gstDocumentRepository = gstDocumentRepository;
        this.shopService = shopService;
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.documentValidationService = documentValidationService;
    }

    public void uploadGST(Long shopId, MultipartFile file) {
        // 1. Cheap metadata validation (no IO).
        documentValidationService.validateFileFormat(file, "GST Registration Certificate");

        // 2. Read the file into memory ONCE — single contact with the source.
        byte[] fileBytes = documentValidationService.readBytes(file);

        // 3. Confirm %PDF magic bytes using the already-loaded byte[].
        documentValidationService.assertPdfMagicBytes(fileBytes, "GST Registration Certificate");

        // 4. OCR directly from byte[] (no S3 round-trip).
        String extractedText = textractService.extractText(fileBytes, file.getOriginalFilename());

        // 5. Content validation — throws on any mismatch.
        documentValidationService.validate(DocumentType.GST, extractedText, file.getOriginalFilename());

        // 6. Only after both validations pass: upload the SAME bytes to S3.
        Shop shop = shopService.getShopById(shopId);
        Optional<GSTDocument> existing = gstDocumentRepository.findByShop(shop);
        GSTDocument document = existing.orElseGet(() -> {
            GSTDocument d = new GSTDocument();
            d.setShop(shop);
            return d;
        });

        String fileKey = getFileKey(shopId);
        String fileUrl = s3Service.uploadFile(fileBytes, file.getContentType(), fileKey);

        document.setOriginalFileName(file.getOriginalFilename());
        document.setUploadedFileName(fileKey);
        document.setFileUrl(fileUrl);
        document.setUploadedAt(LocalDateTime.now());

        gstDocumentRepository.save(document);
    }

    private String getFileKey(Long shopId) {
        return "gst/shop_" + shopId + "/gst_certificate.pdf";
    }
}
