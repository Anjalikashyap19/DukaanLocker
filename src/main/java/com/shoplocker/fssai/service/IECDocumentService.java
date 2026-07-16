package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.IECDocument;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.repository.IECDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class IECDocumentService {

    private final IECDocumentRepository iecDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;
    private final TextractService textractService;
    private final DocumentValidationService documentValidationService;

    public IECDocumentService(IECDocumentRepository repository,
                              ShopService shopService,
                              S3Service s3Service,
                              TextractService textractService,
                              DocumentValidationService documentValidationService) {
        this.iecDocumentRepository = repository;
        this.shopService = shopService;
        this.s3Service = s3Service;
        this.textractService = textractService;
        this.documentValidationService = documentValidationService;
    }

    public void uploadIEC(Long shopId, MultipartFile file) {
        documentValidationService.validateFileFormat(file, "Import Export Code");
        byte[] fileBytes = documentValidationService.readBytes(file);
        documentValidationService.assertPdfMagicBytes(fileBytes, "Import Export Code");

        String extractedText = textractService.extractText(fileBytes, file.getOriginalFilename());
        documentValidationService.validate(DocumentType.IEC, extractedText, file.getOriginalFilename());

        Shop shop = shopService.getShopById(shopId);
        Optional<IECDocument> existing = iecDocumentRepository.findByShop(shop);
        IECDocument document = existing.orElseGet(() -> {
            IECDocument d = new IECDocument();
            d.setShop(shop);
            return d;
        });

        String fileKey = getFileKey(shopId);
        String fileUrl = s3Service.uploadFile(fileBytes, file.getContentType(), fileKey);

        document.setOriginalFileName(file.getOriginalFilename());
        document.setUploadedFileName(fileKey);
        document.setFileUrl(fileUrl);
        document.setUploadedAt(LocalDateTime.now());

        iecDocumentRepository.save(document);
    }

    private String getFileKey(Long shopId) {
        return "iec/shop_" + shopId + "/iec_certificate.pdf";
    }
}
