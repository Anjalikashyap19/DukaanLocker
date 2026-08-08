package com.shoplocker.fssai.service;

import com.shoplocker.fssai.dto.ViewTokenResponse;
import com.shoplocker.fssai.entity.Document;
import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for secure document viewing with one-time view tokens.
 * 
 * Flow:
 * 1. Frontend requests a view token for a document ID
 * 2. Backend validates user has access to the document
 * 3. Backend generates a one-time view token (UUID)
 * 4. Token is stored in Redis with 15-second TTL
 * 5. Frontend uses the token to stream the document
 * 6. Token is deleted immediately after first use
 * 
 * Security Features:
 * - Private S3 bucket (no public access)
 * - No S3 URL exposed to client
 * - JWT-based authentication
 * - Document ownership/authorization check
 * - One-time View Token
 * - Token expires automatically (15 seconds)
 * - Token deleted after first successful use
 * - Backend controls all document access
 */
@Service
public class DocumentStreamService {

    private static final Logger log = LoggerFactory.getLogger(DocumentStreamService.class);

    private static final String TOKEN_PREFIX = "view_token:";
    private static final int TOKEN_TTL_SECONDS = 15;

    private final RedisTemplate<String, Object> redisTemplate;
    private final DocumentRepository documentRepository;
    private final ShopAccessService shopAccessService;
    private final S3Service s3Service;

    @Value("${aws.bucketName}")
    private String bucketName;

    public DocumentStreamService(RedisTemplate<String, Object> redisTemplate,
                                 DocumentRepository documentRepository,
                                 ShopAccessService shopAccessService,
                                 S3Service s3Service) {
        this.redisTemplate = redisTemplate;
        this.documentRepository = documentRepository;
        this.shopAccessService = shopAccessService;
        this.s3Service = s3Service;
    }

    /**
     * Generates a one-time view token for secure document access.
     * 
     * @param documentId    The ID of the document to view
     * @param authenticatedUser The authenticated user requesting access
     * @return ViewTokenResponse containing the token and document info
     * @throws FssaiException if document not found or access denied
     */
    public ViewTokenResponse generateViewToken(Long documentId, User authenticatedUser) {
        // 1. Find the document
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new FssaiException(
                        "Document not found with id: " + documentId,
                        FailureCode.DOCUMENT_NOT_FOUND));

        // 2. Validate user has access to the shop this document belongs to
        Long shopId = document.getShop().getId();
        shopAccessService.validateShopAccess(authenticatedUser, shopId);

        // 3. Verify document has been uploaded
        if (document.getFileUrl() == null || document.getFileUrl().isEmpty()) {
            throw new FssaiException(
                    "This document has not been uploaded yet.",
                    FailureCode.DOCUMENT_NOT_FOUND);
        }

        // 4. Extract S3 object key from the file URL
        String s3ObjectKey = s3Service.extractObjectKeyFromFileUrl(document.getFileUrl());
        if (s3ObjectKey == null || s3ObjectKey.isEmpty()) {
            throw new FssaiException(
                    "Invalid document URL format.",
                    FailureCode.INVALID_REQUEST);
        }

        // 5. Generate one-time view token
        String viewToken = UUID.randomUUID().toString();

        // 6. Create token data object with all required information
        ViewTokenData tokenData = new ViewTokenData();
        tokenData.setUserId(authenticatedUser.getId());
        tokenData.setDocumentId(documentId);
        tokenData.setS3ObjectKey(s3ObjectKey);
        tokenData.setFileName(document.getFileName());
        tokenData.setDocumentType(document.getDocumentType().name());
        tokenData.setContentType(determineContentType(document.getFileName()));

        // 7. Store in Redis with 15-second TTL
        String redisKey = TOKEN_PREFIX + viewToken;
        redisTemplate.opsForValue().set(redisKey, tokenData, TOKEN_TTL_SECONDS, TimeUnit.SECONDS);

        log.info("Generated view token for document {} (user: {}, shop: {})", 
                documentId, authenticatedUser.getId(), shopId);

