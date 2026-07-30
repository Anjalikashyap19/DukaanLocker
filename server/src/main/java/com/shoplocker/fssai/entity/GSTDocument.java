package com.shoplocker.fssai.entity;

import jakarta.persistence.*;


import java.time.LocalDateTime;


@Entity
@Table(name="gstdoc")
public class GSTDocument {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne
    @JoinColumn(name = "shop_id", unique= true)


    private Shop shop;
    private String originalFileName;
    private String uploadedFileName;
    private String fileUrl;
    private LocalDateTime uploadedAt;



    public Long getId(){
        return id;
    }
    public void setId(Long id){
        this.id=id;
    }
    public Shop getShop(){
        return shop;

    }
    public void setShop(Shop shop){
        this.shop=shop;
    }

    public String getOriginalFileName(){
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName){
        this.originalFileName=originalFileName;
    }

    public String getUploadedFileName() {
        return uploadedFileName;
    }
    public void setUploadedFileName(String uploadedFileName){
        this.uploadedFileName=uploadedFileName;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl){
        this.fileUrl=fileUrl;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt){
        this.uploadedAt=uploadedAt;
    }
}