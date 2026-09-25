package com.shoplocker.fssai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

@Service
public class LocalFileStorageService {

    private final String basePath;

    // Legacy mutable state — used only by the deprecated 3-arg uploadFile overload.
    // New code should use the 5-arg overload with explicit userId/shopId parameters.
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
     * Sets the current user ID and shop ID for the legacy uploadFile overload.
     * Deprecated: pass userId and shopId explicitly to {@link #uploadFile(byte[], String, Long, Long, String)}.
     *
     * @deprecated Use {@link #uploadFile(byte[], String, Long, Long, String)} with explicit parameters.
     */
    @Deprecated
    public void setContext(Long userId, Long shopId) {
        this.currentUserId = userId;
        this.currentShopId = shopId;
    }

    /**
     * Clears the context after upload is complete.
     *
     * @deprecated No longer needed when using the 5-arg uploadFile overload.
     */
    @Deprecated
    public void clearContext() {
        this.currentUserId = null;
        this.currentShopId = null;
    }

    /**
     * Uploads file bytes to local folder structure based on DL ID and Shop ID:
     * /basePath/documents/dl-id_shop-id/{fileKey-relative-path}
     *
     * <p>The folder structure is:
     * <pre>
     * documents/
     *   └── {dl-id}_{shop-id}/
     *       ├── udyam/
     *       │   └── {udyam-number}/
     *       │       └── udyam_certificate.pdf
     *       ├── gst/
     *       │   └── {gst-number}/
     *       │       └── gst_certificate.pdf
     *       └── {document-type}/
     *           └── {original-filename}
     * </pre>
     *
     * @param fileBytes    The file content as byte array
     * @param contentType  MIME type of the file (e.g., "application/pdf")
     * @param userId       The DL user ID (used as the 4-digit dl-id prefix)
     * @param shopId       The shop ID
     * @param fileKey      Relative path within the shop folder (e.g., "gst/verify/xyz/gst_certificate.pdf"
     *                     or "udyam_certificate.pdf"). If null/empty, a UUID-based name is generated.
     * @return The stored file key (relative path) that includes dl-id and shop-id
     */
    public String uploadFile(byte[] fileBytes, String contentType, Long userId, Long shopId, String fileKey) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("File bytes cannot be null or empty");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (shopId == null) {
            throw new IllegalArgumentException("shopId cannot be null");
        }

         // Build folder path: dl-id_shop-id
        // DL IDs start from 1111 to avoid collision with the reserved 0001-1110 range.
        // The userId passed here is the User's database ID, offset by +1110 to produce
        // the DL ID (e.g., user id 1 -> dl-id 1111, user id 2 -> 1112, etc.)
        String dlId = String.format("%04d", userId + 1110);
        String folderName = dlId + "_" + shopId;

        // Determine the relative path within the shop folder
        String relativePath;
        if (fileKey != null && !fileKey.isEmpty()) {
            // Sanitize the file key to prevent directory traversal
            relativePath = sanitizeFileKey(fileKey);
        } else {
            // Fallback: generate a UUID-based filename
            String uniqueFileName = UUID.randomUUID().toString().replace("-", "") + ".pdf";
            relativePath = uniqueFileName;
        }

        // Build full stored path: documents/dl-id_shop-id/{relativePath}
        String storedPath = "documents/" + folderName + "/" + relativePath;

        Path filePath = Paths.get(basePath, storedPath);

        // Create parent directories
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

        return storedPath;
    }

    /**
     * Backward-compatible overload that uses setContext() state.
     * Deprecated: prefer {@link #uploadFile(byte[], String, Long, Long, String)}.
     *
     * @deprecated Use {@link #uploadFile(byte[], String, Long, Long, String)} instead.
     */
    @Deprecated
    public String uploadFile(byte[] fileBytes, String contentType, String fileKey) {
        if (currentUserId == null || currentShopId == null) {
            throw new IllegalStateException(
                "No context set. Either call setContext(userId, shopId) before uploadFile, " +
                "or use the uploadFile(fileBytes, contentType, userId, shopId, fileKey) overload.");
        }
        return uploadFile(fileBytes, contentType, currentUserId, currentShopId, fileKey);
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
        // Remove base path if present (handles both default and Docker paths)
        String key = fileUrl;
        if (key.startsWith("/opt/dukaanlocker/Documents")) {
            key = key.substring("/opt/dukaanlocker/Documents".length());
        } else if (key.startsWith("/app/Documents")) {
            key = key.substring("/app/Documents".length());
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
     * Sanitizes a relative file key path to prevent directory traversal.
     * Removes leading slashes, normalizes path separators, and strips
     * dangerous sequences like "../".
     *
     * @param fileKey the raw relative path (e.g., "gst/verify/abc123/gst_certificate.pdf")
     * @return a safe relative path
     */
    private String sanitizeFileKey(String fileKey) {
        if (fileKey == null || fileKey.isEmpty()) {
            String uniqueFileName = UUID.randomUUID().toString().replace("-", "") + ".pdf";
            return uniqueFileName;
        }

        // Remove leading slashes to prevent absolute paths
        String key = fileKey;
        while (key.startsWith("/")) {
            key = key.substring(1);
        }

        // Normalize backslashes to forward slashes
        key = key.replace('\\', '/');

        // Remove any ".." path traversal segments
        String[] parts = key.split("/");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty() || "..".equals(part) || ".".equals(part)) {
                continue;
            }
            // Sanitize each segment to safe characters
            String sanitized = part.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(sanitized);
        }
        String result = sb.toString();

        if (result.isEmpty()) {
            // Fallback if everything was stripped
            String uniqueFileName = UUID.randomUUID().toString().replace("-", "") + ".pdf";
            return uniqueFileName;
        }

        // Ensure the final segment has an extension
        int lastSlash = result.lastIndexOf('/');
        String lastSegment = (lastSlash >= 0) ? result.substring(lastSlash + 1) : result;
        if (!lastSegment.contains(".")) {
            result = result + ".pdf";
        }

        return result;
    }

    /**
     * Sanitizes the file name to prevent directory traversal and ensure valid characters.
     */
    private String sanitizeFileName(String fileName) {
        return sanitizeFileKey(fileName);
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