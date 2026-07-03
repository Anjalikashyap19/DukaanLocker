package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.PropertyTaxDocument;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.PropertyTaxDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PropertyTaxDocumentService {

    private final PropertyTaxDocumentRepository repository;
    private final ShopService shopService;
    private final S3Service s3Service;

    public PropertyTaxDocumentService(PropertyTaxDocumentRepository repository,
                                      ShopService shopService,
                                      S3Service s3Service) {
        this.repository = repository;
        this.shopService = shopService;
        this.s3Service = s3Service;
    }

    public void uploadPropertyTax(Long shopId, MultipartFile file) {
        validatePDFFile(file, "Property Tax Certificate");

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

    private void validatePDFFile(MultipartFile file, String docName) {
        if (file == null || file.isEmpty()) {
            throw new FssaiException("Please upload " + docName + " document.");
        }
        if (!"application/pdf".equals(file.getContentType())) {
            throw new FssaiException("Only PDF files are allowed.");
        }
        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new FssaiException("Maximum file size is 5 MB.");
        }
        if (!isPdfMagicBytes(file)) {
            throw new FssaiException("Invalid PDF file.");
        }
    }

    private boolean isPdfMagicBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[4];
            if (is.read(header) < 4) return false;
            return header[0] == 0x25 && header[1] == 0x50 &&
                   header[2] == 0x44 && header[3] == 0x46;
        } catch (Exception e) {
            return false;
        }
    }
}
