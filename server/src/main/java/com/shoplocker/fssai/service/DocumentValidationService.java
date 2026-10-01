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

    @org.springframework.beans.factory.annotation.Autowired
    private TextractService textractService;

    // ─── Minimum confidence threshold ───────────────────────────────────────
    /** Shared with {@link PdfPreprocessor}, which mirrors this floor when it
     *  decides whether a PDF's embedded text layer is usable. */
    static final int MIN_EXTRACTED_TEXT_LENGTH = 100;

    // ─── Business-name matching ─────────────────────────────────────────────
    /**
     * Escape hatch for the ownership check. It only ever *adds* a rejection, so
     * turning it off reverts to the previous behaviour (type check only) without
     * a deploy.
     */
    @org.springframework.beans.factory.annotation.Value("${app.validation.business-name-check.enabled:true}")
    private boolean businessNameCheckEnabled = true;

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(DocumentValidationService.class);

    /** Legal-entity suffixes: present on the certificate, usually absent from the trade name. */
    private static final Set<String> LEGAL_SUFFIX_TOKENS = new HashSet<>(Arrays.asList(
            "pvt", "private", "ltd", "limited", "llp", "inc", "incorporated", "corp",
            "corporation", "company", "co", "plc", "gmbh", "pty", "enterprises",
            "enterprise", "trading", "traders", "industries", "holdings", "group",
            "and", "the", "firm", "brothers", "sons"));

    /**
     * Words too generic to identify a business on their own. A shop name made
     * only of these leaves nothing to compare, so the check is skipped rather
     * than blocking every document.
 */    private static final Set<String> GENERIC_BUSINESS_TOKENS = new HashSet<>(Arrays.asList(
            "shop", "store", "business", "firm", "company", "restaurant", "cafe", "hotel",
            "trading", "services", "enterprises", "establishment"));

    /**
     * Name tokens shorter than this are matched exactly. One misread character
     * in a 3-letter token ("day" vs "dat") would otherwise match unrelated
     * documents, so short tokens get no tolerance at all.
     */
    private static final int FUZZY_MATCH_MIN_LENGTH = 5;

    /**
     * Name tokens longer than this are matched exactly too: by then a single
     * differing character means a genuinely different business, not an OCR glitch.
     */
    private static final int FUZZY_MATCH_MAX_LENGTH = 8;

    // ─── Conflict Signatures ───────────────────────────────────────────────
    private static final Map<String, List<String>> CONFLICT_SIGNATURES = new LinkedHashMap<>();
    static {
        CONFLICT_SIGNATURES.put("GST Registration Certificate",
                List.of("GSTIN", "Goods and Services Tax"));
        CONFLICT_SIGNATURES.put("PAN Card",
                List.of("Permanent Account Number", "Income Tax Department"));
        CONFLICT_SIGNATURES.put("Aadhaar Card",
                List.of("Aadhaar", "UIDAI", "Unique Identification Authority"));
        CONFLICT_SIGNATURES.put("FSSAI Food License",
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
                    "|Insurance\s+(?:Certificate|Policy)\s*[:/\\-]?\s*[A-Za-z0-9/-]{3,}");
    private static final Pattern PROPERTY_ID_PATTERN =
            Pattern.compile("(?i)(?:Property|Assessment|Ward)\\s*(?:ID|Number|No|#|:)?\\s*[A-Za-z0-9/\\-]+");
    private static final Pattern LABOUR_LICENSE_PATTERN =
            Pattern.compile("(?i)(?:License|Registration)\\s*(?:Number|No|:)?\\s*[A-Za-z0-9/\\-]+");
    private static final Pattern SHOP_EST_PATTERN =
            Pattern.compile("(?i)(?:Registration|License)\\s*(?:Number|No|:)?\\s*[A-Za-z0-9/-]+");
    // Label + number as Indian trade licences actually print it:
    //   "OLD TRADE LICENCE NO: 20004112613"   (West Bengal, British spelling)
    //   "Certificate No.- 0917P1008125372790"  (enlistment certificates)
    //   "License No: MH/12345"                 (Municipal Corporation format)
    // "Licen[cs]e" covers both spellings; the {4,} floor keeps a bare label
    // ("License No.") from satisfying the pattern on its own.
    private static final Pattern TRADE_LICENSE_PATTERN =
            Pattern.compile("(?i)(?:trade\\s+licen[cs]e|licen[cs]e|enlistment|certificate)"
                    + "\\s*(?:no|number|#)?\\.?\\s*[:.\\-]?\\s*[A-Za-z0-9/-]{4,}");

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final byte[] PDF_MAGIC  = {0x25, 0x50, 0x44, 0x46}; // "%PDF"
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8};            // JPEG SOI marker (universal)
    private static final byte[] PNG_MAGIC  = {(byte) 0x89, 0x50, 0x4E, 0x47};       // PNG signature (first 4 of 8)

    /** Identity match against a multi-byte magic header. Byte comparison is authoritative -
     *  Content-Type from MultipartFile is just a hint that can be spoofed. */
    private static boolean matchesMagic(byte[] bytes, byte[] magic) {
        if (bytes == null || bytes.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) if (bytes[i] != magic[i]) return false;
        return true;
    }

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
    /** Accepts PDF, JPEG, or PNG by identity-check of the magic header. The method name is
     *  preserved for backward compatibility with the 14 *DocumentService callers - but the
     *  body now accepts image uploads too. */
    public void assertPdfMagicBytes(byte[] fileBytes, String docDisplayName) {
        if (fileBytes == null) {
            throw new FssaiException(
                    "The uploaded file for " + docDisplayName + " is null. Please re-upload a real PDF, JPEG, or PNG document.",
                    FailureCode.INVALID_FILE_FORMAT);
        }
        if (matchesMagic(fileBytes, PDF_MAGIC)
                || matchesMagic(fileBytes, JPEG_MAGIC)
                || matchesMagic(fileBytes, PNG_MAGIC)) {
            return;
        }
        throw new FssaiException(
                "The uploaded file is not a valid " + docDisplayName + ". Expected PDF, JPEG, or PNG magic bytes at the "
                        + "start of the file, but the file does not contain them. Please upload a real PDF, JPEG, or PNG document.",
                FailureCode.INVALID_FILE_FORMAT);
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
            case MSME_CERTIFICATE:    validateMSME(extractedText, originalFileName); break;
            case PROFESSIONAL_TAX:   validateProfessionalTax(extractedText, originalFileName); break;
            case TRADEMARK:          validateTrademark(extractedText, originalFileName); break;
            case PROPERTY_TAX:       validatePropertyTax(extractedText, originalFileName); break;
            case IEC:                validateIEC(extractedText, originalFileName); break;
            case POLLUTION_CONTROL:  validatePollutionControl(extractedText, originalFileName); break;
            case FIRE_SAFETY:        validateFireSafety(extractedText, originalFileName); break;
            case LABOUR_LICENSE:     validateLabourLicense(extractedText, originalFileName); break;
            case SHOP_INSURANCE:     validateShopInsurance(extractedText, originalFileName); break;
            case DRUG_LICENSE:       validateDrugLicense(extractedText, originalFileName); break;
            case FSSAI_FOOD_LICENSE: validateFssaiFoodLicense(extractedText, originalFileName); break;
            case AADHAAR:            validateAadhaar(extractedText, originalFileName); break;
            case CUSTOM:             // Custom documents bypass content validation — no standard format to check
                break;
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
            case MSME_CERTIFICATE:    return "Udyam MSME Registration";
            case PROFESSIONAL_TAX:   return "Professional Tax Registration";
            case TRADEMARK:          return "Trademark Certificate";
            case PROPERTY_TAX:       return "Property Tax Certificate";
            case IEC:                return "Import Export Code (IEC)";
            case POLLUTION_CONTROL:  return "Pollution Control Certificate";
            case FIRE_SAFETY:        return "Fire Safety Certificate";
            case LABOUR_LICENSE:     return "Labour License / Workmen Compensation";
            case SHOP_INSURANCE:     return "Shop Insurance Policy";
            case DRUG_LICENSE:       return "Drug License";
            case FSSAI_FOOD_LICENSE: return "FSSAI Food License";
            case AADHAAR:            return "Aadhaar Card";
            default:                 return type.name();
        }
    }

    // =========================================================================
    //  INDIVIDUAL DOCUMENT VALIDATIONS
    // =========================================================================

    private void validateGST(String text, String fileName) {
        // "GSTIN" is the strongest discriminator — keep it REQUIRED.
        // Real certs may print only the short form "GST" instead of the long
        // form "Goods and Services Tax", and "Registration Certificate" is
        // sometimes just "Certificate of Registration". Accept any of those.
        requireAllKeywords(text, "GST Registration Certificate", fileName,
                "GSTIN");
        requireAnyKeyword(text, "GST Registration Certificate", fileName,
                "Tax name (full or short form)",
                "Goods and Services Tax", "Goods & Services Tax", "GST", "G.S.T.");
        requireAnyKeyword(text, "GST Registration Certificate", fileName,
                "Certificate wording",
                "Registration Certificate", "Certificate of Registration", "GST Registration Certificate");
        requirePattern(text, "GST Registration Certificate", fileName,
                "GSTIN (Goods and Services Tax Identification Number)",
                GSTIN_PATTERN,
                "e.g., 27ABCDE1234F1Z5");
    }

    private void validatePAN(String text, String fileName) {
        // "PAN" itself is the strongest discriminator — keep it REQUIRED.
        // The header on real cards varies: "Income Tax Department", just
        // "Income Tax", or even just the PAN line. Accept any.
        requireAllKeywords(text, "PAN Card", fileName,
                "PAN");
        requireAnyKeyword(text, "PAN Card", fileName,
                "Card heading",
                "Income Tax Department", "Income Tax", "Permanent Account Number");
        requirePattern(text, "PAN Card", fileName,
                "PAN (Permanent Account Number)",
                PAN_PATTERN,
                "10-character alphanumeric (e.g., ABCDE1234F)");
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
        // Title. Indian states word this differently: "Trade License" (most),
        // "Trade Licence" (British spelling), or a "Certificate of Enlistment"
        // (West Bengal Municipal Act, Form-24). No single phrasing may be
        // required or a genuine state licence is rejected out of hand.
        requireAnyKeyword(text, "Trade License", fileName,
                "Document title",
                "Trade License", "Trade Licence", "Certificate of Enlistment",
                "Shops & Establishments", "Registration of Trade",
                "Licensing of Trades");

        // Issuing authority. A trade licence can come from a Municipal
        // Corporation, a Municipality, a Nagar Nigam / Palika, a Panchayat or a
        // state department - and it is frequently state-level, so neither a
        // particular state nor a particular body may be assumed.
        requireAnyKeyword(text, "Trade License", fileName,
                "Issuing authority",
                "Municipal Corporation", "Municipality", "Municipal",
                "Corporation", "Nagar Nigam", "Nagar Palika", "Nagar Panchayat",
                "Gram Panchayat", "Panchayat", "Government of", "State Government",
                "Urban Development", "Labour Department", "Directorate",
                "Regional Transport Authority", "District Magistrate");

        // Licence-number label. "Licence" is as common as "License" on the
        // originals, and enlistment certificates label it differently again.
        requireAnyKeyword(text, "Trade License", fileName, "License Number",
                "License Number", "Licence Number", "License No", "Licence No",
                "Trade License Number", "Trade Licence Number",
                "Enlistment No", "Certificate No", "Unique Number");

        requirePattern(text, "Trade License", fileName,
                "Trade License Number",
                TRADE_LICENSE_PATTERN,
                "An alphanumeric license number");

        requireAnyKeyword(text, "Trade License", fileName,
                "Validity / Issuing Details",
                "Valid", "Validity", "Issue Date", "Issued",
                "in force", "Issuance", "Date of Issuance", "until", "renewal");
    }

    private void validateMSME(String text, String fileName) {
        // "Udyam" is the strongest discriminator — keep it REQUIRED.
        // Real Udyam certs do NOT always print "Government of India" verbatim,
        // and "MSME" can appear as the long "Micro, Small & Medium Enterprises".
        // The Udyam registration-number regex below is the actual hard gate.
        requireAllKeywords(text, "Udyam MSME Registration", fileName,
                "Udyam");
        requireAnyKeyword(text, "Udyam MSME Registration", fileName,
                "Scheme / ministry name (full or short form)",
                "MSME", "Micro, Small & Medium Enterprises", "Small & Medium Enterprises");
        requireAnyKeyword(text, "Udyam MSME Registration", fileName,
                "Certificate wording",
                "Registration Certificate", "Certificate of Registration", "Registration");
        requireAnyKeyword(text, "Udyam MSME Registration", fileName,
                "Ministry / Governing Body",
                "Ministry of Micro", "Ministry of MSME", "Small & Medium Enterprises", "Government of India");
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
        // "IEC" is the strongest discriminator — keep it REQUIRED. Real IEC
        // certs do NOT always print "Directorate General of Foreign Trade" in
        // full; they often just say "DGFT". And the all-caps "Government of
        // India" headline is sometimes OCR'd inconsistently.
        requireAllKeywords(text, "Import Export Code (IEC)", fileName,
                "IEC");
        requireAnyKeyword(text, "Import Export Code (IEC)", fileName,
                "Document heading",
                "Import Export Code", "Importer Exporter Code", "IEC Certificate", "IEC Allotment");
        requireAnyKeyword(text, "Import Export Code (IEC)", fileName,
                "Issuing Authority",
                "Directorate General of Foreign Trade", "DGFT", "Government of India", "Ministry of Commerce", "Foreign Trade");
        requirePattern(text, "Import Export Code (IEC)", fileName,
                "IEC Number (10-character alphanumeric)",
                IEC_PATTERN,
                "10-character alphanumeric code (e.g., AA1234567890)");
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
                "Sum Insured", "Total Premium", "Coverage Period", "Effective From", "Valid Till", "Expiry Date");
        requireAnyKeyword(text, "Shop Insurance Policy", fileName,
                "Insurance Company",
                "Insurance Company", "Insurance Co", "Insurer", "ICICI Lombard", "TATA AIG", "HDFC ERGO", "Bajaj Allianz", "New India Assurance", "Oriental Insurance", "Cholamandalam");
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

    private void validateFssaiFoodLicense(String text, String fileName) {
        // "FSSAI" is the strongest discriminator — keep it REQUIRED.
        // The long form "Food Safety and Standards Authority of India" gets
        // OCR'd inconsistently; accept short forms too. The 14-digit number
        // regex below is the actual hard gate.
        requireAllKeywords(text, "FSSAI Food License", fileName,
                "FSSAI");
        requireAnyKeyword(text, "FSSAI Food License", fileName,
                "License phrasing",
                "License Number", "License No", "Licence Number", "Licence No", "Certificate");
        requireAnyKeyword(text, "FSSAI Food License", fileName,
                "Issuing Authority (full or short form)",
                "Food Safety and Standards Authority of India", "Food Safety and Standards Authority", "FSSAI Authority", "Government of India");
        requirePattern(text, "FSSAI Food License", fileName,
                "FSSAI License Number (14-digit)",
                FSSAI_PATTERN,
                "14-digit FSSAI license number");
        requireAnyKeyword(text, "FSSAI Food License", fileName,
                "Business / Validity",
                "Food Business Operator", "FBO", "Valid", "Validity", "Category", "Kind of Business");
    }

    private void validateAadhaar(String text, String fileName) {
        // Real Aadhaar cards don't always print "Government of India" verbatim —
        // sometimes only the Aadhaar branding. Keep only "Aadhaar" as the hard
        // requirement and accept any of the common header variants below.
        // The 12-digit Aadhaar-number regex below is the actual hard gate.
        requireAllKeywords(text, "Aadhaar Card", fileName,
                "Aadhaar");
        requireAnyKeyword(text, "Aadhaar Card", fileName,
                "Header / brand",
                "Government of India", "Unique Identification Authority", "UIDAI",
                "Unique Identification", "Aadhaar Card");
        requirePattern(text, "Aadhaar Card", fileName,
                "12-digit Aadhaar Number",
                AADHAAR_PATTERN,
                "12-digit number (e.g., 123456789012)");
    }

    /**
     * Runs OCR (AWS Textract) on the already-loaded PDF bytes and then the
     * per-type content validation. This centralises the same validation the
     * per-type {@code *DocumentService} upload pipelines perform, so the unified
     * re-upload endpoint ({@code ShopController}) holds documents to the same
     * compliance bar instead of accepting unvalidated PDFs.
     *
     * @return the OCR-extracted text, so callers can mine it for metadata
     *         (e.g. expiry dates) without running OCR twice
     */
    public String validateContentWithOcr(DocumentType docType, byte[] fileBytes, String fileName) {
        String extractedText = textractService.extractText(fileBytes, fileName);
        validate(docType, extractedText, fileName);
        return extractedText;
    }

    // ─── Business ownership (does this certificate belong to THIS shop?) ───

    /**
     * Verifies that the uploaded certificate actually belongs to the shop being
     * onboarded, by looking for the shop's name in the OCR text.
     *
     * <p>See {@link #validateBusinessOwnership(DocumentType, String, String,
     * String, String)}; this form passes no proprietor name.</p>
     */
    public void validateBusinessOwnership(DocumentType docType,
                                          String shopName,
                                          String extractedText,
                                          String fileName) {
        validateBusinessOwnership(docType, shopName, null, extractedText, fileName);
    }

    /**
     * Verifies that the uploaded certificate actually belongs to the shop being
     * onboarded, by looking for the shop's trade name <em>or</em> its proprietor's
     * name in the OCR text.
     *
     * <p>Complements {@link #checkDocumentConflict(DocumentType, String, String)},
     * which only asks "is this the right <em>kind</em> of document?". A genuine
     * FSSAI licence belonging to somebody else's shop passes that check, so this
     * is the cross-business check the upload flow was missing.</p>
     *
     * <p>A match on <b>either</b> identity passes. State trade licences and
     * certificates of enlistment are frequently issued in the proprietor's
     * personal name while the profile carries the brand name ("New Market",
     * proprietor "Kavinder Sehgal"), and the reverse happens just as often with
     * proprietary firms.</p>
     *
     * <p>Matching is deliberately <b>lenient</b>: a certificate prints the
     * registered legal entity, which routinely differs from the trade name the
     * seller typed into their profile ("cafe Coffee Day" vs "Coffee Day
     * Enterprises Pvt Ltd"). So the check fails only on a <b>confident
     * mismatch</b> — neither name's distinctive words appear anywhere in the
     * document. That is the case the user means by "the document is some other
     * business". Formatting noise, OCR mangling of a single character, and added
     * legal suffixes all still pass.</p>
     *
     * @param docType       expected document type, used for the error message
     * @param shopName      the shop's trade name as registered on the profile
     * @param ownerName     the proprietor's name on the profile, or null
     * @param extractedText OCR text of the uploaded file
     * @param fileName      original file name, used for the error message
     * @throws FssaiException {@link FailureCode#BUSINESS_NAME_MISMATCH} when the
     *                        document does not look like it belongs to this shop
     */
    public void validateBusinessOwnership(DocumentType docType,
                                          String shopName,
                                          String ownerName,
                                          String extractedText,
                                          String fileName) {
        if (!businessNameCheckEnabled) return;

        // Too little OCR text to judge. validateContentWithOcr already requires
        // 100+ chars, so this only guards direct callers; erring toward
        // accepting is deliberate — a bad scan must not read as "wrong business".
        if (extractedText == null || extractedText.length() < MIN_EXTRACTED_TEXT_LENGTH) {
            log.warn("Skipping business-name check for {}: only {} chars of OCR text",
                    fileName, extractedText == null ? 0 : extractedText.length());
            return;
        }

        // No profile name to check against: never block on missing data.
        List<String> shopTokens = significantTokens(shopName);
        List<String> ownerTokens = significantTokens(ownerName);
        if (shopTokens.isEmpty() && ownerTokens.isEmpty()) {
            log.warn("Skipping business-name check: shop name '{}' and owner name '{}'"
                            + " have no significant words (file={})",
                    shopName, ownerName, fileName);
            return;
        }

        String normalizedText = normalizeForMatching(extractedText);
        boolean anyMatch = anyTokenPresent(normalizedText, shopTokens)
                || anyTokenPresent(normalizedText, ownerTokens);

        if (!anyMatch) {
            String expectedName = getDisplayName(docType);
            String display = displayName(shopName, ownerName);
            throw new FssaiException(
                    "This document does not appear to belong to your shop \"" + display + "\". "
                            + "Your shop name was not found in the " + expectedName
                            + ", so it looks like this certificate was issued to a different business. "
                            + "Please upload a " + expectedName + " issued to \"" + display + "\".",
                    FailureCode.BUSINESS_NAME_MISMATCH,
                    java.util.List.of(
                            "shop_name: " + display,
                            "document_type: " + expectedName
                    ));
        }
    }

    private boolean anyTokenPresent(String normalizedText, List<String> tokens) {
        return tokens.stream().anyMatch(token -> containsToken(normalizedText, token));
    }

    /** The name to quote back to the seller: the trade name, else the proprietor. */
    private String displayName(String shopName, String ownerName) {
        if (shopName != null && !shopName.isBlank()) return shopName.trim();
        if (ownerName != null && !ownerName.isBlank()) return ownerName.trim();
        return "";
    }

    /**
     * Normalises text for tolerant name matching: lower-cases, folds the common
     * ampersand/spelling variants, and strips punctuation so "cafe-coffee-day",
     * "Café Coffee Day" and "cafe coffee day" all reduce to the same shape.
     */
    private String normalizeForMatching(String text) {
        String s = text.toLowerCase(Locale.ENGLISH)
                .replace("&", " and ")
                .replaceAll("[^a-z0-9\\s]", " ");
        // Collapse the spacing the substitutions above leave behind.
        return s.replaceAll("\\s+", " ").trim();
    }

    /**
     * Splits a shop name into the words that actually identify it.
     *
     * <p>Drops legal suffixes (the certificate will carry its own, and the trade
     * name typed on the profile usually will not) and short filler words, so
     * "Maruthi Traders Private Limited" is compared as just {maruthi, traders}.
     * </p>
     */
    private List<String> significantTokens(String name) {
        if (name == null || name.isBlank()) return new ArrayList<>();
        String normalized = normalizeForMatching(name);
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (token.isBlank()) continue;
            if (token.length() < 3 && !token.chars().anyMatch(Character::isDigit)) continue;
            if (LEGAL_SUFFIX_TOKENS.contains(token)) continue;
            if (GENERIC_BUSINESS_TOKENS.contains(token)) continue;
            tokens.add(token);
        }
        return tokens;
    }

    /**
     * True when {@code token} appears in the document text as a <em>word</em>.
     *
     * <p>At least one edge of the match must touch a word boundary, so the shop
     * "New Market" does not match every "renew**al**" clause in a licence while
     * "cafe" still matches the "cafe" in "cafes". An interior match is a
     * coincidence of spelling, never an identity.</p>
     *
     * <p>Failing that, a mid-length token is allowed one character of OCR noise,
     * because single-word shop names ("Verma", "Shah") are exactly the ones
     * where a single misread character would otherwise read as a completely
     * different business. Very short and very long tokens stay exact - see
     * {@link #FUZZY_MATCH_MIN_LENGTH}.</p>
     */
    private boolean containsToken(String normalizedText, String token) {
        if (token == null || token.isEmpty()) return false;
        if (matchesAtWordEdge(normalizedText, token)) return true;

        if (token.length() < FUZZY_MATCH_MIN_LENGTH || token.length() > FUZZY_MATCH_MAX_LENGTH) {
            return false;
        }

        int width = token.length();
        for (int i = 0; i + width <= normalizedText.length(); i++) {
            if (!isWordEdge(normalizedText, i, i + width)) continue;
            String window = normalizedText.substring(i, i + width);
            if (editDistanceWithin(token, window, 1)) return true;
        }
        return false;
    }

    /** True when some occurrence of {@code token} sits at a word edge. */
    private boolean matchesAtWordEdge(String text, String token) {
        int from = 0;
        while (from <= text.length() - token.length()) {
            int idx = text.indexOf(token, from);
            if (idx < 0) return false;
            if (isWordEdge(text, idx, idx + token.length())) return true;
            from = idx + 1;
        }
        return false;
    }

    /**
     * True when {@code [start, end)} abuts a non-word character (or the ends of
     * the text) on either side. The text has already been reduced to letters,
     * digits and spaces by {@link #normalizeForMatching(String)}, so a word
     * character is simply a letter or digit.
     */
    private boolean isWordEdge(String text, int start, int end) {
        boolean before = start == 0 || !isWordChar(text.charAt(start - 1));
        boolean after = end == text.length() || !isWordChar(text.charAt(end));
        return before || after;
    }

    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }

    /** Bounded Levenshtein: true when {@code a} and {@code b} are within {@code max} edits. */
    private boolean editDistanceWithin(String a, String b, int max) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int rowBest = current[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
                rowBest = Math.min(rowBest, current[j]);
            }
            if (rowBest > max) return false;
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()] <= max;
    }
}
