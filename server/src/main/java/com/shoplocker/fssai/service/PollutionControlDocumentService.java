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

    private final PollutionControlDocumentRepository pollutionControlDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public PollutionControlDocumentService(PollutionControlDocumentRepository repository,
                                            ShopService shopService,
                                            S3Service s3Service,
                                            TextractService textractService,
                                            DocumentValidationService documentValidationService) {
        this.pollutionControlDocumentRepository = repository;
        this.shopService = shopService;
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.documentValidationService = documentValidationService;
    }

    public void uploadPollutionControl(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Pollution Control Certificate");
        byte[] fileBytes = documentValidationService.readBytes(file);
        documentValidationService.assertPdfMagicBytes(fileBytes, "Pollution Control Certificate");

        String extractedText = textractService.extractText(fileBytes, file.getOriginalFilename());
        documentValidationService.validate(DocumentType.POLLUTION_CONTROL, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<PollutionControlDocument> existing = pollutionControlDocumentRepository.findByShop(shop);
        PollutionControlDocument document = existing.orElseGet(() -> {
            PollutionControlDocument d = new PollutionControlDocument();
            d.setShop(shop);
            return d;
        });

        String fileKey = getFileKey(shopId);
        String fileUrl = s3Service.uploadFile(fileBytes, file.getContentType(), fileKey);

        document.setOriginalFileName(file.getOriginalFilename());
        document.setUploadedFileName(fileKey);
        document.setFileUrl(fileUrl);
        document.setUploadedAt(LocalDateTime.now());

        pollutionControlDocumentRepository.save(document);
    }

    private String getFileKey(Long shopId) {
        return "pollution-control/shop_" + shopId + "/pollution_control_certificate.pdf";
    }
}
