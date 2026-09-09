package com.shoplocker.fssai.util;

import com.shoplocker.fssai.dto.MsmeParsedData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Udyam Registration Certificate HTML to extract enterprise details.
 * <p>
 * The government portal returns an HTML page with a table structure containing
 * key-value pairs for each field. This parser extracts the relevant data using
 * both table-cell matching and regex patterns for robustness.
 * </p>
 */
public final class MsmeDataParser {

    private static final Logger log = LoggerFactory.getLogger(MsmeDataParser.class);

    // Udyam number pattern: UDYAM-XX-XX-XXXXXXX
    private static final Pattern UDYAM_PATTERN = Pattern.compile(
            "UDYAM-[A-Z]{2}-\\d{2}-\\d{7}", Pattern.CASE_INSENSITIVE);

    // Indian mobile number pattern (10 digits starting with 6-9)
    private static final Pattern MOBILE_PATTERN = Pattern.compile(
            "\\b[6-9]\\d{9}\\b");

    // Email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    // Pincode pattern (6 digits)
    private static final Pattern PINCODE_PATTERN = Pattern.compile(
            "\\b\\d{6}\\b");

    // NIC 2-digit code pattern (e.g., "47 - Retail trade")
    private static final Pattern NIC_2DIGIT_PATTERN = Pattern.compile(
            "\\b\\d{2}\\s*[-–:]\\s*[A-Za-z]", Pattern.CASE_INSENSITIVE);

    // NIC 4-digit code pattern (e.g., "4751 - Retail sale of textiles")
    private static final Pattern NIC_4DIGIT_PATTERN = Pattern.compile(
            "\\b\\d{4}\\s*[-–:]\\s*[A-Za-z]", Pattern.CASE_INSENSITIVE);

    // NIC 5-digit code pattern (e.g., "47510 - Retail sale of textiles")
    private static final Pattern NIC_5DIGIT_PATTERN = Pattern.compile(
            "\\b\\d{5}\\s*[-–:]\\s*[A-Za-z]", Pattern.CASE_INSENSITIVE);

    // ─── NIC Activity to DukaanLocker Category Mapping ──────────────────
    private static final Map<String, String> NIC_CATEGORY_MAP = new HashMap<>();

