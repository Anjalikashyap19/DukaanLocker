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


}