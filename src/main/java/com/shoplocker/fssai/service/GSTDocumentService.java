package com.shoplocker.fssai.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.shoplocker.fssai.entity.GSTDocument;
import com.shoplocker.fssai.repository.GSTDocumentRepository;


@Service

public class GSTDocumentService{

    @Autowired

   private GSTDocumentRepository gstDocumentRepository;

    public GSTDocument saveGSTDocument(GSTDocument gstDocument){
        return gstDocumentRepository.save(gstDocument);
    }






}





