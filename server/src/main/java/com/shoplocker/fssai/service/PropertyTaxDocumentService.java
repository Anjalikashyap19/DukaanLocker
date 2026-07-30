package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.PropertyTaxDocument;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.repository.PropertyTaxDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PropertyTaxDocumentService {

    private final PropertyTaxDocumentRepository propertyTaxDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public PropertyTaxDocumentService(PropertyTaxDocumentRepository propertyTaxDocumentRepository,
                                      ShopService shopService,
                                      S3Service s3Service,
                                      TextractService textractService,
                                      DocumentValidationService documentValidationService) {
        this.propertyTaxDocumentRepository = propertyTaxDocumentRepository;
        this.shopService = shopService;
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.documentValidationService = documentValidationService;
    }

    public void uploadPropertyTax(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Property Tax Certificate");
        byte[] fileBytes = documentValidationService.readBytes(file);
        documentValidationService.assertPdfMagicBytes(fileBytes, "Property Tax Certificate");

        String extractedText = textractService.extractText(fileBytes, file.getOriginalFilename());
        documentValidationService.validate(DocumentType.PROPERTY_TAX, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<PropertyTaxDocument> existing = propertyTaxDocumentRepository.findByShop(shop);
        PropertyTaxDocument document = existing.orElseGet(() -> {
            PropertyTaxDocument d = new PropertyTaxDocument();
            d.setShop(shop);
            return d;
        });

        String fileKey = getFileKey(shopId);
        String fileUrl = s3Service.uploadFile(fileBytes, file.getContentType(), fileKey);

        document.setOriginalFileName(file.getOriginalFilename());
        document.setUploadedFileName(fileKey);
        document.setFileUrl(fileUrl);
        document.setUploadedAt(LocalDateTime.now());

        propertyTaxDocumentRepository.save(document);
    }

    private String getFileKey(Long shopId) {
        return "property-tax/shop_" + shopId + "/property_tax_certificate.pdf";
    }
}
