package com.shoplocker.fssai.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;


@Entity
@Table(name="gstdoc")
public class GSTDocument {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] file;



    @NotBlank(message = "gst number is required for fetching")
    private String gstNumber;


    private String pdfPath;



    @Enumerated(EnumType.STRING)
    private Status status;



    @Enumerated(EnumType.STRING)
    private UploadType uploadType;



    @Column(updatable = false)
    private LocalDateTime createdAt;


    private LocalDateTime updatedAt;



    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }



    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }



    // GETTERS SETTERS


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public byte[] getFile() {
        return file;
    }

    public void setFile(byte[] file) {
        this.file = file;
    }


    public String getGstNumber() {
        return gstNumber;
    }

    public void setGstNumber(String gstNumber) {
        this.gstNumber = gstNumber;
    }


    public String getPdfPath() {
        return pdfPath;
    }

    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
    }


    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }


    public UploadType getUploadType() {
        return uploadType;
    }

    public void setUploadType(UploadType uploadType) {
        this.uploadType = uploadType;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

}