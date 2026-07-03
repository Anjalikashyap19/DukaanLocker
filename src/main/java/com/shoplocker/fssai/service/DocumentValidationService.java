package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.exception.FssaiException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DocumentValidationService {

    /**
     * Validates the extracted text against the rules for the given document type.
     *
     * @param type           the type of document being validated
     * @param extractedText  the full text extracted by AWS Textract
     * @param originalFileName the original file name for error messages
     * @throws FssaiException if validation fails
     */
    public void validate(DocumentType type, String extractedText, String originalFileName) {
        switch (type) {
            case GST:
                validateGST(extractedText, originalFileName);
                break;
            case PAN:
                validatePAN(extractedText, originalFileName);
                break;
            case SHOP_ESTABLISHMENT:
                validateShopEstablishment(extractedText, originalFileName);
                break;
            case TRADE_LICENSE:
                validateTradeLicense(extractedText, originalFileName);
                break;
            case MSME:
                validateMSME(extractedText, originalFileName);
                break;
            case PROFESSIONAL_TAX:
                validateProfessionalTax(extractedText, originalFileName);
                break;
            case TRADEMARK:
                validateTrademark(extractedText, originalFileName);
                break;
            case PROPERTY_TAX:
                validatePropertyTax(extractedText, originalFileName);
                break;
            case IEC:
                validateIEC(extractedText, originalFileName);
                break;
            case POLLUTION_CONTROL:
                validatePollutionControl(extractedText, originalFileName);
                break;
            case FIRE_SAFETY:
                validateFireSafety(extractedText, originalFileName);
                break;
            case LABOUR_LICENSE:
                validateLabourLicense(extractedText, originalFileName);
                break;
            case SHOP_INSURANCE:
                validateShopInsurance(extractedText, originalFileName);
                break;
            case DRUG_LICENSE:
                validateDrugLicense(extractedText, originalFileName);
                break;
            case FSSAI:
                validateFSSAI(extractedText, originalFileName);
                break;
            case AADHAAR:
                validateAadhaar(extractedText, originalFileName);
                break;
            default:
                throw new FssaiException("Unknown document type: " + type);
        }
    }

    // ─── Validation helpers ──────────────────────────────────────────────────────

    private void checkContainsAll(String extractedText, String docDisplayName,
                                   String originalFileName, String... requiredKeywords) {
        List<String> missing = Arrays.stream(requiredKeywords)
                .filter(kw -> !containsIgnoreCase(extractedText, kw))
                .collect(Collectors.toList());

        if (!missing.isEmpty()) {
            String missingStr = String.join(", ", missing);
            throw new FssaiException(
                    "Uploaded file \"" + originalFileName + "\" does not appear to be a valid " + docDisplayName + ". " +
                    "Missing required field(s): " + missingStr + ". " +
                    "Please upload the correct " + docDisplayName + " document."
            );
        }
    }

    private void checkPattern(String extractedText, String docDisplayName,
                               String originalFileName, String fieldName,
                               Pattern pattern, String example) {
        if (pattern.matcher(extractedText).find()) return;

        throw new FssaiException(
                "Uploaded file \"" + originalFileName + "\" does not appear to be a valid " + docDisplayName + ". " +
                "Mandatory " + fieldName + " was not found. " +
                "Expected format: " + example + ". " +
                "Please upload the correct " + docDisplayName + " document."
        );
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        if (text == null || keyword == null) return false;
        return text.toLowerCase().contains(keyword.toLowerCase());
    }

    // ─── INDIAN GSTIN regex: 2-digit state, 10-char PAN, 1 digit, 1 check char, Z (default), 1 check digit ──
    private static final Pattern GSTIN_PATTERN =
            Pattern.compile("\\b[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}\\b");

    // ─── PAN regex: 5 letters, 4 digits, 1 letter ──
    private static final Pattern PAN_PATTERN =
            Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]{1}\\b");

    // ─── Aadhaar-like 12-digit number ──
    private static final Pattern AADHAAR_PATTERN =
            Pattern.compile("\\b[0-9]{12}\\b");

    // ─── IEC regex: 10 alphanumeric characters ──
    private static final Pattern IEC_PATTERN =
            Pattern.compile("\\b[A-Za-z0-9]{10}\\b");

    // ─── FSSAI License Number: 14 digits ──
    private static final Pattern FSSAI_PATTERN =
            Pattern.compile("\\b[0-9]{14}\\b");

    // =============================================================================
    //  GST Certificate
    // =============================================================================
    private void validateGST(String text, String fileName) {
        checkContainsAll(text, "GST Registration Certificate", fileName,
                "GSTIN", "Registration Certificate", "Goods and Services Tax");
        checkPattern(text, "GST Registration Certificate", fileName,
                "GSTIN", GSTIN_PATTERN, "e.g., 27ABCDE1234F1Z5");
    }

    // =============================================================================
    //  PAN Card
    // =============================================================================
    private void validatePAN(String text, String fileName) {
        checkContainsAll(text, "PAN Card", fileName,
                "Income Tax Department", "Permanent Account Number");
        checkPattern(text, "PAN Card", fileName,
                "PAN", PAN_PATTERN, "e.g., ABCDE1234F");
    }

    // =============================================================================
    //  Shop & Establishment License
    // =============================================================================
    private void validateShopEstablishment(String text, String fileName) {
        checkContainsAll(text, "Shop & Establishment License", fileName,
                "Shop", "Establishment", "License");
    }

    // =============================================================================
    //  Trade License
    // =============================================================================
    private void validateTradeLicense(String text, String fileName) {
        checkContainsAll(text, "Trade License", fileName,
                "Trade License", "Trade Licence");
    }

    // =============================================================================
    //  Udyam MSME Registration
    // =============================================================================
    private void validateMSME(String text, String fileName) {
        checkContainsAll(text, "Udyam MSME Registration", fileName,
                "Udyam", "MSME", "Registration");
    }

    // =============================================================================
    //  Professional Tax Registration
    // =============================================================================
    private void validateProfessionalTax(String text, String fileName) {
        checkContainsAll(text, "Professional Tax Registration", fileName,
                "Professional Tax", "PT", "Registration");
    }

    // =============================================================================
    //  Trademark
    // =============================================================================
    private void validateTrademark(String text, String fileName) {
        checkContainsAll(text, "Trademark Certificate", fileName,
                "Trademark", "Trade Mark");
    }

    // =============================================================================
    //  Property Tax Certificate
    // =============================================================================
    private void validatePropertyTax(String text, String fileName) {
        checkContainsAll(text, "Property Tax Certificate", fileName,
                "Property Tax");
    }

    // =============================================================================
    //  Import Export Code (IEC)
    // =============================================================================
    private void validateIEC(String text, String fileName) {
        checkContainsAll(text, "Import Export Code", fileName,
                "Import Export Code", "IEC");
        checkPattern(text, "Import Export Code", fileName,
                "IEC number", IEC_PATTERN, "10-character alphanumeric code");
    }

    // =============================================================================
    //  Pollution Control Certificate
    // =============================================================================
    private void validatePollutionControl(String text, String fileName) {
        checkContainsAll(text, "Pollution Control Certificate", fileName,
                "Pollution Control", "Consent");
    }

    // =============================================================================
    //  Fire Safety Certificate
    // =============================================================================
    private void validateFireSafety(String text, String fileName) {
        checkContainsAll(text, "Fire Safety Certificate", fileName,
                "Fire Safety", "Fire");
    }

    // =============================================================================
    //  Labour License / Workmen Compensation Policy
    // =============================================================================
    private void validateLabourLicense(String text, String fileName) {
        // Accept either Labour License or Workmen Compensation or both
        boolean hasLabour = containsIgnoreCase(text, "Labour");
        boolean hasWorkmen = containsIgnoreCase(text, "Workmen");
        boolean hasCompensation = containsIgnoreCase(text, "Compensation");

        if (!hasLabour && !(hasWorkmen && hasCompensation)) {
            throw new FssaiException(
                    "Uploaded file \"" + fileName + "\" does not appear to be a valid Labour License / Workmen Compensation Policy. " +
                    "Required keywords \"Labour\" or \"Workmen Compensation\" were not found. " +
                    "Please upload the correct Labour License / Workmen Compensation document."
            );
        }
    }

    // =============================================================================
    //  Shop Insurance
    // =============================================================================
    private void validateShopInsurance(String text, String fileName) {
        checkContainsAll(text, "Shop Insurance Policy", fileName,
                "Insurance", "Policy");
    }

    // =============================================================================
    //  Drug License
    // =============================================================================
    // =============================================================================
    //  FSSAI Certificate
    // =============================================================================
    private void validateFSSAI(String text, String fileName) {
        checkContainsAll(text, "FSSAI License", fileName,
                "FSSAI", "License Number", "Food Safety");
        checkPattern(text, "FSSAI License", fileName,
                "FSSAI License Number", FSSAI_PATTERN, "14-digit FSSAI license number");
    }

    // =============================================================================
    //  Aadhaar Card
    // =============================================================================
    private void validateAadhaar(String text, String fileName) {
        checkContainsAll(text, "Aadhaar Card", fileName,
                "Government of India", "Aadhaar");
        checkPattern(text, "Aadhaar Card", fileName,
                "12-digit Aadhaar number", AADHAAR_PATTERN, "e.g., 123456789012");
    }

    // =============================================================================
    //  Drug License
    // =============================================================================
    private void validateDrugLicense(String text, String fileName) {
        boolean hasDrugLicense = containsIgnoreCase(text, "Drug License");
        boolean hasDrugLicence = containsIgnoreCase(text, "Drug Licence");
        boolean hasFDA = containsIgnoreCase(text, "Food and Drug");

        if (!hasDrugLicense && !hasDrugLicence && !hasFDA) {
            throw new FssaiException(
                    "Uploaded file \"" + fileName + "\" does not appear to be a valid Drug License. " +
                    "Required keywords \"Drug License\" or \"Food and Drug\" were not found. " +
                    "Please upload the correct Drug License document."
            );
        }
    }
}
