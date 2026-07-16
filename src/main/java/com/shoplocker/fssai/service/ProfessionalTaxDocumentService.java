package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.ProfessionalTaxDocument;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.repository.ProfessionalTaxDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ProfessionalTaxDocumentService {

    private final ProfessionalTaxDocumentRepository professionalTaxDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public ProfessionalTaxDocumentService(ProfessionalTaxDocumentRepository professionalTaxDocumentRepository,
                                          ShopService shopService,
                                          S3Service s3Service,
                                          TextractService textractService,
                                          DocumentValidationService documentValidationService) {
        this.professionalTaxDocumentRepository = professionalTaxDocumentRepository;
        this.shopService = shopService;
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.documentValidationService = documentValidationService;
    }

    public void uploadProfessionalTax(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Professional Tax Registration");
        byte[] fileBytes = documentValidationService.readBytes(file);
        documentValidationService.assertPdfMagicBytes(fileBytes, "Professional Tax Registration");

        String extractedText = textractService.extractText(fileBytes, file.getOriginalFilename());
        documentValidationService.validate(DocumentType.PROFESSIONAL_TAX, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<ProfessionalTaxDocument> existing = professionalTaxDocumentRepository.findByShop(shop);
        ProfessionalTaxDocument document = existing.orElseGet(() -> {
            ProfessionalTaxDocument d = new ProfessionalTaxDocument();
            d.setShop(shop);
            return d;
        });

        String fileKey = getFileKey(shopId);
        String fileUrl = s3Service.uploadFile(fileBytes, file.getContentType(), fileKey);

        document.setOriginalFileName(file.getOriginalFilename());
        document.setUploadedFileName(fileKey);
        document.setFileUrl(fileUrl);
        document.setUploadedAt(LocalDateTime.now());

        professionalTaxDocumentRepository.save(document);
    }

    private String getFileKey(Long shopId) {
        return "professional-tax/shop_" + shopId + "/professional_tax_registration.pdf";
    }
}
