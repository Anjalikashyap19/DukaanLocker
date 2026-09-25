package com.shoplocker.fssai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

@Service
public class LocalFileStorageService {

    private final String basePath;

    private Long currentUserId = null;
    private Long currentShopId = null;

    public LocalFileStorageService(
            @Value("${storage.base-path:/opt/dukaanlocker/Documents}") String basePath) {
        this.basePath = basePath;
        try {
            Files.createDirectories(Paths.get(basePath));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create base storage directory: " + basePath, e);
        }
    }

    /**
     * Sets the current user ID and shop ID for folder path generation.
     * This should be called before uploadFile() to set the DL ID and shop context.
     */
    public void setContext(Long userId, Long shopId) {
        this.currentUserId = userId;
        this.currentShopId = shopId;
    }

    /**
     * Clears the context after upload is complete.
     */
    public void clearContext() {
        this.currentUserId = null;
        this.currentShopId = null;
    }

    /**
     * Uploads file bytes to local folder structure based on DL ID and Shop ID:
     * /basePath/dl-id/documents/dl-id_shop-id/
     * 
     * The file is stored with a unique UUID inside the shop-specific folder,
     * and the fileUrl returned includes the DL ID and Shop ID for organized storage.
     * 
     * @param fileBytes    The file content as byte array
     * @param contentType  MIME type of the file (e.g., "application/pdf")
     * @param fileKey      Original file key/name from the request
     * @return The stored file key (relative path) that includes dl-id and shop-id
     */
    public String uploadFile(byte[] fileBytes, String contentType, String fileKey) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("File bytes cannot be null or empty");
        }

        // Generate unique file name (UUID to prevent guessing)
        String uniqueFileName = UUID.randomUUID().toString().replace("-", "") + ".pdf";

        // Build folder path: dl-id_shop-id
        // e.g., 0001_shop123 or 0042_shop456
        String dlId = String.format("%04d", currentUserId != null ? currentUserId : 1);
        String shopIdStr = currentShopId != null ? String.valueOf(currentShopId) : "0";
        String folderName = dlId + "_" + shopIdStr;

        // Sanitize the original file key to prevent directory traversal
        String sanitizedKey = sanitizeFileName(fileKey);

        // Build stored path: documents/dl-id_shop-id/unique-filename
        // The fileKey returned will be: documents/dl-id_shop-id/unique-filename
        String storedPath = "documents/" + folderName + "/" + uniqueFileName;

        Path filePath = Paths.get(basePath, storedPath);

        // Create parent directories (dl-id_shop-id folder)
        try {
            Files.createDirectories(filePath.getParent());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create directory for: " + filePath, e);
        }

        // Write file
        try {
            Files.write(filePath, fileBytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file to: " + filePath, e);
        }

        // Return file key that includes dl-id and shop-id in the path
        // Format: documents/dl-id_shop-id/unique-filename
        return storedPath;
    }

    /**
     * Retrieves an InputStream for a file stored locally.
     * Used for secure document streaming without exposing file paths.
     */
    public InputStream getObject(String fileKey) {
        if (fileKey == null || fileKey.isEmpty()) {
            throw new IllegalArgumentException("Invalid file key provided");
        }

        Path filePath = Paths.get(basePath, fileKey);

        if (!java.nio.file.Files.exists(filePath)) {
            throw new IllegalArgumentException("File not found: " + fileKey);
        }

        try {
            return new BufferedInputStream(Files.newInputStream(filePath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + fileKey, e);
        }
    }

    /**
     * Gets the file size of a local file.
     */
    public long getObjectSize(String fileKey) {
        if (fileKey == null || fileKey.isEmpty()) {
            return -1;
        }

        Path filePath = Paths.get(basePath, fileKey);

        if (!java.nio.file.Files.exists(filePath)) {
            return -1;
        }

        try {
            return java.nio.file.Files.size(filePath);
        } catch (IOException e) {
            // Log but don't fail - Content-Length is optional
            return -1;
        }
    }

    /**
     * Extracts the stored file key from a full local path or returns the input as-is.
     * Local paths format: "documents/dl-id_shop-id/unique-filename"
     * Also extracts document type from the path (gst, msme, etc.)
     */
    public String extractObjectKeyFromFileUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }

        // If already a bare key (starts with "documents/"), return as-is
        if (fileUrl.startsWith("documents/") || fileUrl.startsWith("Documents/")) {
            return fileUrl;
        }

        // If it's a full path, extract just the key portion
        // Remove base path if present
        String key = fileUrl;
        if (key.startsWith("/opt/dukaanlocker/Documents")) {
            key = key.substring("/opt/dukaanlocker/Documents".length());
        } else if (key.startsWith(basePath)) {
            key = key.substring(basePath.length());
        }

        // Ensure it starts with documents/
        if (!key.startsWith("documents/") && !key.startsWith("Documents/")) {
            key = "documents/" + key;
        }

        return key;
    }

    /**
     * Gets the DL ID from the file key path.
     */
    public String getDlIdFromKey(String fileKey) {
        if (fileKey == null || fileKey.isEmpty()) {
            return null;
        }
        // Path format: documents/dl-id_shop-id/unique-filename
        int docsIdx = fileKey.indexOf("documents/");
        if (docsIdx < 0) return null;
        
        String afterDocs = fileKey.substring(docsIdx + "documents/".length());
        int slashIdx = afterDocs.indexOf("/");
        if (slashIdx < 0) return null;
        
        return afterDocs.substring(0, slashIdx);
    }

    /**
     * Gets the Shop ID from the file key path.
     */
    public String getShopIdFromKey(String fileKey) {
        if (fileKey == null || fileKey.isEmpty()) {
            return null;
        }
        // Path format: documents/dl-id_shop-id/unique-filename
        int docsIdx = fileKey.indexOf("documents/");
        if (docsIdx < 0) return null;
        
        String afterDocs = fileKey.substring(docsIdx + "documents/".length());
        int firstUnderscore = afterDocs.indexOf("_");
        int secondUnderscore = afterDocs.indexOf("_", firstUnderscore + 1);
        
        if (firstUnderscore < 0 || secondUnderscore < 0) return null;
        
        return afterDocs.substring(firstUnderscore + 1, secondUnderscore);
    }

    /**
     * Gets the document type from the file key path.
     */
    public String getDocumentTypeFromKey(String fileKey) {
        if (fileKey == null || fileKey.isEmpty()) {
            return null;
        }
        // Path format: documents/dl-id_shop-id/unique-filename
        // The unique-filename could have doc type info, but we extract what's after dl-id_shop-id/
        int docsIdx = fileKey.indexOf("documents/");
        if (docsIdx < 0) return null;
        
        String afterDocs = fileKey.substring(docsIdx + "documents/".length());
        int firstSlash = afterDocs.indexOf("/");
        
        if (firstSlash < 0) return null;
        
        return afterDocs.substring(0, firstSlash);
    }

    /**
     * Sanitizes the file name to prevent directory traversal and ensure valid characters.
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unknown." + guessExtension("");
        }
        
        // Remove any path separators or dangerous characters
        String sanitized = fileName.replaceAll("[/\\\\]", "_");
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        // Ensure it has an extension or add default
        if (!sanitized.contains(".")) {
            sanitized += ".pdf";
        }
        
        return sanitized;
    }

    /**
     * Guesses file extension from content type or returns default.
     */
    private String guessExtension(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return "bin";
        }
        if (contentType.contains("pdf")) return "pdf";
        if (contentType.contains("image")) return "jpg";
        if (contentType.contains("word")) return "doc";
        if (contentType.contains("excel")) return "xls";
        return "bin";
    }
}