package com.shoplocker.fssai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;

import java.io.InputStream;

/**
 * Handles S3 operations for document storage and retrieval.
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
 * document — no garbage, no false-positive uploads.</p>
 *
 * <p>Failure semantics:</p>
 * <ul>
 *   <li>{@code S3_UPLOAD_FAILED} (502) — AWS S3 itself errored.</li>
 *   <li>{@code S3_OBJECT_NOT_FOUND} (404) — Requested object does not exist.</li>
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

    /**
     * Uploads file bytes to S3 and returns the public URL.
     *
     * @param fileBytes    The file content as byte array
     * @param contentType  MIME type of the file (e.g., "application/pdf")
     * @param fileKey      S3 object key (path in the bucket)
     * @return The S3 object URL
     */
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

    /**
     * Retrieves an InputStream for a file in S3.
     * Used for secure document streaming without exposing S3 URLs.
     *
     * @param fileKey  S3 object key (path in the bucket)
     * @return InputStream for reading the file content
     * @throws FssaiException if the object does not exist or cannot be retrieved
     */
    public InputStream getObject(String fileKey) {
        if (fileKey == null || fileKey.isEmpty()) {
            throw new FssaiException(
                    "Invalid file key provided.",
                    FailureCode.INVALID_FILE_FORMAT);
        }

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();
            return s3Client.getObject(getObjectRequest);
        } catch (Exception e) {
            throw new FssaiException(
                    "The requested document could not be retrieved. It may have been deleted or is temporarily unavailable.",
                    FailureCode.S3_OBJECT_NOT_FOUND, e);
        }
    }

    /**
     * Gets the file size of an object in S3.
     * Used to set Content-Length header for streaming responses.
     *
     * @param fileKey  S3 object key (path in the bucket)
     * @return File size in bytes, or -1 if unable to determine
     */
    public long getObjectSize(String fileKey) {
        if (fileKey == null || fileKey.isEmpty()) {
            return -1;
        }

        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();
            HeadObjectResponse response = s3Client.headObject(headObjectRequest);
            return response.contentLength();
        } catch (Exception e) {
            // Log but don't fail - Content-Length is optional
            return -1;
        }
    }

    /**
     * Extracts the S3 object key from a full S3 URL.
     * Example: "https://bucket.s3.amazonaws.com/pan/shop_1/pan_card.pdf" -> "pan/shop_1/pan_card.pdf"
     *
     * @param fileUrl  Full S3 URL
     * @return Extracted object key
     */
    public String extractObjectKeyFromFileUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }
        
        // Pattern: https://bucket.s3.amazonaws.com/key
        String prefix = "https://" + bucketName + ".s3.amazonaws.com/";
        if (fileUrl.startsWith(prefix)) {
            return fileUrl.substring(prefix.length());
        }
        
        // Fallback: try to extract key after bucket domain
        String genericPrefix = ".s3.amazonaws.com/";
        int idx = fileUrl.indexOf(genericPrefix);
        if (idx > 0) {
            return fileUrl.substring(idx + genericPrefix.length());
        }
        
        return null;
    }
}
