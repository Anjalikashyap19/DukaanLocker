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
        documentValidationService.validateFileFormat(file, "PAN/TAN");

        // Extract text via AWS Textract and validate document content
        String extractedText = textractService.extractText(file);
        documentValidationService.validate(DocumentType.PAN, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);

        Optional<PANDocument> existingDocument = panDocumentRepository.findByShop(shop);

        PANDocument panDocument;
        if (existingDocument.isPresent()) {
            panDocument = existingDocument.get();
        } else {
            panDocument = new PANDocument();
            panDocument.setShop(shop);
        }

        String fileKey = "pan/shop_" + shopId + "/pan_card.pdf";
        String fileUrl = s3Service.uploadFile(file, fileKey);

        panDocument.setOriginalFileName(file.getOriginalFilename());
        panDocument.setUploadedFileName(fileKey);
        panDocument.setFileUrl(fileUrl);
        panDocument.setUploadedAt(LocalDateTime.now());

        panDocumentRepository.save(panDocument);
    }
}
