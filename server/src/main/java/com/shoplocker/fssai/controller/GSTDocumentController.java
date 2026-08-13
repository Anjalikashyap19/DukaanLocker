package com.shoplocker.fssai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.service.GSTDocumentService;
import com.shoplocker.fssai.service.ShopAccessService;


@RestController
@RequestMapping("/docs")
public class GSTDocumentController {

    private final GSTDocumentService gstDocumentService;
    private final ShopAccessService shopAccessService;

    public GSTDocumentController(GSTDocumentService gstDocumentService,
                                 ShopAccessService shopAccessService) {
        this.gstDocumentService = gstDocumentService;
        this.shopAccessService = shopAccessService;
    }


    @PostMapping(
            value = "/shops/{shopId}/gst/upload",
            consumes = "multipart/form-data"
    )
    public ResponseEntity<String> uploadGST(
            @PathVariable Long shopId,
            @RequestParam("file") MultipartFile file
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = shopAccessService.getAuthenticatedUser(auth);
        shopAccessService.validateShopAccess(user, shopId);

        gstDocumentService.uploadGST(shopId, file);

        return ResponseEntity.ok("GST document uploaded successfully");
    }

}