    static {
        // Retail & Trading
        NIC_CATEGORY_MAP.put("RETAIL", "GENERAL STORE");
        NIC_CATEGORY_MAP.put("WHOLESALE", "GENERAL STORE");
        NIC_CATEGORY_MAP.put("TRADING", "GENERAL STORE");
        NIC_CATEGORY_MAP.put("COMMERCE", "GENERAL STORE");
        NIC_CATEGORY_MAP.put("SHOP", "GENERAL STORE");
        NIC_CATEGORY_MAP.put("STORE", "GENERAL STORE");
        NIC_CATEGORY_MAP.put("MARKET", "GENERAL STORE");

        // Food & Grocery
        NIC_CATEGORY_MAP.put("FOOD", "GROCERY");
        NIC_CATEGORY_MAP.put("GROCERY", "GROCERY");
        NIC_CATEGORY_MAP.put("PROVISION", "GROCERY");
        NIC_CATEGORY_MAP.put("KIRANA", "GROCERY");
        NIC_CATEGORY_MAP.put("FRUIT", "GROCERY");
        NIC_CATEGORY_MAP.put("VEGETABLE", "GROCERY");
        NIC_CATEGORY_MAP.put("DAIRY", "GROCERY");
        NIC_CATEGORY_MAP.put("MILK", "GROCERY");
        NIC_CATEGORY_MAP.put("BAKERY", "RESTAURANT");

        // Restaurant & Hospitality
        NIC_CATEGORY_MAP.put("RESTAURANT", "RESTAURANT");
        NIC_CATEGORY_MAP.put("HOTEL", "RESTAURANT");
        NIC_CATEGORY_MAP.put("CAFE", "RESTAURANT");
        NIC_CATEGORY_MAP.put("FOOD SERVICE", "RESTAURANT");
        NIC_CATEGORY_MAP.put("CATERING", "RESTAURANT");
        NIC_CATEGORY_MAP.put("LODGING", "RESTAURANT");
        NIC_CATEGORY_MAP.put("HOSPITALITY", "RESTAURANT");

        // Manufacturing
        NIC_CATEGORY_MAP.put("MANUFACTURING", "MANUFACTURING");
        NIC_CATEGORY_MAP.put("PRODUCTION", "MANUFACTURING");
        NIC_CATEGORY_MAP.put("FACTORY", "MANUFACTURING");
        NIC_CATEGORY_MAP.put("INDUSTRY", "MANUFACTURING");
        NIC_CATEGORY_MAP.put("PROCESSING", "MANUFACTURING");
        NIC_CATEGORY_MAP.put("ASSEMBLY", "MANUFACTURING");
        NIC_CATEGORY_MAP.put("FABRICATION", "MANUFACTURING");
        NIC_CATEGORY_MAP.put("WORKSHOP", "MANUFACTURING");

        // Healthcare & Medical
        NIC_CATEGORY_MAP.put("HEALTH", "MEDICAL");
        NIC_CATEGORY_MAP.put("MEDICAL", "MEDICAL");
        NIC_CATEGORY_MAP.put("CLINIC", "MEDICAL");
        NIC_CATEGORY_MAP.put("HOSPITAL", "MEDICAL");
        NIC_CATEGORY_MAP.put("DIAGNOSTIC", "MEDICAL");
        NIC_CATEGORY_MAP.put("PATHOLOGY", "MEDICAL");
        NIC_CATEGORY_MAP.put("PHARMA", "PHARMACY");
        NIC_CATEGORY_MAP.put("PHARMACY", "PHARMACY");
        NIC_CATEGORY_MAP.put("DRUG", "PHARMACY");
        NIC_CATEGORY_MAP.put("MEDICINE", "PHARMACY");

        // IT & Software
        NIC_CATEGORY_MAP.put("IT", "IT, SOFTWARE & DIGITAL SERVICES");
        NIC_CATEGORY_MAP.put("SOFTWARE", "IT, SOFTWARE & DIGITAL SERVICES");
        NIC_CATEGORY_MAP.put("TECHNOLOGY", "IT, SOFTWARE & DIGITAL SERVICES");
        NIC_CATEGORY_MAP.put("DIGITAL", "IT, SOFTWARE & DIGITAL SERVICES");
        NIC_CATEGORY_MAP.put("COMPUTER", "IT, SOFTWARE & DIGITAL SERVICES");
        NIC_CATEGORY_MAP.put("CONSULTANCY", "IT, SOFTWARE & DIGITAL SERVICES");
        NIC_CATEGORY_MAP.put("SERVICES", "GENERAL STORE");

        // Electronics
        NIC_CATEGORY_MAP.put("ELECTRONICS", "ELECTRONICS");
        NIC_CATEGORY_MAP.put("ELECTRICAL", "ELECTRONICS");
        NIC_CATEGORY_MAP.put("TELECOM", "ELECTRONICS");
        NIC_CATEGORY_MAP.put("MOBILE", "ELECTRONICS");
        NIC_CATEGORY_MAP.put("APPLIANCE", "ELECTRONICS");

        // Clothing & Fashion
        NIC_CATEGORY_MAP.put("TEXTILE", "CLOTHING");
        NIC_CATEGORY_MAP.put("GARMENT", "CLOTHING");
        NIC_CATEGORY_MAP.put("CLOTHING", "CLOTHING");
        NIC_CATEGORY_MAP.put("APPAREL", "CLOTHING");
        NIC_CATEGORY_MAP.put("WEAVING", "CLOTHING");
        NIC_CATEGORY_MAP.put("FASHION", "FASHION");
        NIC_CATEGORY_MAP.put("JEWELLERY", "FASHION");
        NIC_CATEGORY_MAP.put("COSMETIC", "FASHION");
        NIC_CATEGORY_MAP.put("ACCESSORIES", "FASHION");

        // Hardware & Construction
        NIC_CATEGORY_MAP.put("CONSTRUCTION", "HARDWARE");
        NIC_CATEGORY_MAP.put("HARDWARE", "HARDWARE");
        NIC_CATEGORY_MAP.put("BUILDING", "HARDWARE");
        NIC_CATEGORY_MAP.put("CEMENT", "HARDWARE");
        NIC_CATEGORY_MAP.put("STEEL", "HARDWARE");
        NIC_CATEGORY_MAP.put("IRON", "HARDWARE");

        // Beauty
        NIC_CATEGORY_MAP.put("BEAUTY", "BEAUTY");
        NIC_CATEGORY_MAP.put("SALON", "BEAUTY");
        NIC_CATEGORY_MAP.put("PARLOUR", "BEAUTY");
        NIC_CATEGORY_MAP.put("SPA", "BEAUTY");
        NIC_CATEGORY_MAP.put("PERSONAL CARE", "BEAUTY");

        // Import Export
        NIC_CATEGORY_MAP.put("IMPORT", "IMPORT_EXPORT");
        NIC_CATEGORY_MAP.put("EXPORT", "IMPORT_EXPORT");
        NIC_CATEGORY_MAP.put("LOGISTICS", "IMPORT_EXPORT");
        NIC_CATEGORY_MAP.put("TRANSPORT", "IMPORT_EXPORT");
        NIC_CATEGORY_MAP.put("COURIER", "IMPORT_EXPORT");
        NIC_CATEGORY_MAP.put("WAREHOUSE", "IMPORT_EXPORT");
        NIC_CATEGORY_MAP.put("AGRICULTURE", "IMPORT_EXPORT");
        NIC_CATEGORY_MAP.put("FARMING", "IMPORT_EXPORT");
    }

    private MsmeDataParser() {
        // utility class
    }

