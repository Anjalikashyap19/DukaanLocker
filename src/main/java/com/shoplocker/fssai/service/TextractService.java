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
     *       or Textract returned no text at all (blank / scanned-image-only / low-resolution).</li>
     *   <li>{@code TEXTRACT_FAILURE} (502) — AWS Textract service itself failed
     *       (AccessDenied, throttle, network). Upstream issue.</li>
     * </ul>
     *
     * <p>The user-facing message is intentionally generic — raw AWS text
     * (ARNs, request IDs) is not surfaced to callers but is preserved in
     * the {@code cause} for server-side observability.</p>
     */
    public String extractText(MultipartFile file) {
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new FssaiException(
                    "We couldn't read your file just now. Please try uploading again — if the problem persists, contact support.",
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
                        "We couldn't read any text from this file. It may be a blank PDF, an image-only PDF without an OCR layer, or scanned at too low a resolution. Please upload a clear, text-based PDF of the required document.",
                        FailureCode.INVALID_FILE_FORMAT);
            }
            return result;

        } catch (FssaiException e) {
            throw e;
        } catch (Exception e) {
            // AWS-side failure (AccessDenied, throttle, network). Keep the
            // user message generic — the full exception is logged via the cause.
            throw new FssaiException(
                    "We couldn't verify your file right now — our document verifier is temporarily unavailable. Please try again in a few minutes, or contact support if the problem persists.",
                    FailureCode.TEXTRACT_FAILURE, e);
        }
    }
}
