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
     *   <li>{@code INVALID_FILE_FORMAT} (400) — could not read the bytes in process.</li>
     *   <li>{@code S3_UPLOAD_FAILED} (502) — AWS S3 itself errored.</li>
     * </ul>
     *
     * <p>User-facing messages stay generic; the {@code cause} carries the
     * full AWS error for server-side logs.</p>
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
                    "We couldn't read your file. Please try uploading it again — if the problem persists, contact support.",
                    FailureCode.INVALID_FILE_FORMAT, e);
        } catch (Exception e) {
            throw new FssaiException(
                    "We couldn't save your file just now. Please try again in a moment, or contact support if the problem persists.",
                    FailureCode.S3_UPLOAD_FAILED, e);
        }
    }
}