    /**
     * Maps a Major Activity or NIC code description to the closest DukaanLocker category.
     * Returns "GENERAL STORE" if no match is found.
     *
     * @param activity the Major Activity or NIC description from MSME certificate
     * @return mapped DukaanLocker category (e.g., "GROCERY", "RESTAURANT", etc.)
     */
    public static String mapToDukaanLockerCategory(String activity) {
        if (activity == null || activity.isBlank()) {
            return "GENERAL STORE";
        }
        String activityUpper = activity.trim().toUpperCase();

        // Direct match first
        String directMatch = NIC_CATEGORY_MAP.get(activityUpper);
        if (directMatch != null) {
            return directMatch;
        }

        // Partial match - check if any keyword from the map appears in the activity
        for (Map.Entry<String, String> entry : NIC_CATEGORY_MAP.entrySet()) {
            if (activityUpper.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Default fallback
        return "GENERAL STORE";
    }

    /**
     * Parses the Udyam certificate HTML and extracts all relevant enterprise data.
     * Uses a multi-strategy approach:
     *   1. Table row parsing (original method)
     *   2. Leaf element parsing (div/span/p with "Label: Value" text)
     *   3. ASP.NET span/input ID-based extraction (e.g. &lt;span id="lblXxx"&gt;)
     *   4. Comprehensive full-text regex extraction (works regardless of HTML structure)
     *   5. Regex fallback for specific fields (mobile, email, pincode)
     *
     * @param html the raw HTML from PrintUdyamApplication.aspx
     * @return parsed enterprise data
     * @throws IllegalArgumentException if html is null or empty
     */
    public static MsmeParsedData parse(String html) {
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("HTML content cannot be null or empty");
        }

        MsmeParsedData data = new MsmeParsedData();
        Document doc = Jsoup.parse(html);
        String fullText = doc.text();

        // Strategy 1: Table row parsing (original method)
        parseTableFields(doc, data);

        // Strategy 2: ASP.NET span/input ID-based extraction
        // The government portal often uses <span id="...lblEnterpriseName...">VALUE</span>
        // or <input id="...txtXxx..." value="VALUE" />
        extractFromAspIds(doc, data);

        // Strategy 3: Comprehensive full-text regex extraction
        // Works regardless of HTML structure — finds "Label Value" patterns in text
        extractFromFullText(fullText, data);

        // Strategy 4: Regex fallbacks for specific fields
        if (data.getUdyamNumber() == null || data.getUdyamNumber().isBlank()) {
            data.setUdyamNumber(extractByRegex(fullText, UDYAM_PATTERN));
        }
        // NOTE: Mobile number is NOT extracted via regex fallback because the portal
        // masks it (e.g. "83*****456") and the regex would pick up the portal's
        // helpline number from the footer instead. The mobile comes from the user's
        // registration request, not from the HTML.
        if (data.getEmailId() == null || data.getEmailId().isBlank()) {
            data.setEmailId(extractByRegex(fullText, EMAIL_PATTERN));
        }
        if (data.getPincode() == null || data.getPincode().isBlank()) {
            data.setPincode(extractByRegex(fullText, PINCODE_PATTERN));
        }

        // Build the full address from components
        if (data.getAddress() == null || data.getAddress().isBlank()) {
            data.setAddress(buildAddress(data));
        }

        // For sole proprietorships, the enterprise name IS the entrepreneur name.
        // If entrepreneurName is still null but enterpriseName is set, use it.
        if ((data.getEntrepreneurName() == null || data.getEntrepreneurName().isBlank())
                && data.getEnterpriseName() != null && !data.getEnterpriseName().isBlank()) {
            data.setEntrepreneurName(data.getEnterpriseName());
        }

        // Clean up enterpriseType — the table parsing may grab the full classification
        // table text. Extract just "Micro", "Small", or "Medium".
        if (data.getEnterpriseType() != null && !data.getEnterpriseType().isBlank()) {
            String typeLower = data.getEnterpriseType().toLowerCase();
            if (typeLower.contains("micro")) {
                data.setEnterpriseType("Micro");
            } else if (typeLower.contains("small")) {
                data.setEnterpriseType("Small");
            } else if (typeLower.contains("medium")) {
                data.setEnterpriseType("Medium");
            }
            // If none matched, leave as-is (might be a custom value)
        }

        log.info("=== MSME PARSED RESULT ===");
        log.info("  udyamNumber: {}", data.getUdyamNumber());
        log.info("  enterpriseName: {}", data.getEnterpriseName());
        log.info("  entrepreneurName: {}", data.getEntrepreneurName());
        log.info("  mobileNumber: {}", data.getMobileNumber());
        log.info("  emailId: {}", data.getEmailId());
        log.info("  address: {}", data.getAddress());
        log.info("  city: {}", data.getCity());
        log.info("  district: {}", data.getDistrict());
        log.info("  state: {}", data.getState());
        log.info("  pincode: {}", data.getPincode());
        log.info("  majorActivity: {}", data.getMajorActivity());
        log.info("  enterpriseType: {}", data.getEnterpriseType());
        log.info("  typeOfOrganization: {}", data.getTypeOfOrganization());
        log.info("=== END PARSED RESULT ===");

        log.info("Parsed MSME data: {}", data);
        return data;
    }

    /**
     * Parses key-value pairs from the HTML table structure.
     * The Udyam certificate typically has tables where each row contains
     * a label cell followed by a value cell.
     */
    private static void parseTableFields(Document doc, MsmeParsedData data) {
        // 1) Table rows (primary, most reliable)
        Elements rows = doc.select("tr");
        log.info("=== MSME PARSER: Found {} table rows ===", rows.size());
        int rowIdx = 0;
        for (Element row : rows) {
            Elements cells = row.select("td, th");
            String label;
            String value;
            if (cells.size() >= 2) {
                label = cells.get(0).text().trim();
                value = cells.get(1).text().trim();
                if (value.isEmpty()) {
                    String[] kv = splitLabelValue(label);
                    if (kv != null) { label = kv[0]; value = kv[1]; }
                }
            } else if (cells.size() == 1) {
                String[] kv = splitLabelValue(cells.get(0).text().trim());
                if (kv == null) continue;
                label = kv[0];
                value = kv[1];
            } else {
                continue;
            }
            if (value.isEmpty()) continue;
            log.info("ROW {}: label=\"{}\" value=\"{}\"", rowIdx++, label, value);
            matchField(label.toLowerCase(), value, data);
        }

        // 2) Leaf block elements as a supplement (only fills still-empty fields)
        Elements leaves = doc.select("div, span, p, li");
        log.info("=== MSME PARSER: Found {} leaf elements ===", leaves.size());
        int leafIdx = 0;
        for (Element el : leaves) {
            if (!el.children().isEmpty()) continue; // ignore elements with nested markup
            String[] kv = splitLabelValue(el.text().trim());
            if (kv == null) continue;
            log.info("LEAF {}: label=\"{}\" value=\"{}\"", leafIdx++, kv[0], kv[1]);
            matchField(kv[0].toLowerCase(), kv[1], data);
        }
    }

    /**
     * Strategy 2: Extract data from ASP.NET-style &lt;span&gt; and &lt;input&gt; elements
     * whose IDs contain known field keywords. The government portal often renders
     * certificate values in elements like:
     * <pre>
     *   &lt;span id="ctl00_ContentPlaceHolder1_lblEnterpriseName"&gt;My Enterprise&lt;/span&gt;
     *   &lt;input id="ctl00_ContentPlaceHolder1_txtMobile" value="9876543210" /&gt;
     * </pre>
     * This method scans all span and input elements, matches their ID against
     * known keywords, and populates the corresponding field.
     */
    private static void extractFromAspIds(Document doc, MsmeParsedData data) {
        int found = 0;

        // --- Span elements: extract text content ---
        // The Udyam portal uses spans with IDs like:
        //   ctl00_ContentPlaceHolder1_lblEnterpriseName
        //   ctl00_ContentPlaceHolder1_lblOrganisationType
        //   ctl00_ContentPlaceHolder1_lblServices
        //   ctl00_ContentPlaceHolder1_lblCity, lblState, lblDistrict, lblPin, etc.
        for (Element span : doc.select("span[id]")) {
            String id = span.id();
            String idLower = id.toLowerCase();
            String value = span.text().trim();
            if (value.isEmpty()) continue;

            // Skip dashboard statistics spans (lblTotal, lblMicro, etc.)
            if (idLower.contains("lbltotal") || idLower.contains("lblmicro")
                    || idLower.contains("lblsmall") || idLower.contains("lblmedium")
                    || idLower.contains("lblemp") || idLower.contains("lblhits")
                    || idLower.contains("lbldate") || idLower.contains("uutor")
                    || idLower.contains("uutoc") || idLower.contains("uutom")
                    || idLower.contains("uutos") || idLower.contains("uutome")) {
                continue;
            }

            // Match specific Udyam portal span IDs by suffix
            String suffix = idLower.replace("ctl00_contentplaceholder1_", "");

            if (suffix.equals("lblenterprisename") && !isNotBlank(data.getEnterpriseName())) {
                data.setEnterpriseName(value);
                log.info("ASP-ID: enterpriseName='{}' (from {})", value, id);
                found++;
            } else if (suffix.equals("lblorganisationtype") && !isNotBlank(data.getTypeOfOrganization())) {
                data.setTypeOfOrganization(value);
                log.info("ASP-ID: typeOfOrganization='{}' (from {})", value, id);
                found++;
            } else if (suffix.equals("lblservices") && !isNotBlank(data.getMajorActivity())) {
                data.setMajorActivity(value);
                log.info("ASP-ID: majorActivity='{}' (from {})", value, id);
                found++;
            } else if (suffix.equals("lblcity") && !isNotBlank(data.getCity())) {
                data.setCity(value);
                log.info("ASP-ID: city='{}' (from {})", value, id);
                found++;
            } else if (suffix.equals("lblstate") && !isNotBlank(data.getState())) {
                data.setState(value);
                log.info("ASP-ID: state='{}' (from {})", value, id);
                found++;
            } else if (suffix.equals("lbldistrict") && !isNotBlank(data.getDistrict())) {
                data.setDistrict(value);
                log.info("ASP-ID: district='{}' (from {})", value, id);
                found++;
            } else if (suffix.equals("lblpin") && !isNotBlank(data.getPincode())) {
                Matcher m = PINCODE_PATTERN.matcher(value);
                if (m.find()) {
                    data.setPincode(m.group());
                    log.info("ASP-ID: pincode='{}' (from {})", m.group(), id);
                    found++;
                }
            } else if (suffix.equals("lblmobile") && !isNotBlank(data.getMobileNumber())) {
                Matcher m = MOBILE_PATTERN.matcher(value);
                if (m.find()) {
                    data.setMobileNumber(m.group());
                    log.info("ASP-ID: mobileNumber='{}' (from {})", m.group(), id);
                    found++;
                }
            } else if (suffix.equals("lblemail") && !isNotBlank(data.getEmailId())) {
                Matcher m = EMAIL_PATTERN.matcher(value);
                if (m.find()) {
                    data.setEmailId(m.group().toLowerCase());
                    log.info("ASP-ID: emailId='{}' (from {})", m.group().toLowerCase(), id);
                    found++;
                }
            } else if (suffix.equals("lblflats") && !isNotBlank(data.getAddress())) {
                data.setAddress(value);
                log.info("ASP-ID: address='{}' (from {})", value, id);
                found++;
            } else if (suffix.equals("lblvillage") && !isNotBlank(data.getCity())) {
                data.setCity(value);
                log.info("ASP-ID: city(village)='{}' (from {})", value, id);
                found++;
            } else if (suffix.equals("lblblock") || suffix.equals("lblroad")) {
                // Append block/road to address
                String existing = data.getAddress() != null ? data.getAddress() : "";
                if (existing.toLowerCase().indexOf(value.toLowerCase()) < 0) {
                    data.setAddress((existing + " " + value).trim());
                    log.info("ASP-ID: address(appended)='{}' (from {})", value, id);
                }
            } else if (suffix.equals("lblacknowledgement") && !isNotBlank(data.getDateOfRegistration())) {
                data.setDateOfRegistration(value);
                log.info("ASP-ID: dateOfRegistration='{}' (from {})", value, id);
                found++;
            } else if (suffix.equals("lblgender") || suffix.equals("lblsocialcat")
                    || suffix.equals("lbldateofincorporation") || suffix.equals("lbldateofcommencement")) {
                // Known non-critical fields — skip silently
            } else {
                // Fallback: generic keyword matching for unknown span IDs
                if (!isFieldSetByKeyword(idLower, data)) {
                    setFieldByKeyword(idLower, value, data);
                }
            }
        }

        log.info("=== ASP-ID extraction: populated {} fields ===", found);
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Checks whether a field identified by an ASP.NET element ID is already set.
     */
    private static boolean isFieldSetByKeyword(String id, MsmeParsedData data) {
        if (id.contains("udyam") && (data.getUdyamNumber() != null && !data.getUdyamNumber().isBlank())) return true;
        if (id.contains("enterprise") && !id.contains("type") && id.contains("name")
                && (data.getEnterpriseName() != null && !data.getEnterpriseName().isBlank())) return true;
        if ((id.contains("entrepreneur") || id.contains("owner") || id.contains("proprietor")) && id.contains("name")
                && (data.getEntrepreneurName() != null && !data.getEntrepreneurName().isBlank())) return true;
        if (id.contains("mobile") && (data.getMobileNumber() != null && !data.getMobileNumber().isBlank())) return true;
        if (id.contains("email") && (data.getEmailId() != null && !data.getEmailId().isBlank())) return true;
        if (id.contains("state") && !id.contains("district") && (data.getState() != null && !data.getState().isBlank())) return true;
        if (id.contains("district") && (data.getDistrict() != null && !data.getDistrict().isBlank())) return true;
        if ((id.contains("city") || id.contains("town")) && (data.getCity() != null && !data.getCity().isBlank())) return true;
        if (id.contains("pin") && (data.getPincode() != null && !data.getPincode().isBlank())) return true;
        if (id.contains("activity") && !id.contains("nic") && (data.getMajorActivity() != null && !data.getMajorActivity().isBlank())) return true;
        if ((id.contains("enterprise") && id.contains("type")) && (data.getEnterpriseType() != null && !data.getEnterpriseType().isBlank())) return true;
        if (id.contains("organization") && (data.getTypeOfOrganization() != null && !data.getTypeOfOrganization().isBlank())) return true;
        if (id.contains("address") || id.contains("flat") || id.contains("door") || id.contains("block")
                || id.contains("road") || id.contains("street") || id.contains("premises")) {
            return data.getAddress() != null && !data.getAddress().isBlank();
        }
        return false;
    }

    /**
     * Sets a field in MsmeParsedData based on keywords found in an ASP.NET element ID.
     * Returns true if the value was set.
     */
    private static boolean setFieldByKeyword(String id, String value, MsmeParsedData data) {
        // Skip garbage values (HTML artifacts, single chars, etc.)
        if (value.length() < 2 || value.equals("-")) return false;

        // Udyam number
        if (id.contains("udyam") && id.contains("number")) {
            Matcher m = UDYAM_PATTERN.matcher(value);
            if (m.find()) { data.setUdyamNumber(m.group().toUpperCase()); return true; }
            if (value.toUpperCase().startsWith("UDYAM")) { data.setUdyamNumber(value.toUpperCase().trim()); return true; }
        }
        // Enterprise name
        if (id.contains("enterprise") && id.contains("name") && !id.contains("type")) {
            data.setEnterpriseName(value); return true;
        }
        // Entrepreneur / owner / proprietor name
        if ((id.contains("entrepreneur") || id.contains("owner") || id.contains("proprietor")) && id.contains("name")) {
            data.setEntrepreneurName(value); return true;
        }
        // Mobile
        if (id.contains("mobile") || id.contains("phone")) {
            Matcher m = MOBILE_PATTERN.matcher(value);
            if (m.find()) { data.setMobileNumber(m.group()); return true; }
        }
        // Email
        if (id.contains("email")) {
            Matcher m = EMAIL_PATTERN.matcher(value);
            if (m.find()) { data.setEmailId(m.group().toLowerCase()); return true; }
        }
        // State
        if (id.contains("state") && !id.contains("district") && !id.contains("estate")) {
            data.setState(value); return true;
        }
        // District
        if (id.contains("district")) {
            data.setDistrict(value); return true;
        }
        // City / Town
        if (id.contains("city") || id.contains("town")) {
            data.setCity(value); return true;
        }
        // Pincode
        if (id.contains("pin") && !id.contains("spine")) {
            Matcher m = PINCODE_PATTERN.matcher(value);
            if (m.find()) { data.setPincode(m.group()); return true; }
        }
        // Major Activity
        if (id.contains("activity") || id.contains("major") || id.contains("nic")) {
            data.setMajorActivity(value); return true;
        }
        // Enterprise type (Micro/Small/Medium)
        if (id.contains("enterprise") && id.contains("type")) {
            data.setEnterpriseType(value); return true;
        }
        // Type of Organization
        if (id.contains("organization") || id.contains("organisation")) {
            data.setTypeOfOrganization(value); return true;
        }
        // Address components
        if (id.contains("flat") || id.contains("door") || id.contains("block")
                || id.contains("road") || id.contains("street") || id.contains("premises")
                || id.contains("building") || id.contains("address")) {
            String existing = data.getAddress() != null ? data.getAddress() : "";
            if (existing.toLowerCase().indexOf(value.toLowerCase()) < 0) {
                data.setAddress((existing + " " + value).trim());
            }
            return true;
        }
        return false;
    }

    /**
     * Strategy 3: Comprehensive full-text regex extraction.
     * Scans the entire page text for patterns like:
     *   "Name of Enterprise  My Enterprise Name"
     *   "Name of Entrepreneur  John Doe"
     *   "State  Maharashtra"
     * This works regardless of HTML structure.
     */
    private static void extractFromFullText(String fullText, MsmeParsedData data) {
        int found = 0;

        // Enterprise name: "Name of Enterprise" or "Enterprise Name" followed by text
        if (data.getEnterpriseName() == null || data.getEnterpriseName().isBlank()) {
            String name = extractLabelFromText(fullText,
                    "Name of Enterprise", "Enterprise Name", "Name of Business", "Business Name");
            if (name != null && name.length() >= 2) {
                data.setEnterpriseName(name);
                log.info("FULLTEXT: enterpriseName='{}'", name);
                found++;
            }
        }

        // Entrepreneur name: "Name of Entrepreneur" or "Entrepreneur Name"
        if (data.getEntrepreneurName() == null || data.getEntrepreneurName().isBlank()) {
            String name = extractLabelFromText(fullText,
                    "Name of Entrepreneur", "Entrepreneur Name", "Name of Proprietor",
                    "Proprietor Name", "Owner Name", "Authorized Signatory");
            if (name != null && name.length() >= 2) {
                data.setEntrepreneurName(name);
                log.info("FULLTEXT: entrepreneurName='{}'", name);
                found++;
            }
        }

        // State
        if (data.getState() == null || data.getState().isBlank()) {
            String state = extractLabelFromText(fullText, "State");
            if (state != null && state.length() >= 2 && state.length() <= 40) {
                data.setState(state);
                log.info("FULLTEXT: state='{}'", state);
                found++;
            }
        }

        // District
        if (data.getDistrict() == null || data.getDistrict().isBlank()) {
            String district = extractLabelFromText(fullText, "District");
            if (district != null && district.length() >= 2 && district.length() <= 40) {
                data.setDistrict(district);
                log.info("FULLTEXT: district='{}'", district);
                found++;
            }
        }

        // City/Town
        if (data.getCity() == null || data.getCity().isBlank()) {
            String city = extractLabelFromText(fullText,
                    "City", "City/Town", "City/Town/District", "Village/Town");
            if (city != null && city.length() >= 2 && city.length() <= 40) {
                data.setCity(city);
                log.info("FULLTEXT: city='{}'", city);
                found++;
            }
        }

        // Major Activity
        if (data.getMajorActivity() == null || data.getMajorActivity().isBlank()) {
            String activity = extractLabelFromText(fullText,
                    "Major Activity", "Business Activity", "NIC Code", "Activity Type",
                    "NIC 2 Digit", "NIC 4 Digit", "NIC 5 Digit");
            if (activity != null && activity.length() >= 2) {
                data.setMajorActivity(activity);
                log.info("FULLTEXT: majorActivity='{}'", activity);
                found++;
            }
        }

        // Enterprise type (Micro/Small/Medium)
        if (data.getEnterpriseType() == null || data.getEnterpriseType().isBlank()) {
            // First try to find it as a labeled field
            String type = extractLabelFromText(fullText,
                    "Type of Enterprise", "Enterprise Type", "MSME Type", "Classification");
            if (type != null && type.length() >= 2) {
                data.setEnterpriseType(type);
                log.info("FULLTEXT: enterpriseType='{}'", type);
                found++;
            } else {
                // Fallback: look for the keywords directly in text
                String lowerText = fullText.toLowerCase();
                if (lowerText.contains("micro")) {
                    data.setEnterpriseType("Micro"); found++;
                } else if (lowerText.contains("small")) {
                    data.setEnterpriseType("Small"); found++;
                } else if (lowerText.contains("medium")) {
                    data.setEnterpriseType("Medium"); found++;
                }
            }
        }

        // Type of Organization
        if (data.getTypeOfOrganization() == null || data.getTypeOfOrganization().isBlank()) {
            String orgType = extractLabelFromText(fullText,
                    "Type of Organization", "Organization Type", "Type of Organisation");
            if (orgType != null && orgType.length() >= 2) {
                data.setTypeOfOrganization(orgType);
                log.info("FULLTEXT: typeOfOrganization='{}'", orgType);
                found++;
            }
        }

        // Address
        if (data.getAddress() == null || data.getAddress().isBlank()) {
            String addr = extractLabelFromText(fullText,
                    "Flat/Door/Block No", "Flat/Door/Block", "Official Address",
                    "Address", "Flat No", "Door No", "Block No");
            if (addr != null && addr.length() >= 2) {
                data.setAddress(addr);
                log.info("FULLTEXT: address='{}'", addr);
                found++;
            }
        }

        // Udyam number (from full text, more aggressive)
        if (data.getUdyamNumber() == null || data.getUdyamNumber().isBlank()) {
            String udyam = extractLabelFromText(fullText,
                    "Udyam Registration Number", "Udyam No", "Registration Number");
            if (udyam != null) {
                Matcher m = UDYAM_PATTERN.matcher(udyam);
                if (m.find()) {
                    data.setUdyamNumber(m.group().toUpperCase());
                    log.info("FULLTEXT: udyamNumber='{}'", data.getUdyamNumber());
                    found++;
                } else if (udyam.toUpperCase().startsWith("UDYAM")) {
                    data.setUdyamNumber(udyam.toUpperCase().trim());
                    log.info("FULLTEXT: udyamNumber='{}'", data.getUdyamNumber());
                    found++;
                }
            }
        }

        log.info("=== FULLTEXT extraction: populated {} fields ===", found);
    }

    /**
     * Extracts a value following one or more known labels in full page text.
     * Searches for patterns like "Label  value" or "Label: value" or "Label value"
     * where the value is the text after the label until the next newline, label, or
     * a reasonable boundary.
     *
     * @param text the full page text
     * @param labels one or more label patterns to search for (case-insensitive)
     * @return the extracted value, or null if not found
     */
    private static String extractLabelFromText(String text, String... labels) {
        String lowerText = text.toLowerCase();
        for (String label : labels) {
            String labelLower = label.toLowerCase();
            int idx = lowerText.indexOf(labelLower);
            if (idx < 0) continue;

            // Get text after the label
            String afterLabel = text.substring(idx + label.length()).trim();
            if (afterLabel.isEmpty()) continue;

            // Skip common separators at the start
            if (afterLabel.startsWith(":")) afterLabel = afterLabel.substring(1).trim();
            if (afterLabel.startsWith("-")) afterLabel = afterLabel.substring(1).trim();
            if (afterLabel.startsWith("—")) afterLabel = afterLabel.substring(1).trim();

            if (afterLabel.isEmpty()) continue;

            // Extract until a reasonable boundary:
            //   - newline
            //   - next known label
            //   - end of line (2+ consecutive spaces or tab)
            String value = afterLabel.split("[\\n\\r]")[0].trim();

            // If the line is very long, try to cut at the next label boundary
            if (value.length() > 100) {
                String[] cutLabels = {
                        "Name of", "Type of", "State", "District", "City", "Pin",
                        "Mobile", "Email", "Date of", "Investment", "Turnover",
                        "NIC", "Activity", "Organization", "Enterprise", "Address",
                        "Flat", "Door", "Block", "Road", "Street", "PAN"
                };
                for (String cut : cutLabels) {
                    int cutIdx = value.toLowerCase().indexOf(cut.toLowerCase());
                    if (cutIdx > 2 && cutIdx < value.length() - 1) {
                        value = value.substring(0, cutIdx).trim();
                        break;
                    }
                }
            }

            // Remove trailing label fragments that might have been captured
            if (value.toLowerCase().contains("type of")) {
                value = value.substring(0, value.toLowerCase().indexOf("type of")).trim();
            }
            if (value.toLowerCase().contains("udyam registration")) {
                value = value.substring(0, value.toLowerCase().indexOf("udyam registration")).trim();
            }

            // Skip garbage values
            if (value.isEmpty() || value.length() < 2 || value.equals("-")) continue;
            // Skip values that are just numbers (likely not enterprise names)
            if (label.toLowerCase().contains("name") && value.matches("\\d+")) continue;

            return value;
        }
        return null;
    }

    /**
     * Matches a label-value pair to the corresponding field in MsmeParsedData.
     */
    private static void matchField(String label, String value, MsmeParsedData data) {
        if (value == null || value.isBlank()) return;

        // Address building blocks are accumulated (deduped) regardless of order.
        if (label.contains("flat") || label.contains("door") || label.contains("block no")
                || label.contains("road") || label.contains("street") || label.contains("lane")
                || label.contains("premises") || label.contains("building")) {
            String existing = data.getAddress() != null ? data.getAddress() : "";
            if (existing.toLowerCase().indexOf(value.toLowerCase()) < 0) {
                data.setAddress((existing + " " + value).trim());
            }
            return;
        }

        // Don't overwrite a field that already has a value (first good match wins).
        if (isFieldSet(data, label)) return;

        // Udyam Registration Number
        if (label.contains("udyam registration number") || label.contains("udyam no")
                || label.contains("registration number")) {
            Matcher m = UDYAM_PATTERN.matcher(value);
            if (m.find()) {
                data.setUdyamNumber(m.group().toUpperCase());
            } else if (value.toUpperCase().startsWith("UDYAM")) {
                data.setUdyamNumber(value.toUpperCase().trim());
            }
        }
        // Enterprise Name
        else if (label.contains("name of enterprise") || label.contains("enterprise name")
                || label.contains("name of business")) {
            data.setEnterpriseName(value);
        }
        // Entrepreneur Name
        else if (label.contains("name of entrepreneur") || label.contains("entrepreneur name")
                || label.contains("owner name") || label.contains("proprietor name")
                || label.contains("authorized signatory")) {
            data.setEntrepreneurName(value);
        }
        // Mobile Number
        else if (label.contains("mobile") || label.contains("phone")) {
            Matcher m = MOBILE_PATTERN.matcher(value);
            if (m.find()) {
                data.setMobileNumber(m.group());
            }
        }
        // Email
        else if (label.contains("email")) {
            Matcher m = EMAIL_PATTERN.matcher(value);
            if (m.find()) {
                data.setEmailId(m.group().toLowerCase());
            }
        }
        // State
        else if (label.equals("state") || label.contains("state of")) {
            data.setState(value);
        }
        // District
        else if (label.equals("district") || label.contains("district")) {
            data.setDistrict(value);
        }
        // City
        else if (label.equals("city") || label.equals("city/town")
                || label.contains("village/town") || label.contains("city/town/block")) {
            data.setCity(value);
        }
        // Pincode
        else if (label.contains("pin") || label.contains("pincode") || label.contains("postal")) {
            Matcher m = PINCODE_PATTERN.matcher(value);
            if (m.find()) {
                data.setPincode(m.group());
            }
        }
        // Major Activity / NIC Code
        else if (label.contains("major activity") || label.contains("business activity")
                || label.contains("nic code") || label.contains("activity type")
                || label.contains("nic 2 digit") || label.contains("nic 4 digit")
                || label.contains("nic 5 digit") || label.contains("activity")) {
            data.setMajorActivity(value);
        }
        // Enterprise Type (Micro/Small/Medium)
        else if (label.contains("type of enterprise") || label.contains("enterprise type")
                || label.contains("msme type") || label.contains("classification")) {
            data.setEnterpriseType(value);
        }
        // Type of Organization
        else if (label.contains("type of organization") || label.contains("organization type")) {
            data.setTypeOfOrganization(value);
        }
    }

    /**
     * Extracts the first match of a regex pattern from the input text.
     */
    private static String extractByRegex(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    /**
     * Builds a full address string from individual parsed components.
     */
    private static String buildAddress(MsmeParsedData data) {
        StringBuilder sb = new StringBuilder();
        if (data.getAddress() != null && !data.getAddress().isBlank()) {
            sb.append(data.getAddress());
        }
        if (data.getCity() != null && !data.getCity().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(data.getCity());
        }
        if (data.getDistrict() != null && !data.getDistrict().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(data.getDistrict());
        }
        if (data.getState() != null && !data.getState().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(data.getState());
        }
        if (data.getPincode() != null && !data.getPincode().isBlank()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(data.getPincode());
        }
        return sb.length() > 0 ? sb.toString().trim() : null;
    }

    /**
     * Splits a "Label : Value" (or "Label - Value") string into its two parts.
     * Returns {@code null} when there is no usable separator / value.
     */
    public static String[] splitLabelValue(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.length() < 3) return null;
        int idx = t.indexOf(':');
        if (idx <= 0) idx = t.indexOf('\u2013'); // en dash
        if (idx <= 0) idx = t.indexOf('\u2014'); // em dash
        if (idx <= 0) {
            Matcher m = LEAF_SEP.matcher(t); // " - " with surrounding spaces
            if (m.find()) idx = m.start() + 1;
        }
        if (idx <= 0 || idx >= t.length() - 1) return null;
        String label = t.substring(0, idx).trim();
        String value = t.substring(idx + 1).trim();
        if (label.isEmpty() || value.isEmpty()) return null;
        return new String[]{label, value};
    }

    private static final Pattern LEAF_SEP = Pattern.compile("\\s-\\s");

    private static boolean isFieldSet(MsmeParsedData data, String label) {
        if (label.contains("udyam registration number") || label.contains("udyam no")
                || label.contains("registration number"))
            return data.getUdyamNumber() != null && !data.getUdyamNumber().isBlank();
        if (label.contains("name of enterprise") || label.contains("enterprise name")
                || label.contains("name of business"))
            return data.getEnterpriseName() != null && !data.getEnterpriseName().isBlank();
        if (label.contains("name of entrepreneur") || label.contains("entrepreneur name")
                || label.contains("owner name") || label.contains("proprietor name")
                || label.contains("authorized signatory"))
            return data.getEntrepreneurName() != null && !data.getEntrepreneurName().isBlank();
        if (label.contains("mobile") || label.contains("phone"))
            return data.getMobileNumber() != null && !data.getMobileNumber().isBlank();
        if (label.contains("email"))
            return data.getEmailId() != null && !data.getEmailId().isBlank();
        if (label.contains("state"))
            return data.getState() != null && !data.getState().isBlank();
        if (label.contains("district"))
            return data.getDistrict() != null && !data.getDistrict().isBlank();
        if (label.contains("city") || label.contains("village/town") || label.contains("city/town/block"))
            return data.getCity() != null && !data.getCity().isBlank();
        if (label.contains("pin") || label.contains("pincode") || label.contains("postal"))
            return data.getPincode() != null && !data.getPincode().isBlank();
        if (label.contains("major activity") || label.contains("business activity")
                || label.contains("nic") || label.contains("activity"))
            return data.getMajorActivity() != null && !data.getMajorActivity().isBlank();
        if (label.contains("type of enterprise") || label.contains("enterprise type")
                || label.contains("msme type") || label.contains("classification"))
            return data.getEnterpriseType() != null && !data.getEnterpriseType().isBlank();
        if (label.contains("type of organization") || label.contains("organization type"))
            return data.getTypeOfOrganization() != null && !data.getTypeOfOrganization().isBlank();
        return false;
    }
}
