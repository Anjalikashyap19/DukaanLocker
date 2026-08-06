package com.shoplocker.fssai.dto;

/**
 * Request DTO for requesting a view token to securely view a document.
 */
public class ViewDocumentRequest {

    private Long documentId;

    public ViewDocumentRequest() {}

    public ViewDocumentRequest(Long documentId) {
        this.documentId = documentId;
    }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
}
