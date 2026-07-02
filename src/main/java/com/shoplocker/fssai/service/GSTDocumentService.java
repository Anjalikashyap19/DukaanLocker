package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.GSTDocument;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.GSTDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class GSTDocumentService {

    private final GSTDocumentRepository gstDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;

    public GSTDocumentService(GSTDocumentRepository gstDocumentRepository,
                              ShopService shopService,
                              S3Service s3Service) {
        this.gstDocumentRepository = gstDocumentRepository;
        this.shopService = shopService;
        this.s3Service = s3Service;
    }

    public void uploadGST(Long shopId, MultipartFile file) {

        validateGSTFile(file);

        // Get shop
        Shop shop = shopService.getShopById(shopId);

        // Check if GST already exists
        Optional<GSTDocument> existingDocument =
                gstDocumentRepository.findByShop(shop);

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

    private void validateGSTFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new FssaiException("Please upload GST certificate.");
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

            if (is.read(header) < 4) {
                return false;
            }

            return header[0] == 0x25 &&
                    header[1] == 0x50 &&
                    header[2] == 0x44 &&
                    header[3] == 0x46;

        } catch (Exception e) {
            return false;
        }
    }

    private String generateGSTFileName(Long shopId) {

        return "gst/shop_" + shopId + "/gst_certificate.pdf";
    }
}