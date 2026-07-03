package com.shoplocker.fssai.service;

import com.shoplocker.fssai.exception.FssaiException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

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
     * @param file the uploaded PDF or image file
     * @return the full extracted text from the document
     * @throws FssaiException if extraction fails
     */
    public String extractText(MultipartFile file) {
        try {
            // Read file bytes
            byte[] fileBytes = file.getBytes();

            // Build the Document from bytes
            Document document = Document.builder()
                    .bytes(SdkBytes.fromByteArray(fileBytes))
                    .build();

            // Build the DetectDocumentText request
            DetectDocumentTextRequest request = DetectDocumentTextRequest.builder()
                    .document(document)
                    .build();

            // Call Textract
            DetectDocumentTextResponse response = textractClient.detectDocumentText(request);

            // Extract all text lines
            List<Block> blocks = response.blocks();
            StringBuilder extractedText = new StringBuilder();

            for (Block block : blocks) {
                if (BlockType.LINE.equals(block.blockType())) {
                    extractedText.append(block.text()).append("\n");
                }
            }

            String result = extractedText.toString().trim();

            if (result.isEmpty()) {
                throw new FssaiException("No text could be extracted from the uploaded document. Please upload a clear, readable document.");
            }

            return result;

        } catch (IOException e) {
            throw new FssaiException("Failed to read uploaded file for text extraction.", e);
        } catch (FssaiException e) {
            throw e;
        } catch (Exception e) {
            throw new FssaiException("AWS Textract text extraction failed: " + e.getMessage(), e);
        }
    }
}
