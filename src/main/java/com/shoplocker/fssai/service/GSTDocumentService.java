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


    public GSTDocument getGstDocumentById(Long id){
        return gstDocumentRepository.findById(id).orElse(null);
    }

    public void deleteGstDocumentById(Long id){
        gstDocumentRepository.deleteById(id);

    }

    // Stub method for fetching GST document from government API
    // TODO: Implement actual government API integration
    public byte[] fetchGSTDocument(String gstNumber) {
        // Placeholder: government API logic will be added here
        // Return empty bytes until real API integration is done
        return new byte[0];
    }

}





