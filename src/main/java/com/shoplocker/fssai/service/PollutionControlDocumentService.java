package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.PollutionControlDocument;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.repository.PollutionControlDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PollutionControlDocumentService {

    private final PollutionControlDocumentRepository repository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public PollutionControlDocumentService(PollutionControlDocumentRepository repository,
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

    public void uploadPollutionControl(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Pollution Control Certificate");

        // Extract text via AWS Textract and validate document content
        String extractedText = textractService.extractText(file);
        documentValidationService.validate(DocumentType.POLLUTION_CONTROL, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<PollutionControlDocument> existing = repository.findByShop(shop);

        PollutionControlDocument doc;
        if (existing.isPresent()) {
            doc = existing.get();
        } else {
            doc = new PollutionControlDocument();
            doc.setShop(shop);
        }

        String fileKey = "pollution-control/shop_" + shopId + "/pollution_control.pdf";
        String fileUrl = s3Service.uploadFile(file, fileKey);

        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setUploadedFileName(fileKey);
        doc.setFileUrl(fileUrl);
        doc.setUploadedAt(LocalDateTime.now());

        repository.save(doc);
    }
}
