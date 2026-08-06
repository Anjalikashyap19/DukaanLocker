package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.StreamDocumentRequest;
import com.shoplocker.fssai.dto.ViewDocumentRequest;
import com.shoplocker.fssai.dto.ViewTokenResponse;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.service.DocumentStreamService;
import com.shoplocker.fssai.service.ShopAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

/**
 * REST controller for secure document viewing with one-time view tokens.
 * 
 * Endpoints:
 * - POST /api/documents/view: Generate a one-time view token
 * - POST /api/documents/stream: Stream document using the view token
 * 
 * Security:
 * - JWT authentication required for both endpoints
 * - One-time view tokens with 15-second TTL
 * - Document ownership/authorization check
 * - No S3 URLs exposed to client
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentStreamController {

    private static final Logger log = LoggerFactory.getLogger(DocumentStreamController.class);

    private final DocumentStreamService documentStreamService;
    private final ShopAccessService shopAccessService;

    public DocumentStreamController(DocumentStreamService documentStreamService,
                                    ShopAccessService shopAccessService) {
        this.documentStreamService = documentStreamService;
        this.shopAccessService = shopAccessService;
    }

    /**
     * Request a one-time view token to securely view a document.
     * 
     * Flow:
     * 1. Frontend sends the documentId
     * 2. Backend validates the user's JWT
     * 3. Backend verifies that the user has permission to access the document
     * 4. Backend generates a one-time View Token (UUID)
     * 5. Token is stored in Redis with 15-second TTL
     * 6. Returns the View Token to the frontend
     * 
     * @param request  ViewDocumentRequest containing documentId
     * @param authentication  JWT authentication object
     * @return ViewTokenResponse with the one-time token
     */
    @PostMapping("/view")
    public ResponseEntity<ViewTokenResponse> requestViewToken(
            @RequestBody ViewDocumentRequest request,
            Authentication authentication) {
        
        log.info("View token requested for document: {}", request.getDocumentId());

        // Get authenticated user
        User user = shopAccessService.getAuthenticatedUser(authentication);

        // Generate one-time view token
        ViewTokenResponse response = documentStreamService.generateViewToken(
                request.getDocumentId(), user);

        log.info("View token generated successfully for document: {}", request.getDocumentId());

        return ResponseEntity.ok(response);
    }

    /**
     * Stream a document using a one-time view token.
     * 
     * Flow:
     * 1. Frontend sends the viewToken
     * 2. Backend validates the JWT
     * 3. Backend validates the View Token
     * 4. Backend deletes the token immediately after successful validation (one-time use)
     * 5. Backend retrieves the document from the private S3 bucket
     * 6. Backend streams the document back to the frontend as a binary response
     * 
     * @param request  StreamDocumentRequest containing viewToken
     * @param authentication  JWT authentication object
     * @return ResponseEntity with the document as binary stream
     */
    @PostMapping("/stream")
    public ResponseEntity<InputStream> streamDocument(
            @RequestBody StreamDocumentRequest request,
            Authentication authentication) {
        
        log.info("Document stream requested with token");

        // Get authenticated user
        User user = shopAccessService.getAuthenticatedUser(authentication);

        // Stream the document using the one-time token
        DocumentStreamService.DocumentStreamResult result = documentStreamService.streamDocument(
                request.getViewToken(), user);

        log.info("Document stream started successfully for: {}", result.getFileName());

        // Return the document as binary response with correct content type
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "inline; filename=\"" + result.getFileName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header("X-Content-Type-Options", "nosniff")
                .body(result.getInputStream());
    }
}
