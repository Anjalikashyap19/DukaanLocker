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

        documentValidationService.validateFileFormat(file, "GST Registration Certificate");

        // Extract text via AWS Textract and validate document content
        String extractedText = textractService.extractText(file);
        documentValidationService.validate(DocumentType.GST, extractedText, file.getOriginalFilename());

        // Get shop
        Shop shop = shopService.getShopById(shopId);

        // Check if GST already exists
        Optional<GSTDocument> existingDocument = gstDocumentRepository.findByShop(shop);

        GSTDocument gstDocument;

        if (existingDocument.isPresent()) {
            gstDocument = existingDocument.get();   // UPDATE existing row
        } else {
            gstDocument = new GSTDocument();        // CREATE new row
            gstDocument.setShop(shop);
        }

        // Fixed S3 key (same for every upload of same shop)
        String fileKey = generateGSTFileName(shopId);

        // Upload to S3 (same key => overwrite)
        String fileUrl = s3Service.uploadFile(file, fileKey);

        // Update metadata
        gstDocument.setOriginalFileName(file.getOriginalFilename());
        gstDocument.setUploadedFileName(fileKey);
        gstDocument.setFileUrl(fileUrl);
        gstDocument.setUploadedAt(LocalDateTime.now());

        // Save
        gstDocumentRepository.save(gstDocument);
    }

    private String generateGSTFileName(Long shopId) {

        return "gst/shop_" + shopId + "/gst_certificate.pdf";
    }
}
