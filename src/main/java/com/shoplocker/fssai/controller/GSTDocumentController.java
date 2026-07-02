package com.shoplocker.fssai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.shoplocker.fssai.service.GSTDocumentService;


@RestController
@RequestMapping("/docs")
public class GSTDocumentController {

    private final GSTDocumentService gstDocumentService;

    public GSTDocumentController(GSTDocumentService gstDocumentService) {
        this.gstDocumentService = gstDocumentService;
    }


    @PostMapping(
            value = "/shops/{shopId}/gst/upload",
            consumes = "multipart/form-data"
    )
    public ResponseEntity<String> uploadGST(
            @PathVariable Long shopId,
            @RequestParam("file") MultipartFile file
    ) {

        gstDocumentService.uploadGST(shopId, file);

        return ResponseEntity.ok("GST document uploaded successfully");
    }

}