package com.shoplocker.fssai.service;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.shoplocker.fssai.entity.GSTDocument;
import com.shoplocker.fssai.repository.GSTDocumentRepository;

import org.springframework.web.multipart.MultipartFile;
import com.shoplocker.fssai.entity.Shop;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
@Service

public class GSTDocumentService{

    @Autowired
   private GSTDocumentRepository gstDocumentRepository;

    @Autowired
    private ShopService shopService;

    @Autowired
    private S3Service s3Service;


    public void uploadGST(Long shopId ,MultipartFile  file ){

        Shop shop =shopService.getShopById(shopId);

        Optional<GSTDocument> existingDocument =gstDocumentRepository.findByShop(shop);
    GSTDocument gstDocument;

        if (existingDocument.isPresent()){
            gstDocument=existingDocument.get();

        }else{
            gstDocument=new GSTDocument();
            gstDocument.setShop(shop);

        }
        validateGSTFile(file);
        String fileKey = generateGSTFileName(shopId);
        String fileUrl = s3Service.uploadFile(file, fileKey);

        gstDocument.setOriginalFileName(file.getOriginalFilename());

        gstDocument.setUploadedFileName(fileKey);

        gstDocument.setFileUrl(fileUrl);

        gstDocument.setUploadedAt(LocalDateTime.now());


        gstDocumentRepository.save(gstDocument);

    }

    private void validateGSTFile(MultipartFile file){
        if(file==null || file.isEmpty()){
            throw new RuntimeException("please upload a gst certificate");
        }
        if(!"application/pdf".equals(file.getContentType())){
            throw new RuntimeException("only pdf files are allowed");
        }
        long maxSize = 5 * 1024 * 1024;
        if(file.getSize()>maxSize){
            throw new RuntimeException("pdf file should be less than 5mb");
        }
    }

    private String generateGSTFileName(Long shopId){
        String uniqueId= UUID.randomUUID().toString();
        return "/gst/shop_"+shopId +"/gst"+shopId+"_"+uniqueId+".pdf";

    }






}





