package com.shoplocker.fssai.service;

import org.springframework.stereotype.Service;

import com.shoplocker.fssai.entity.GSTDocument;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.GSTDocumentRepository;

import org.springframework.web.multipart.MultipartFile;
import com.shoplocker.fssai.entity.Shop;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

@Service
public class GSTDocumentService {

    private final GSTDocumentRepository gstDocumentRepository;
    private final ShopService shopService;
    private final S3Service s3Service;

    public GSTDocumentService(GSTDocumentRepository gstDocumentRepository, ShopService shopService, S3Service s3Service) {
        this.gstDocumentRepository = gstDocumentRepository;
        this.shopService = shopService;
        this.s3Service = s3Service;
    }

    public void uploadGST(Long shopId, MultipartFile file) {
        validateGSTFile(file);

        Shop shop = shopService.getShopById(shopId);

        Optional<GSTDocument> existingDocument = gstDocumentRepository.findByShop(shop);
        GSTDocument gstDocument;

        if (existingDocument.isPresent()) {
            gstDocument = existingDocument.get();
        } else {
            gstDocument = new GSTDocument();
            gstDocument.setShop(shop);
        }

        String fileKey = generateGSTFileName(shopId);
        String fileUrl = s3Service.uploadFile(file, fileKey);

        String originalName = file.getOriginalFilename();
        gstDocument.setOriginalFileName(originalName != null ? originalName : "unknown.pdf");
        gstDocument.setUploadedFileName(fileKey);
        gstDocument.setFileUrl(fileUrl);
        gstDocument.setUploadedAt(LocalDateTime.now());

        gstDocumentRepository.save(gstDocument);
    }

    private void validateGSTFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FssaiException("please upload a gst certificate");
        }

        String contentType = file.getContentType();
        if (!"application/pdf".equals(contentType)) {
            throw new FssaiException("only pdf files are allowed");
        }

        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new FssaiException("pdf file should be less than 5mb");
        }

        if (!isPdfMagicBytes(file)) {
            throw new FssaiException("file content does not match pdf format");
        }
    }

    private boolean isPdfMagicBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[4];
            int bytesRead = is.read(header);
            if (bytesRead < 4) return false;
            return header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46;
        } catch (Exception e) {
            return false;
        }
    }

    private String generateGSTFileName(Long shopId) {
        String uniqueId = UUID.randomUUID().toString();
        return "gst/shop_" + shopId + "/gst" + shopId + "_" + uniqueId + ".pdf";
    }

}
