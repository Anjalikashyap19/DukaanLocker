package com.shoplocker.fssai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;

/**
 * Uploads ALREADY-VALIDATED file bytes to S3.
 *
 * <p>This service intentionally does NOT validate the file. By the time it is
 * called, the caller (i.e. a {@code *DocumentService}) has already:
 * <ol>
 *   <li>Checked basic file format (size, content type, PDF magic bytes)</li>
 *   <li>Run OCR via {@link TextractService} on the same {@code byte[]}</li>
 *   <li>Validated the OCR'd content against the expected document type</li>
 * </ol>
 *
 * <p>Only after all three gates pass is {@code uploadFile} called. Anything
 * that lands in S3 is therefore guaranteed to be a real, correctly-typed
 * document — no garbage, no false-positive uploads.
 *
 * <p>Failure semantics:</p>
 * <ul>
 *   <li>{@code S3_UPLOAD_FAILED} (502) — AWS S3 itself errored.</li>
 * </ul>
 *
 * <p>User-facing messages stay generic; raw AWS errors live in {@code cause}.</p>
 */
@Service
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.bucketName}")
    private String bucketName;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String uploadFile(byte[] fileBytes, String contentType, String fileKey) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new FssaiException(
                    "We couldn't save your file because it appears to be empty. Please re-upload the document.",
                    FailureCode.INVALID_FILE_FORMAT);
        }

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileBytes));
            return "https://" + bucketName + ".s3.amazonaws.com/" + fileKey;

        } catch (Exception e) {
            throw new FssaiException(
                    "We couldn't save your file just now. Please try again in a moment, or contact support if the problem persists.",
                    FailureCode.S3_UPLOAD_FAILED, e);
        }
    }
}
