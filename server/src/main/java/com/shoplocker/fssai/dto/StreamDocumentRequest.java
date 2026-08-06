package com.shoplocker.fssai.dto;

/**
 * Request DTO for streaming a document using a one-time view token.
 */
public class StreamDocumentRequest {

    private String viewToken;

    public StreamDocumentRequest() {}

    public StreamDocumentRequest(String viewToken) {
        this.viewToken = viewToken;
    }

    public String getViewToken() { return viewToken; }
    public void setViewToken(String viewToken) { this.viewToken = viewToken; }
}
