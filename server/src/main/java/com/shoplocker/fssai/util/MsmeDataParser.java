package com.shoplocker.fssai.util;

import com.shoplocker.fssai.dto.MsmeParsedData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private MsmeDataParser() {
        // utility class
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
        // Major Activity
        else if (label.contains("major activity") || label.contains("business activity")
                || label.contains("nic code") || label.contains("activity type")) {
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
