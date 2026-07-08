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

    private final PropertyTaxDocumentRepository repository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public PropertyTaxDocumentService(PropertyTaxDocumentRepository repository,
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

    public void uploadPropertyTax(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Property Tax Certificate");

        // Extract text via AWS Textract and validate document content
        String extractedText = textractService.extractText(file);
        documentValidationService.validate(DocumentType.PROPERTY_TAX, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<PropertyTaxDocument> existing = repository.findByShop(shop);

        PropertyTaxDocument doc;
        if (existing.isPresent()) {
            doc = existing.get();
        } else {
            doc = new PropertyTaxDocument();
            doc.setShop(shop);
        }

        String fileKey = "property-tax/shop_" + shopId + "/property_tax.pdf";
        String fileUrl = s3Service.uploadFile(file, fileKey);

        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setUploadedFileName(fileKey);
        doc.setFileUrl(fileUrl);
        doc.setUploadedAt(LocalDateTime.now());

        repository.save(doc);
    }
}
