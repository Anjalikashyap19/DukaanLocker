package com.shoplocker.fssai.controller;

import java.util.List;

import com.shoplocker.fssai.dto.CreateShopRequest;
import com.shoplocker.fssai.dto.DocumentResponse;
import com.shoplocker.fssai.dto.ShopResponse;
import com.shoplocker.fssai.dto.UpdateShopRequest;
import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.service.DocumentValidationService;
import com.shoplocker.fssai.service.S3Service;
import com.shoplocker.fssai.service.ShopAccessService;
import com.shoplocker.fssai.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/shops")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Shops", description = "Shop management — create, list, and manage documents")
public class ShopController {

    private final ShopService shopService;
    private final ShopAccessService shopAccessService;
    private final DocumentValidationService documentValidationService;
    private final S3Service s3Service;

    public ShopController(ShopService shopService,
                          ShopAccessService shopAccessService,
                          DocumentValidationService documentValidationService,
                          S3Service s3Service) {
        this.shopService = shopService;
        this.shopAccessService = shopAccessService;
        this.documentValidationService = documentValidationService;
        this.s3Service = s3Service;
    }

    @Operation(summary = "Create a shop", description = "ADMIN only. Creates a new shop and links it to " +
            "the authenticated user as the owner. The owner is read from JWT, NOT from the request body.")
    @PostMapping
    public ResponseEntity<ShopResponse> createShop(@Valid @RequestBody CreateShopRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        ShopResponse response = shopService.createShop(request, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{shopId}")
    public ResponseEntity<ShopResponse> updateShop(
            @PathVariable Long shopId,
            @Valid @RequestBody UpdateShopRequest request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = shopAccessService.getAuthenticatedUser(auth);

        shopAccessService.validateShopAccess(user, shopId);

        return ResponseEntity.ok(shopService.updateShop(shopId, request));
    }

    @Operation(summary = "Get my shops", description = "ADMIN only. Returns shops owned by the logged-in user.")
    @GetMapping("/my-shops")
    public ResponseEntity<List<ShopResponse>> getMyShops() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        List<ShopResponse> shops = shopService.getMyShops(email);
        return ResponseEntity.ok(shops);
    }

    @Operation(summary = "Get shop by ID", description = "Get details of a specific shop. " +
            "ADMIN can access own shops. MANAGER can access assigned shops.")
    @GetMapping("/{shopId}")
    public ResponseEntity<ShopResponse> getShop(@PathVariable Long shopId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = shopAccessService.getAuthenticatedUser(auth);
        shopAccessService.validateShopAccess(user, shopId);
        ShopResponse response = shopService.getShopResponseById(shopId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get shop documents", description = "Returns the document checklist for a shop. " +
            "Includes both uploaded documents and required documents not yet uploaded (status=NOT_UPLOADED). " +
            "ADMIN can access own shops. MANAGER can access assigned shops.")
    @GetMapping("/{shopId}/documents")
    public ResponseEntity<List<DocumentResponse>> getShopDocuments(@PathVariable Long shopId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = shopAccessService.getAuthenticatedUser(auth);
        shopAccessService.validateShopAccess(user, shopId);
        List<DocumentResponse> documents = shopService.getShopDocuments(shopId);
        return ResponseEntity.ok(documents);
    }

    @Operation(summary = "Re-upload document", description = "Upload or re-upload a document for a shop. " +
            "Increments version on re-upload. Only the shop owner ADMIN or an assigned MANAGER may upload. " +
            "Send multipart/form-data with 'file' part and optional metadata fields.")
    @PutMapping(value = "/{shopId}/documents/{documentType}/reupload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> reuploadDocument(
            @PathVariable Long shopId,
            @PathVariable String documentType,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentNumber", required = false) String documentNumber,
            @RequestParam(value = "issueDate", required = false) String issueDateStr,
            @RequestParam(value = "expiryDate", required = false) String expiryDateStr) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = shopAccessService.getAuthenticatedUser(auth);
        shopAccessService.validateShopAccess(user, shopId);

        // Parse document type
        DocumentType docType;
        try {
            docType = DocumentType.valueOf(documentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new FssaiException("Invalid document type: " + documentType,
                    FailureCode.INVALID_REQUEST);
        }

        // Validate file
        documentValidationService.validateFileFormat(file, docType.name());

        // Read file bytes
        byte[] fileBytes = documentValidationService.readBytes(file);

        // Validate magic bytes
        documentValidationService.assertPdfMagicBytes(fileBytes, docType.name());

        // Upload to S3
        String fileKey = "documents/shop_" + shopId + "/" + docType.name().toLowerCase() + "/" + file.getOriginalFilename();
        String fileUrl = s3Service.uploadFile(fileBytes, file.getContentType(), fileKey);

        // Parse dates if provided
        LocalDateTime issueDate = null;
        LocalDateTime expiryDate = null;
        if (issueDateStr != null && !issueDateStr.isBlank()) {
            issueDate = LocalDateTime.parse(issueDateStr);
        }
        if (expiryDateStr != null && !expiryDateStr.isBlank()) {
            expiryDate = LocalDateTime.parse(expiryDateStr);
        }

        DocumentResponse response = shopService.uploadOrReuploadDocument(
                shopId, docType,
                file.getOriginalFilename(), fileUrl,
                documentNumber, issueDate, expiryDate);

        return ResponseEntity.ok(response);
    }
}
