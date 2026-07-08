package com.shoplocker.fssai.service;

import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;
import software.amazon.awssdk.services.textract.model.Document;

import java.io.IOException;
import java.util.List;

@Service
public class TextractService {

    private final TextractClient textractClient;

    public TextractService(TextractClient textractClient) {
        this.textractClient = textractClient;
    }

    /**
     * Sends the uploaded file to AWS Textract and extracts all text content.
     *
     * <p>Failure semantics:</p>
     * <ul>
     *   <li>{@code INVALID_FILE_FORMAT} (400) — the file is unreadable (IOException)
     *       or Textract returned no text at all (blank / scanned-image-only / scanned at too
     *       low a resolution). This is a problem with the user's file.</li>
     *   <li>{@code TEXTRACT_FAILURE} (502) — AWS Textract service itself failed
     *       (AccessDenied, throttle, network). This is an upstream issue, not the user's fault.</li>
     * </ul>
     */
    public String extractText(MultipartFile file) {
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new FssaiException(
                    "Failed to read the uploaded file before OCR. The file may be corrupted or unreadable.",
                    FailureCode.INVALID_FILE_FORMAT, e);
        }

        try {
            Document document = Document.builder()
                    .bytes(SdkBytes.fromByteArray(fileBytes))
                    .build();

            DetectDocumentTextRequest request = DetectDocumentTextRequest.builder()
                    .document(document)
                    .build();

            DetectDocumentTextResponse response = textractClient.detectDocumentText(request);

            List<Block> blocks = response.blocks();
            StringBuilder extractedText = new StringBuilder();
            for (Block block : blocks) {
                if (BlockType.LINE.equals(block.blockType())) {
                    extractedText.append(block.text()).append("\n");
                }
            }

            String result = extractedText.toString().trim();
            if (result.isEmpty()) {
                throw new FssaiException(
                        "AWS Textract returned no readable text for the uploaded file. " +
                                "The PDF may be blank, image-based without an OCR layer, or scanned at too " +
                                "low a resolution to read. Please upload a clearer PDF.",
                        FailureCode.INVALID_FILE_FORMAT);
            }
            return result;

        } catch (FssaiException e) {
            throw e;
        } catch (Exception e) {
            // SDK-level failure (AccessDenied, network, throttling, etc.)
            throw new FssaiException(
                    "AWS Textract call failed: " + e.getMessage(),
                    FailureCode.TEXTRACT_FAILURE, e);
        }
    }
}
