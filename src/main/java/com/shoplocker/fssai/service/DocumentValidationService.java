package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Validates uploaded documents at three levels:
 * <ol>
 *   <li>{@link #validateFileFormat(MultipartFile, String)} — basic file
 *       metadata (size, content type). Cheap, no IO.</li>
 *   <li>{@link #assertPdfMagicBytes(byte[], String)} — confirms the file is a
 *       real PDF (operates on an already-loaded byte[]).</li>
 *   <li>{@link #validate(DocumentType, String, String)} — content keywords /
 *       ID regex after OCR. Throws {@link FailureCode#DOCUMENT_VALIDATION_FAILED}
 *       for missing fields and {@link FailureCode#DOCUMENT_TYPE_MISMATCH} when
 *       the file appears to be a different document type entirely.</li>
 * </ol>
 *
 * <p>Together with {@link #readBytes(MultipartFile)} this lets the upload
 * pipeline read the {@code MultipartFile} exactly once (inside
 * {@code readBytes}) and then reuse the same {@code byte[]} for Textract,
 * S3, and any future downstream consumer.</p>
 */
@Service
public class DocumentValidationService {

    // ─── Minimum confidence threshold ───────────────────────────────────────
    private static final int MIN_EXTRACTED_TEXT_LENGTH = 100;

    // ─── Conflict Signatures ───────────────────────────────────────────────
    private static final Map<String, List<String>> CONFLICT_SIGNATURES = new LinkedHashMap<>();
    static {
        CONFLICT_SIGNATURES.put("GST Registration Certificate",
                List.of("GSTIN", "Goods and Services Tax"));
        CONFLICT_SIGNATURES.put("PAN Card",
                List.of("Permanent Account Number", "Income Tax Department"));
        CONFLICT_SIGNATURES.put("Aadhaar Card",
                List.of("Aadhaar", "UIDAI", "Unique Identification Authority"));
        CONFLICT_SIGNATURES.put("FSSAI License",
                List.of("FSSAI", "Food Safety and Standards Authority"));
        CONFLICT_SIGNATURES.put("Udyam MSME Registration",
                List.of("Udyam", "Ministry of Micro, Small & Medium Enterprises"));
        CONFLICT_SIGNATURES.put("Import Export Code (IEC)",
                List.of("Import Export Code", "Directorate General of Foreign Trade"));
        CONFLICT_SIGNATURES.put("Trademark Certificate",
                List.of("Trade Marks Act, 1999", "Trademark Journal"));
        CONFLICT_SIGNATURES.put("Drug License",
                List.of("Drugs and Cosmetics Act", "Food and Drug Administration"));
        CONFLICT_SIGNATURES.put("Pollution Control Certificate",
                List.of("Consent to Establish", "Consent to Operate", "Pollution Control Board"));
        CONFLICT_SIGNATURES.put("Fire Safety Certificate",
                List.of("Fire Prevention", "NOC from Fire"));
        CONFLICT_SIGNATURES.put("Labour License / Workmen Compensation",
                List.of("Contract Labour", "Workmen Compensation"));
        CONFLICT_SIGNATURES.put("Shop & Establishment License",
                List.of("Shops and Establishments Act", "Establishment License"));
        CONFLICT_SIGNATURES.put("Trade License",
                List.of("Municipal Corporation", "Trade License"));
        CONFLICT_SIGNATURES.put("Professional Tax Registration",
                List.of("Professional Tax", "Enrollment Certificate"));
        CONFLICT_SIGNATURES.put("Property Tax Certificate",
                List.of("Property Tax", "Property ID"));
        CONFLICT_SIGNATURES.put("Shop Insurance Policy",
                List.of("Insurance Policy", "Sum Insured"));
    }

    // ─── Regex Patterns for Official ID Numbers ────────────────────────────
    private static final Pattern GSTIN_PATTERN =
            Pattern.compile("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]");
    private static final Pattern PAN_PATTERN =
            Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]");
    private static final Pattern AADHAAR_PATTERN =
            Pattern.compile("\\b[0-9]{12}\\b");
    private static final Pattern FSSAI_PATTERN =
            Pattern.compile("\\b[0-9]{14}\\b");
    private static final Pattern IEC_PATTERN =
            Pattern.compile("\\b[A-Za-z0-9]{10}\\b");
    private static final Pattern UDYAM_PATTERN =
            Pattern.compile("(?i)UDYAM[-][A-Za-z0-9]{2}[-][A-Za-z0-9]{2}[-][A-Za-z0-9]{7}");
    private static final Pattern DRUG_LICENSE_PATTERN =
            Pattern.compile("\\b[0-9]{2}[-][0-9]{2}[-][A-Za-z0-9-]+\\b");
    private static final Pattern TRADEMARK_PATTERN =
            Pattern.compile("\\b[0-9]{6,7}\\b");
    private static final Pattern FIRE_NOC_PATTERN =
            Pattern.compile("(?i)(?:NOC|Certificate)\\s*[:\\.]?\\s*[A-Za-z0-9/-]+");
    // Three branches so we accept any real PT enrollment-number format:
    //   1. PTEC/PTRC/PTC literal prefix + 4+ alphanumeric (e.g. PTEC123456, PTRC987654)
    //   2. "Professional Tax Enrollment Certificate/Cert/Number/No" + alphanumeric
    //   3. "Professional Tax Number/No" + alphanumeric (real certs that omit "Enrollment")
    private static final Pattern PTEC_PATTERN =
            Pattern.compile("(?i)(?:PTEC|PTRC|PTC)\\s*[-/:#.]?\\s*[A-Za-z0-9/-]{4,}" +
                    "|Professional\\s+Tax\\s+[Ee]nrol?ment\\s+(?:Certificate|Cert|Number|No)\\s*[:-]?\\s*[A-Za-z0-9/-]{2,}" +
                    "|Professional\\s+Tax\\s+(?:Number|No)\\s*[:-]?\\s*[A-Za-z0-9/-]{2,}");

    // Shop Insurance Policy: accept common policy-number formats and Certificate of Insurance.
    // We need an ID suffix (reject bare "Insurance Policy" alone) - same tightening rationale as PTEC_PATTERN.
    private static final Pattern POLICY_PATTERN =
            Pattern.compile("(?i)Policy\s*[-/:#.]?\s*[A-Za-z0-9/-]{4,}" +
                    "|Certificate\s+of\s+Insurance\s*[:-]?\s*[A-Za-z0-9/-]{3,}" +
                    "|Insurance\s+(?:Certificate|Policy)\s*[:-/]?\s*[A-Za-z0-9/-]{3,}");
    private static final Pattern PROPERTY_ID_PATTERN =
            Pattern.compile("(?i)(?:Property|Assessment|Ward)\\s*(?:ID|Number|No|#|:)?\\s*[A-Za-z0-9/\\-]+");
    private static final Pattern LABOUR_LICENSE_PATTERN =
            Pattern.compile("(?i)(?:License|Registration)\\s*(?:Number|No|:)?\\s*[A-Za-z0-9/\\-]+");
    private static final Pattern SHOP_EST_PATTERN =
            Pattern.compile("(?i)(?:Registration|License)\\s*(?:Number|No|:)?\\s*[A-Za-z0-9/-]+");
    private static final Pattern TRADE_LICENSE_PATTERN =
            Pattern.compile("(?i)(?:Trade License|License)\\s*(?:Number|No|:)?\\s*[A-Za-z0-9/-]+");

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46}; // "%PDF"

    // =========================================================================
    //  FILE-FORMAT HELPERS (cheap metadata + magic-byte check on byte[])
    // =========================================================================

    /**
     * Cheap metadata-only validation: file present, has PDF content-type, and
     * fits under the 5 MB ceiling. Does NOT touch the file contents.
     *
     * <p>After this returns, callers should {@link #readBytes(MultipartFile)}
     * to fetch the bytes once, then {@link #assertPdfMagicBytes(byte[], String)}
     * to confirm the file is a real PDF before sending to Textract.</p>
     */
    public void validateFileFormat(MultipartFile file, String docDisplayName) {
        if (file == null || file.isEmpty()) {
            throw new FssaiException(
                    "No file uploaded. Please upload the " + docDisplayName + " (PDF format).",
                    FailureCode.INVALID_FILE_FORMAT);
        }
        String contentType = file.getContentType();
        if (!"application/pdf".equals(contentType)) {
            throw new FssaiException(
                    "Only PDF files are accepted for " + docDisplayName + ". " +
                            "The uploaded file has Content-Type \"" + contentType + "\". " +
                            "Please upload a PDF document, not " + (contentType == null ? "an unknown file type" : contentType) + ".",
                    FailureCode.INVALID_FILE_FORMAT);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new FssaiException(
                    "The uploaded file is too large (" + file.getSize() + " bytes). " +
                            "Maximum allowed size for " + docDisplayName + " is " + MAX_FILE_SIZE_BYTES + " bytes " +
                            "(5 MB). Please upload a smaller PDF.",
                    FailureCode.INVALID_FILE_FORMAT);
        }
    }

    /**
     * Read the {@link MultipartFile} into a byte array — the SINGLE point of
     * contact between the upload pipeline and the multipart source. The same
     * {@code byte[]} is then passed to {@link TextractService} and
     * {@link S3Service} so the file is read from disk at most once per request.
     */
    public byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new FssaiException(
                    "We couldn't read your file just now. Please try uploading it again — if the problem persists, contact support.",
                    FailureCode.INVALID_FILE_FORMAT, e);
        }
    }

    /**
     * Confirms the first 4 bytes of {@code fileBytes} spell out {@code %PDF}.
     * Operating on the byte[] (not on a fresh stream) keeps the I/O cost at
     * zero — the bytes were already loaded by {@link #readBytes(MultipartFile)}.
     */
    public void assertPdfMagicBytes(byte[] fileBytes, String docDisplayName) {
        if (fileBytes == null || fileBytes.length < 4) {
            throw new FssaiException(
                    "The uploaded file for " + docDisplayName + " is empty or truncated. Please re-upload a complete PDF.",
                    FailureCode.INVALID_FILE_FORMAT);
        }
        if (fileBytes[0] != PDF_MAGIC[0] || fileBytes[1] != PDF_MAGIC[1] ||
                fileBytes[2] != PDF_MAGIC[2] || fileBytes[3] != PDF_MAGIC[3]) {
            throw new FssaiException(
                    "The uploaded file is not a valid " + docDisplayName + " PDF. Expected PDF magic bytes '%PDF' at the " +
                            "start of the file, but the file does not contain them. Please upload a real PDF document.",
                    FailureCode.INVALID_FILE_FORMAT);
        }
    }

    // =========================================================================
    //  CONTENT VALIDATION (post-OCR)
    // =========================================================================

    /**
     * Validates the OCR'd text against strict rules for the given document type.
     *
     * @throws FssaiException with code {@link FailureCode#DOCUMENT_VALIDATION_FAILED}
     *                        or {@link FailureCode#DOCUMENT_TYPE_MISMATCH}
     */
    public void validate(DocumentType type, String extractedText, String originalFileName) {
        ensureMinTextLength(extractedText, originalFileName);

        // Cross-contamination FIRST. If the OCR'd text matches another document
        // type's signatures (>=2 of that type's keywords), surface
        // DOCUMENT_TYPE_MISMATCH so the UI can tell the user
        // "you uploaded X, we need Y" — the most actionable error. This runs
        // before the per-type validators so a wholly wrong upload doesn't get
        // dismissed as a long list of "missing fields".
        checkDocumentConflict(type, extractedText, originalFileName);

        switch (type) {
            case GST:                validateGST(extractedText, originalFileName); break;
            case PAN:                validatePAN(extractedText, originalFileName); break;
            case SHOP_ESTABLISHMENT: validateShopEstablishment(extractedText, originalFileName); break;
            case TRADE_LICENSE:      validateTradeLicense(extractedText, originalFileName); break;
            case MSME:               validateMSME(extractedText, originalFileName); break;
            case PROFESSIONAL_TAX:   validateProfessionalTax(extractedText, originalFileName); break;
            case TRADEMARK:          validateTrademark(extractedText, originalFileName); break;
            case PROPERTY_TAX:       validatePropertyTax(extractedText, originalFileName); break;
            case IEC:                validateIEC(extractedText, originalFileName); break;
            case POLLUTION_CONTROL:  validatePollutionControl(extractedText, originalFileName); break;
            case FIRE_SAFETY:        validateFireSafety(extractedText, originalFileName); break;
            case LABOUR_LICENSE:     validateLabourLicense(extractedText, originalFileName); break;
            case SHOP_INSURANCE:     validateShopInsurance(extractedText, originalFileName); break;
            case DRUG_LICENSE:       validateDrugLicense(extractedText, originalFileName); break;
            case FSSAI:              validateFSSAI(extractedText, originalFileName); break;
            case AADHAAR:            validateAadhaar(extractedText, originalFileName); break;
            default: throw new FssaiException("Unknown document type: " + type, FailureCode.INTERNAL_ERROR);
        }
    }

    private void ensureMinTextLength(String text, String fileName) {
        if (text == null || text.trim().length() < MIN_EXTRACTED_TEXT_LENGTH) {
            throw new FssaiException(
                    "The uploaded file \"" + fileName + "\" does not contain enough readable text to verify its contents. " +
                            "Only " + (text == null ? 0 : text.trim().length()) + " characters were extracted. " +
                            "Please upload a clear, high-quality PDF of the required document.",
                    FailureCode.DOCUMENT_VALIDATION_FAILED);
        }
    }

    private void requireAllKeywords(String text, String docDisplayName, String fileName,
                                     String... requiredKeywords) {
        List<String> missing = new ArrayList<>();
        for (String kw : requiredKeywords) {
            if (!containsIgnoreCase(text, kw)) {
                missing.add("\"" + kw + "\"");
            }
        }
        if (!missing.isEmpty()) {
            String missingStr = String.join(", ", missing);
            java.util.List<String> details = new java.util.ArrayList<>();
            details.add("expected_document_type: " + docDisplayName);
            for (String m : missing) {
                details.add("missing_field: " + m);
            }
            throw new FssaiException(
                    "This file doesn't look like a " + docDisplayName + ". " +
                            "We're missing these required field(s): " + missingStr + ". " +
                            "Please upload a correct " + docDisplayName + " document.",
                    FailureCode.DOCUMENT_VALIDATION_FAILED,
                    details);
        }
    }

    private void requireAnyKeyword(String text, String docDisplayName, String fileName,
                                    String fieldName, String... options) {
        for (String opt : options) {
            if (containsIgnoreCase(text, opt)) return;
        }
        String optionsStr = String.join(" or ", options);
        throw new FssaiException(
                "Uploaded file \"" + fileName + "\" is not a valid " + docDisplayName + ". " +
                        "Required field \"" + fieldName + "\" not found. " +
                        "Expected to find: " + optionsStr + ". " +
                        "Please upload a correct " + docDisplayName + " document.",
                FailureCode.DOCUMENT_VALIDATION_FAILED);
    }

    private void requirePattern(String text, String docDisplayName, String fileName,
                                 String fieldName, Pattern pattern, String example) {
        if (pattern.matcher(text).find()) return;
        throw new FssaiException(
                "Uploaded file \"" + fileName + "\" is not a valid " + docDisplayName + ". " +
                        "Mandatory \"" + fieldName + "\" could not be found. " +
                        "Expected format: " + example + ". " +
                        "Please upload a correct " + docDisplayName + " document.",
                FailureCode.DOCUMENT_VALIDATION_FAILED);
    }

    /**
     * Cross-contamination check: detect if a *different* document type's signatures
     * are present in the OCR text and reject with a dedicated code so the UI can
     * show "you uploaded X, we need Y".
     */
    private void checkDocumentConflict(DocumentType expectedType, String text, String fileName) {
        String expectedName = getDisplayName(expectedType);

        for (Map.Entry<String, List<String>> entry : CONFLICT_SIGNATURES.entrySet()) {
            String otherDocName = entry.getKey();

            if (otherDocName.equalsIgnoreCase(expectedName)) continue;

            List<String> signatures = entry.getValue();
            int matchCount = 0;
            for (String sig : signatures) {
                if (containsIgnoreCase(text, sig)) matchCount++;
            }

            if (matchCount >= 2) {
                throw new FssaiException(
                        "This file doesn't look like the " + expectedName + " we expected. " +
                                "Its contents appear to be a " + otherDocName + ". " +
                                "Please upload the correct " + expectedName + " document.",
                        FailureCode.DOCUMENT_TYPE_MISMATCH,
                        java.util.List.of(
                                "expected_document_type: " + expectedName,
                                "detected_document_type: " + otherDocName
                        ));
            }
        }
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        if (text == null || keyword == null) return false;
        return text.toLowerCase(Locale.ENGLISH).contains(keyword.toLowerCase(Locale.ENGLISH));
    }

    private String getDisplayName(DocumentType type) {
        switch (type) {
            case GST:                return "GST Registration Certificate";
            case PAN:                return "PAN Card";
            case SHOP_ESTABLISHMENT: return "Shop & Establishment License";
            case TRADE_LICENSE:      return "Trade License";
            case MSME:               return "Udyam MSME Registration";
            case PROFESSIONAL_TAX:   return "Professional Tax Registration";
            case TRADEMARK:          return "Trademark Certificate";
            case PROPERTY_TAX:       return "Property Tax Certificate";
            case IEC:                return "Import Export Code (IEC)";
            case POLLUTION_CONTROL:  return "Pollution Control Certificate";
            case FIRE_SAFETY:        return "Fire Safety Certificate";
            case LABOUR_LICENSE:     return "Labour License / Workmen Compensation";
            case SHOP_INSURANCE:     return "Shop Insurance Policy";
            case DRUG_LICENSE:       return "Drug License";
            case FSSAI:              return "FSSAI License";
            case AADHAAR:            return "Aadhaar Card";
            default:                 return type.name();
        }
    }

    // =========================================================================
    //  INDIVIDUAL DOCUMENT VALIDATIONS
    // =========================================================================

    private void validateGST(String text, String fileName) {
        requireAllKeywords(text, "GST Registration Certificate", fileName,
                "GSTIN",
                "Registration Certificate",
                "Goods and Services Tax");
        requirePattern(text, "GST Registration Certificate", fileName,
                "GSTIN (Goods and Services Tax Identification Number)",
                GSTIN_PATTERN,
                "e.g., 27ABCDE1234F1Z5");
        requireAnyKeyword(text, "GST Registration Certificate", fileName,
                "Issuing Authority / Government",
                "Tax Department", "Government of India", "GST Council");
    }

    private void validatePAN(String text, String fileName) {
        requireAllKeywords(text, "PAN Card", fileName,
                "Income Tax Department",
                "Permanent Account Number",
                "PAN");
        requirePattern(text, "PAN Card", fileName,
                "PAN (Permanent Account Number)",
                PAN_PATTERN,
                "10-character alphanumeric (e.g., ABCDE1234F)");
        requireAnyKeyword(text, "PAN Card", fileName, "Government / Authority",
                "Government of India", "Income Tax");
    }

    private void validateShopEstablishment(String text, String fileName) {
        requireAllKeywords(text, "Shop & Establishment License", fileName,
                "Shops and Establishments Act",
                "Establishment",
                "Registration");
        requireAnyKeyword(text, "Shop & Establishment License", fileName,
                "License / Registration Number",
                "Registration Number", "License Number", "Registration No");
        requirePattern(text, "Shop & Establishment License", fileName,
                "Registration/License Number",
                SHOP_EST_PATTERN,
                "An alphanumeric registration number (e.g., SHA/12345/2024)");
        requireAnyKeyword(text, "Shop & Establishment License", fileName,
                "Issuing Authority",
                "Government", "Municipal", "Labour Department", "Labour Commissioner");
    }

    private void validateTradeLicense(String text, String fileName) {
        requireAllKeywords(text, "Trade License", fileName,
                "Trade License",
                "Municipal Corporation");
        requireAnyKeyword(text, "Trade License", fileName, "License Number",
                "License Number", "License No", "Trade License Number");
        requirePattern(text, "Trade License", fileName,
                "Trade License Number",
                TRADE_LICENSE_PATTERN,
                "An alphanumeric license number");
        requireAnyKeyword(text, "Trade License", fileName,
                "Validity / Issuing Details",
                "Valid", "Validity", "Issue Date", "Issued");
    }

    private void validateMSME(String text, String fileName) {
        requireAllKeywords(text, "Udyam MSME Registration", fileName,
                "Udyam",
                "MSME",
                "Registration Certificate",
                "Government of India");
        requireAnyKeyword(text, "Udyam MSME Registration", fileName,
                "Ministry / Governing Body",
                "Ministry of Micro", "Ministry of MSME", "Small & Medium Enterprises");
        requirePattern(text, "Udyam MSME Registration", fileName,
                "Udyam Registration Number",
                UDYAM_PATTERN,
                "UDYAM-XX-XX-XXXXXXX format");
    }

    private void validateProfessionalTax(String text, String fileName) {
        requireAllKeywords(text, "Professional Tax Registration", fileName,
                "Professional Tax",
                "Registration",
                "Tax");
        requireAnyKeyword(text, "Professional Tax Registration", fileName,
                "Certificate Type / Enrollment",
                "Enrollment Certificate", "Registration Certificate", "PTEC", "PTRC");
        requirePattern(text, "Professional Tax Registration", fileName,
                "Professional Tax Enrollment Number",
                PTEC_PATTERN,
                "An alphanumeric enrollment number (e.g., PTEC123456)");
        requireAnyKeyword(text, "Professional Tax Registration", fileName,
                "State / Authority",
                "Government of", "State", "Sales Tax", "Commercial Tax");
    }

    private void validateTrademark(String text, String fileName) {
        requireAllKeywords(text, "Trademark Certificate", fileName,
                "Trademark",
                "Trade Marks Act");
        requireAnyKeyword(text, "Trademark Certificate", fileName,
                "Trademark Variations",
                "Trade Mark", "Trade Marks Act, 1999");
        requirePattern(text, "Trademark Certificate", fileName,
                "Trademark Application/Registration Number",
                TRADEMARK_PATTERN,
                "6-7 digit application/registration number");
        requireAnyKeyword(text, "Trademark Certificate", fileName,
                "Issuing Office",
                "Registry", "Government of India", "Intellectual Property");
    }

    private void validatePropertyTax(String text, String fileName) {
        requireAllKeywords(text, "Property Tax Certificate", fileName,
                "Property Tax",
                "Receipt");
        requireAnyKeyword(text, "Property Tax Certificate", fileName,
                "Certificate Type",
                "Certificate", "Challan", "Paid", "Payment");
        requireAnyKeyword(text, "Property Tax Certificate", fileName,
                "Municipal / Local Body",
                "Municipal", "Corporation", "Nagar Nigam", "Municipal Council");
        requirePattern(text, "Property Tax Certificate", fileName,
                "Property ID / Assessment Number",
                PROPERTY_ID_PATTERN,
                "An alphanumeric property ID or assessment number");
    }

    private void validateIEC(String text, String fileName) {
        requireAllKeywords(text, "Import Export Code (IEC)", fileName,
                "Import Export Code",
                "IEC",
                "Government of India",
                "Directorate General of Foreign Trade");
        requirePattern(text, "Import Export Code (IEC)", fileName,
                "IEC Number (10-character alphanumeric)",
                IEC_PATTERN,
                "10-character alphanumeric code (e.g., AA1234567890)");
        requireAnyKeyword(text, "Import Export Code (IEC)", fileName,
                "DGFT / Issuing Office",
                "DGFT", "Foreign Trade", "Ministry of Commerce");
    }

    private void validatePollutionControl(String text, String fileName) {
        requireAllKeywords(text, "Pollution Control Certificate", fileName,
                "Pollution Control",
                "Consent");
        requireAnyKeyword(text, "Pollution Control Certificate", fileName,
                "Consent Type",
                "Consent to Establish", "Consent to Operate", "CTE", "CTO");
        requireAnyKeyword(text, "Pollution Control Certificate", fileName,
                "Board / Authority",
                "Pollution Control Board", "PCB", "State Pollution");
        requireAnyKeyword(text, "Pollution Control Certificate", fileName,
                "Certificate Details",
                "Certificate", "Order", "Number", "Validity", "Valid");
    }

    private void validateFireSafety(String text, String fileName) {
        requireAllKeywords(text, "Fire Safety Certificate", fileName,
                "Fire Safety",
                "NOC");
        requireAnyKeyword(text, "Fire Safety Certificate", fileName,
                "Fire Department Mention",
                "Fire Department", "Fire Service", "Fire Prevention", "Fire Brigade");
        requirePattern(text, "Fire Safety Certificate", fileName,
                "NOC / Certificate Number",
                FIRE_NOC_PATTERN,
                "A certificate or NOC reference number");
        requireAnyKeyword(text, "Fire Safety Certificate", fileName,
                "Validity / Premises Details",
                "Valid", "Validity", "Building", "Premises", "Occupancy");
    }

    private void validateLabourLicense(String text, String fileName) {
        boolean isLabourLicense = containsIgnoreCase(text, "Labour License")
                || containsIgnoreCase(text, "Contract Labour")
                || containsIgnoreCase(text, "Labour Department");

        boolean isWorkmenComp = containsIgnoreCase(text, "Workmen")
                && containsIgnoreCase(text, "Compensation");

        if (!isLabourLicense && !isWorkmenComp) {
            throw new FssaiException(
                    "Uploaded file \"" + fileName + "\" is not a valid Labour License / Workmen Compensation Policy. " +
                            "Required fields were not found. " +
                            "Expected to find either: " +
                            "\"Labour License\" or \"Contract Labour\" (for Labour License), " +
                            "or BOTH \"Workmen\" AND \"Compensation\" (for Workmen Compensation Policy). " +
                            "Please upload a correct Labour License or Workmen Compensation document.",
                    FailureCode.DOCUMENT_VALIDATION_FAILED);
        }

        requireAnyKeyword(text, "Labour License / Workmen Compensation", fileName,
                "License/Policy Number",
                "License Number", "License No", "Policy Number", "Policy No", "Registration Number");

        requirePattern(text, "Labour License / Workmen Compensation", fileName,
                "License/Policy Number",
                LABOUR_LICENSE_PATTERN,
                "An alphanumeric license or policy number");
    }

    private void validateShopInsurance(String text, String fileName) {
        requireAllKeywords(text, "Shop Insurance Policy", fileName,
                "Insurance",
                "Policy");
        requireAnyKeyword(text, "Shop Insurance Policy", fileName,
                "Insurance Type",
                "Shop Insurance", "Shopkeepers", "Business Insurance", "General Insurance");
        requirePattern(text, "Shop Insurance Policy", fileName,
                "Policy Number",
                POLICY_PATTERN,
                "An alphanumeric policy number (e.g., Policy No: SHOP123456)");
        requireAnyKeyword(text, "Shop Insurance Policy", fileName,
                "Policy Details",
                "Sum Insured", "Premium", "Period", "From", "To", "Validity");
        requireAnyKeyword(text, "Shop Insurance Policy", fileName,
                "Insurance Company",
                "Insurance Company", "Insurance Co", "Insurer");
    }

    private void validateDrugLicense(String text, String fileName) {
        requireAllKeywords(text, "Drug License", fileName,
                "Drug License");
        requireAnyKeyword(text, "Drug License", fileName,
                "License Type",
                "Drug Licence", "Drugs and Cosmetics Act", "Food and Drug");
        requirePattern(text, "Drug License", fileName,
                "Drug License Number",
                DRUG_LICENSE_PATTERN,
                "License number (e.g., 21-21-ABCD-2024)");
        requireAnyKeyword(text, "Drug License", fileName,
                "Issuing Authority",
                "Food and Drug", "FDA", "Drug Administration", "Health Department");
        requireAnyKeyword(text, "Drug License", fileName,
                "License Category",
                "Sale", "Wholesale", "Retail", "Manufacturing", "Distribution");
    }

    private void validateFSSAI(String text, String fileName) {
        requireAllKeywords(text, "FSSAI License", fileName,
                "FSSAI",
                "License Number",
                "Food Safety and Standards Authority of India");
        requirePattern(text, "FSSAI License", fileName,
                "FSSAI License Number (14-digit)",
                FSSAI_PATTERN,
                "14-digit FSSAI license number");
        requireAnyKeyword(text, "FSSAI License", fileName,
                "Business / Validity",
                "Food Business Operator", "FBO", "Valid", "Validity", "Category");
    }

    private void validateAadhaar(String text, String fileName) {
        requireAllKeywords(text, "Aadhaar Card", fileName,
                "Government of India",
                "Aadhaar");
        requireAnyKeyword(text, "Aadhaar Card", fileName,
                "UIDAI / Unique Identification",
                "UIDAI", "Unique Identification Authority", "Unique Identification");
        requirePattern(text, "Aadhaar Card", fileName,
                "12-digit Aadhaar Number",
                AADHAAR_PATTERN,
                "12-digit number (e.g., 123456789012)");
    }
}
