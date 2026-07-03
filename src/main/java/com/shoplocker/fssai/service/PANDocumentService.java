package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.PANDocument;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.PANDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PANDocumentService {

    private final PANDocumentRepository panDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;

    public PANDocumentService(PANDocumentRepository panDocumentRepository,
                              ShopService shopService,
                              S3Service s3Service) {
        this.panDocumentRepository = panDocumentRepository;
        this.shopService = shopService;
        this.s3Service = s3Service;
    }

    public void uploadPAN(Long shopId, MultipartFile file) {
        validatePDFFile(file, "PAN/TAN");

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
