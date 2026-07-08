package com.shoplocker.fssai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;

import java.io.IOException;

@Service
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.bucketName}")
    private String bucketName;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Uploads the file bytes to S3.
     *
     * <p>Failure semantics:</p>
     * <ul>
     *   <li>{@code INVALID_FILE_FORMAT} (400) — the bytes of {@code file} could not be read in
     *       this process (typically a corrupt multipart upload).</li>
     *   <li>{@code S3_UPLOAD_FAILED} (502) — AWS S3 PutObject itself failed
     *       (AccessDenied, throttling, network). Upstream issue, not the user's fault.</li>
     * </ul>
     */
    public String uploadFile(MultipartFile file, String fileKey) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .contentType(file.getContentType())
                    .build();
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
            return "https://" + bucketName + ".s3.amazonaws.com/" + fileKey;

        } catch (IOException e) {
            throw new FssaiException(
                    "Failed to read uploaded file before sending to S3. The file may be corrupted.",
                    FailureCode.INVALID_FILE_FORMAT, e);
        } catch (Exception e) {
            throw new FssaiException(
                    "AWS S3 upload failed: " + e.getMessage(),
                    FailureCode.S3_UPLOAD_FAILED, e);
        }
    }
}
