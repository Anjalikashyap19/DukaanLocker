package com.shoplocker.fssai.service;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;


@Service
public class S3Service {


    @Autowired
    private S3Client s3Client;


    @Value("${aws.bucketName}")
    private String bucketName;



    public String uploadFile(MultipartFile file, String fileKey) {


        try {

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .contentType(file.getContentType())
                    .build();


            s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromBytes(file.getBytes())
            );


            return "https://"
                    + bucketName
                    + ".s3.amazonaws.com/"
                    + fileKey;


        } catch (IOException e) {

            throw new RuntimeException("File upload failed");

        }

    }

}