        // 8. Return token response (no S3 URL exposed)
        return new ViewTokenResponse(
                viewToken,
                documentId,
                document.getFileName(),
                TOKEN_TTL_SECONDS);
    }

    /**
     * Streams a document using a one-time view token.
     * 
     * @param viewToken       The one-time view token
     * @param authenticatedUser The authenticated user
     * @return DocumentStreamResult containing the input stream and content type
     * @throws FssaiException if token invalid, expired, or access denied
     */
    public DocumentStreamResult streamDocument(String viewToken, User authenticatedUser) {
        // 1. Validate token format
        if (viewToken == null || viewToken.isEmpty()) {
            throw new FssaiException(
                    "Invalid view token.",
                    FailureCode.VIEW_TOKEN_EXPIRED);
        }

        // 2. Retrieve and delete token from Redis (one-time use)
        String redisKey = TOKEN_PREFIX + viewToken;
        ViewTokenData tokenData = (ViewTokenData) redisTemplate.opsForValue().get(redisKey);

        if (tokenData == null) {
            throw new FssaiException(
                    "View token has expired or is invalid. Please request a new token.",
                    FailureCode.VIEW_TOKEN_EXPIRED);
        }

        // 3. Delete the token immediately (one-time use)
        redisTemplate.delete(redisKey);

        // 4. Validate user matches the token owner
        if (!tokenData.getUserId().equals(authenticatedUser.getId())) {
            log.warn("User {} attempted to use token belonging to user {}", 
                    authenticatedUser.getId(), tokenData.getUserId());
            throw new FssaiException(
                    "Invalid view token.",
                    FailureCode.VIEW_TOKEN_EXPIRED);
        }

        // 5. Validate user still has access to the document
        Document document = documentRepository.findById(tokenData.getDocumentId())
                .orElseThrow(() -> new FssaiException(
                        "Document not found.",
                        FailureCode.DOCUMENT_NOT_FOUND));

        Long shopId = document.getShop().getId();
        shopAccessService.validateShopAccess(authenticatedUser, shopId);

        log.info("Streaming document {} for user {} (shop: {})", 
                tokenData.getDocumentId(), authenticatedUser.getId(), shopId);

        // 6. Get file size for Content-Length header (helps with nginx buffering)
        long fileSize = s3Service.getObjectSize(tokenData.getS3ObjectKey());
        
        // 7. Stream the document from S3
        InputStream inputStream = s3Service.getObject(tokenData.getS3ObjectKey());
        
        return new DocumentStreamResult(inputStream, tokenData.getContentType(), tokenData.getFileName(), fileSize);
    }

    /**
     * Determine content type based on file name.
     */
    private String determineContentType(String fileName) {
        if (fileName == null) {
            return "application/pdf";
        }
        
        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerFileName.endsWith(".png")) {
            return "image/png";
        }
        
        return "application/pdf";
    }

    /**
     * Result object containing document stream and metadata.
     */
    public static class DocumentStreamResult {
        private final InputStream inputStream;
        private final String contentType;
        private final String fileName;
        private final long fileSize;

        public DocumentStreamResult(InputStream inputStream, String contentType, String fileName, long fileSize) {
            this.inputStream = inputStream;
            this.contentType = contentType;
            this.fileName = fileName;
            this.fileSize = fileSize;
        }

        public InputStream getInputStream() { return inputStream; }
        public String getContentType() { return contentType; }
        public String getFileName() { return fileName; }
        public long getFileSize() { return fileSize; }
    }

    /**
     * Inner class to hold view token data in Redis.
     * Uses simple POJO for Redis serialization.
     */
    public static class ViewTokenData implements java.io.Serializable {
        private Long userId;
        private Long documentId;
        private String s3ObjectKey;
        private String fileName;
        private String documentType;
        private String contentType;

        public ViewTokenData() {}

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public Long getDocumentId() { return documentId; }
        public void setDocumentId(Long documentId) { this.documentId = documentId; }

        public String getS3ObjectKey() { return s3ObjectKey; }
        public void setS3ObjectKey(String s3ObjectKey) { this.s3ObjectKey = s3ObjectKey; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }

        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
    }
}
