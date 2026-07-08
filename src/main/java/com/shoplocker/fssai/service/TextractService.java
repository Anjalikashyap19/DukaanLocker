package com.shoplocker.fssai.service;

import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import java.util.List;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;
import software.amazon.awssdk.services.textract.model.UnsupportedDocumentException;
import software.amazon.awssdk.services.textract.model.Document;

/**
 * Sends the file bytes directly to AWS Textract and extracts all text content.
 *
 * <p>Intentionally takes {@code byte[]} (not {@code MultipartFile} or {@code S3Object})
 * so that:
 * <ul>
 *   <li>Validation (size, magic bytes, OCR keywords) can run BEFORE the file ever
 *       leaves the upload server. An invalid document never reaches S3.</li>
 *   <li>{@code TextractService} is decoupled from Spring's web layer.</li>
 *   <li>The same {@code byte[]} that we hand to Textract later goes to S3
 *       (no duplicate read).</li>
 * </ul>
 *
 * <p>Failure semantics:</p>
 * <ul>
 *   <li>{@code INVALID_FILE_FORMAT} (400) — Textract returned no text at all
 *       (blank / scanned-image-only / low-resolution PDF).</li>
 *   <li>{@code TEXTRACT_FAILURE} (502) — AWS Textract service itself failed
 *       (AccessDenied, throttle, network). Upstream issue.</li>
 * </ul>
 *
 * <p>User-facing messages stay generic; raw AWS text (ARNs, request IDs) is
 * preserved in the {@code cause} for server-side observability.</p>
 */
@Service
public class TextractService {

    private final TextractClient textractClient;

    public TextractService(TextractClient textractClient) {
        this.textractClient = textractClient;
    }

    /**
     * Sends {@code fileBytes} to AWS Textract and returns the concatenated
     * LINE-block text. Never reads from S3 — the bytes are passed in directly.
     */
    public String extractText(byte[] fileBytes, String fileName) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new FssaiException(
                    "The uploaded file \"" + (fileName == null ? "" : fileName) + "\" is empty. Please upload a non-empty PDF document.",
                    FailureCode.INVALID_FILE_FORMAT);
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
                        "We couldn't read any text from \"" + fileName + "\". It may be a blank PDF, an image-only PDF without an OCR layer, or scanned at too low a resolution. Please upload a clear, text-based PDF of the required document.",
                        FailureCode.INVALID_FILE_FORMAT);
            }
            return result;

        } catch (FssaiException e) {
            throw e;
        } catch (UnsupportedDocumentException e) {
            // Deterministic failure: Textract's strict parser rejects the PDF because it falls outside
            // its supported spec (PDF 2.0 in some regions, password-protected, AcroForms, or a corrupted
            // XREF table). A retry will not fix this - the bytes are rejected every time. User must
            // flatten (Print > Save as PDF) and re-upload. Other Textract failures (throttling, IAM, size)
            // remain 502 - distinct operational responses.
            throw new FssaiException(
                    "This PDF uses features our system can't read. The easiest fix: open the file, select 'Print', choose 'Save as PDF', and upload the flattened file.",
                    FailureCode.UNSUPPORTED_DOCUMENT_FORMAT, e);
        } catch (Exception e) {
            // AWS-side failure (AccessDenied, throttle, network). Generic user message;
            // the full exception is logged via the cause.
            throw new FssaiException(
                    "We couldn't verify your file right now — our document verifier is temporarily unavailable. Please try again in a few minutes, or contact support if the problem persists.",
                    FailureCode.TEXTRACT_FAILURE, e);
        }
    }
}
