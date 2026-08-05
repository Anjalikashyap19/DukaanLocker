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

        // Extract table-based fields (primary method)
        parseTableFields(doc, data);

        // Extract Udyam number from full text if not found in table
        if (data.getUdyamNumber() == null || data.getUdyamNumber().isBlank()) {
            data.setUdyamNumber(extractByRegex(doc.text(), UDYAM_PATTERN));
        }

        // Extract mobile from full text if not found in table
        if (data.getMobileNumber() == null || data.getMobileNumber().isBlank()) {
            String fullText = doc.text();
            Matcher m = MOBILE_PATTERN.matcher(fullText);
            if (m.find()) {
                data.setMobileNumber(m.group());
            }
        }

        // Extract email from full text if not found in table
        if (data.getEmailId() == null || data.getEmailId().isBlank()) {
            data.setEmailId(extractByRegex(doc.text(), EMAIL_PATTERN));
        }

        // Extract pincode from address if not found separately
        if (data.getPincode() == null || data.getPincode().isBlank()) {
            data.setPincode(extractByRegex(doc.text(), PINCODE_PATTERN));
        }

        // Build the full address from components
        if (data.getAddress() == null || data.getAddress().isBlank()) {
            data.setAddress(buildAddress(data));
        }

        log.info("Parsed MSME data: {}", data);
        return data;
    }

    /**
     * Parses key-value pairs from the HTML table structure.
     * The Udyam certificate typically has tables where each row contains
     * a label cell followed by a value cell.
     */
    private static void parseTableFields(Document doc, MsmeParsedData data) {
        // Try table rows first (most common structure)
        Elements rows = doc.select("tr");
        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() >= 2) {
                String label = cells.get(0).text().trim().toLowerCase();
                String value = cells.get(1).text().trim();
                matchField(label, value, data);
            }
        }

        // Also try div-based layouts (some portal versions use divs)
        Elements allElements = doc.select("div, span, p");
        for (Element el : allElements) {
            String text = el.text().trim();
            if (text.contains(":")) {
                String[] parts = text.split(":", 2);
                if (parts.length == 2) {
                    String label = parts[0].trim().toLowerCase();
                    String value = parts[1].trim();
                    matchField(label, value, data);
                }
            }
        }
    }

    /**
     * Matches a label-value pair to the corresponding field in MsmeParsedData.
     */
    private static void matchField(String label, String value, MsmeParsedData data) {
        if (value == null || value.isBlank()) return;

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
            // If the value contains a NIC code pattern, extract the description
            Matcher nicMatcher = NIC_5DIGIT_PATTERN.matcher(value);
            if (nicMatcher.find()) {
                // Use the full NIC description
                data.setMajorActivity(value);
            } else {
                nicMatcher = NIC_4DIGIT_PATTERN.matcher(value);
                if (nicMatcher.find()) {
                    data.setMajorActivity(value);
                } else {
                    nicMatcher = NIC_2DIGIT_PATTERN.matcher(value);
                    if (nicMatcher.find()) {
                        data.setMajorActivity(value);
                    } else {
                        data.setMajorActivity(value);
                    }
                }
            }
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
        // Address components
        else if (label.contains("flat") || label.contains("door") || label.contains("block no")) {
            String existing = data.getAddress() != null ? data.getAddress() : "";
            data.setAddress((existing + " " + value).trim());
        }
        else if (label.contains("road") || label.contains("street") || label.contains("lane")) {
            String existing = data.getAddress() != null ? data.getAddress() : "";
            data.setAddress((existing + " " + value).trim());
        }
        else if (label.contains("premises") || label.contains("building")) {
            String existing = data.getAddress() != null ? data.getAddress() : "";
            data.setAddress((existing + " " + value).trim());
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
}
