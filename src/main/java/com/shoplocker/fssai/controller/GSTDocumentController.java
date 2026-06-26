package com.shoplocker.fssai.controller;


import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.shoplocker.fssai.entity.GSTDocument;
import com.shoplocker.fssai.entity.Status;
import com.shoplocker.fssai.entity.UploadType;
import com.shoplocker.fssai.service.GSTDocumentService;


@RestController
@RequestMapping("/docs")
public class GSTDocumentController {


    @Autowired
    private GSTDocumentService gstDocumentService;



    @GetMapping("/{id}")
    public GSTDocument getGSTById(@PathVariable Long id){
        return gstDocumentService.getGstDocumentById(id);
    }



    @DeleteMapping("/{id}")
    public void deleteGstDocumentById(@PathVariable Long id){
        gstDocumentService.deleteGstDocumentById(id);
    }




    // Manual document upload

    @PostMapping(
            value="/manual-upload",
            consumes="multipart/form-data"
    )
    public ResponseEntity<?> manualUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("gstNumber") String gstNumber
    ) throws IOException {


        GSTDocument obj = new GSTDocument();


        obj.setGstNumber(gstNumber);

        obj.setFile(file.getBytes());

        obj.setPdfPath(file.getOriginalFilename());

        obj.setUploadType(UploadType.MANUAL);

        obj.setStatus(Status.UPLOADED);



        gstDocumentService.saveGSTDocument(obj);


        return ResponseEntity.ok("Manual document uploaded");
    }





    // Automatic fetch using GST number

    @PostMapping("/fetch")
    public ResponseEntity<?> fetchDocument(
            @RequestParam("gstNumber") String gstNumber
    ){


        // abhi government API logic yaha add hoga

        byte[] pdf = gstDocumentService.fetchGSTDocument(gstNumber);



        GSTDocument obj = new GSTDocument();


        obj.setGstNumber(gstNumber);

        obj.setFile(pdf);

        obj.setPdfPath(gstNumber + ".pdf");

        obj.setUploadType(UploadType.AUTO);

        obj.setStatus(Status.FETCHED);



        gstDocumentService.saveGSTDocument(obj);



        return ResponseEntity.ok("GST document fetched");
    }



}