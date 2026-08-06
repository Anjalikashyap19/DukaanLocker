package com.shoplocker.fssai.dto;

/**
 * Response DTO containing a one-time view token for secure document access.
 * The token expires after 15 seconds and can only be used once.
 */
public class ViewTokenResponse {

    private String viewToken;
    private Long documentId;
    private String fileName;
    private int expiresIn;

    public ViewTokenResponse() {}

    public ViewTokenResponse(String viewToken, Long documentId, String fileName, int expiresIn) {
        this.viewToken = viewToken;
        this.documentId = documentId;
        this.fileName = fileName;
        this.expiresIn = expiresIn;
    }

    public String getViewToken() { return viewToken; }
    public void setViewToken(String viewToken) { this.viewToken = viewToken; }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public int getExpiresIn() { return expiresIn; }
    public void setExpiresIn(int expiresIn) { this.expiresIn = expiresIn; }
}
