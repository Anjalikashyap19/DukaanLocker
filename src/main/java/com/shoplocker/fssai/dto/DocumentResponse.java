package com.shoplocker.fssai.dto;

import com.shoplocker.fssai.entity.DocumentStatus;
import com.shoplocker.fssai.entity.DocumentType;

import java.time.LocalDateTime;

/**
 * Response DTO for document data. Used in document checklist listing.
 * For documents not yet uploaded, id is null and status is NOT_UPLOADED.
 */
public class DocumentResponse {

    private Long id;
    private Long shopId;
    private DocumentType documentType;
    private String fileName;
    private String fileUrl;
    private String documentNumber;
    private LocalDateTime issueDate;
    private LocalDateTime expiryDate;
    private DocumentStatus status;
    private int version;
    private LocalDateTime uploadedAt;
    private LocalDateTime updatedAt;

    public DocumentResponse() {}

    public DocumentResponse(Long id, Long shopId, DocumentType documentType,
                            String fileName, String fileUrl, String documentNumber,
                            LocalDateTime issueDate, LocalDateTime expiryDate,
                            DocumentStatus status, int version,
                            LocalDateTime uploadedAt, LocalDateTime updatedAt) {
        this.id = id;
        this.shopId = shopId;
        this.documentType = documentType;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.documentNumber = documentNumber;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.status = status;
        this.version = version;
        this.uploadedAt = uploadedAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType documentType) { this.documentType = documentType; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }

    public LocalDateTime getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDateTime issueDate) { this.issueDate = issueDate; }

    public LocalDateTime getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDateTime expiryDate) { this.expiryDate = expiryDate; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
