package com.shoplocker.fssai.util;

import com.shoplocker.fssai.dto.FssaiCertificateData;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an FSSAI Form C licence PDF's text layer.
 *
 * <p>Form C layout, in the order it appears:</p>
 * <pre>
 * 1. Name &amp; Registered Office address of Licensee: M/s. ACME STORES PVT LTD
 *    &lt;address lines&gt;
 * 2. Address of Authorized Premises: &lt;address&gt;
 * 3. Kind of Business: Trade/Retail - Retailer
 * 5. Category of License: State License
 * ...
 * Page 4 (Non-Form C annexure, the only place owner details exist):
 * Person in charge of operations Name: K MALLIKARJUNA PRASAD
 *   Qualification: Degree ... Mobile No: 9959055143
 *   Email-ID: owner@example.com Address : ... State: Telangana
 *   District: Hyderabad Pin Code: 500033
 * </pre>
 *
 * <p>PDFBox may return the text line-by-line or collapsed onto one line
 * depending on the generator, so every field is matched against a whitespace-
 * normalized copy of the text with label-to-label slicing. Nothing here throws:
 * absent fields stay {@code null} and the caller decides to skip the file.</p>
 */
public final class FssaiCertificateParser {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final Pattern LICENSE_NUMBER = Pattern.compile(
            "License\\s*Number\\s*:?\\s*(\\d{10,16})", FLAGS);

    private static final Pattern DATE = Pattern.compile(
            "(\\d{1,2}[-/][0-9A-Za-z]{1,9}[-/]\\d{4})");

    private static final Pattern ISSUED_ON = Pattern.compile(
            "Issued\\s*On\\s*:?\\s*" + DATE.pattern(), FLAGS);

    private static final Pattern VALID_UPTO = Pattern.compile(
            "Valid\\s*Upto\\s*:?\\s*" + DATE.pattern(), FLAGS);

    private static final Pattern KIND_OF_BUSINESS = Pattern.compile(
            "Kind\\s*of\\s*Business\\s*:?\\s*(.+?)\\s+(?:\\d+\\.\\s|Dairy\\s*Business|Category\\s*of\\s*License)",
            FLAGS);

    private static final Pattern LICENSE_CATEGORY = Pattern.compile(
            "Category\\s*of\\s*License\\s*:?\\s*(.+?)\\s+(?:This\\s+license|\\d+\\.\\s|$)", FLAGS);

    private static final Pattern OWNER_NAME = Pattern.compile(
            "Name\\s*:\\s*(.+?)\\s*(?:Qualification|Contact\\s*No|Mobile\\s*No|Email-ID|Address\\s*:)",
            FLAGS);

    private static final Pattern MOBILE = Pattern.compile(
            "Mobile\\s*No\\s*:?\\s*([6-9]\\d{9})\\b", FLAGS);

    private static final Pattern EMAIL = Pattern.compile(
            "Email-ID\\s*:?\\s*([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})", FLAGS);

    private static final Pattern STATE_LABEL = Pattern.compile(
            "State\\s*:\\s*([A-Za-z][A-Za-z .&-]{1,40}?)\\s+District\\s*:", FLAGS);

    private static final Pattern DISTRICT_LABEL = Pattern.compile(
            "District\\s*:\\s*([A-Za-z][A-Za-z .&-]{1,40}?)\\s+Pin\\s*Code", FLAGS);

    private static final Pattern PIN_CODE_LABEL = Pattern.compile(
            "Pin\\s*Code\\s*:?\\s*(\\d{6})", FLAGS);

    private static final Pattern PIN_CODE = Pattern.compile("(?<!\\d)([1-9]\\d{5})(?!\\d)");

    private static final Pattern FILE_LICENSE = Pattern.compile("(\\d{10,16})");

    /** "PROP:", "PROP-", "PRO:" etc. separate the business name from the proprietor. */
    private static final Pattern PROPRIETOR = Pattern.compile(
            "\\b(?:PROPRIETOR|PROPRITOR|PROP|PRO)\\b", FLAGS);

    /** Where the licensee name ends and its street address begins. */
    private static final List<String> ADDRESS_MARKERS = List.of(
            "plot no", "plot nos", "shop no", "door no", "d.no", "h.no", "h. no",
            "house no", "building no", "building", "flat no", "flat", "ground floor",
            "first floor", "near", "road no", "street no", "street", "village",
            "post office", "post", "tehsil", "taluk", "district", "state:", "pincode",
            "pin code", "address of authorized premises", "municipal", "circle");

    private static final List<String> INDIAN_STATES = List.of(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh",
            "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka",
            "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram",
            "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu",
            "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal",
            "Delhi", "Jammu and Kashmir", "Ladakh", "Puducherry", "Chandigarh");

    private FssaiCertificateParser() {
    }

    /**
     * @param rawText text layer of the certificate (may be null/empty)
     * @param fileName the feed file name, used for the licence-number fallback
     * @return parsed fields; never null
     */
    public static FssaiCertificateData parse(String rawText, String fileName) {
        FssaiCertificateData data = new FssaiCertificateData();
        String text = rawText == null ? "" : rawText
                .replace('\u2010', '-')
                .replace('\u2011', '-')
                .replace('\u2013', '-')
                .replace('\u2014', '-');
        String flat = text.replaceAll("\\s+", " ").trim();

        data.setLicenseNumber(firstGroup(LICENSE_NUMBER, flat));
        if (data.getLicenseNumber() == null && fileName != null) {
            data.setLicenseNumber(firstGroup(FILE_LICENSE, fileName));
        }

        data.setShopName(extractLicenseeName(flat));
        data.setRegisteredAddress(sliceAfter(flat, "Registered Office address of Licensee",
                "Address of Authorized Premises", "Kind of Business"));
        data.setPremisesAddress(sliceAfter(flat, "Address of Authorized Premises",
                "Kind of Business", "Dairy Business"));
        data.setKindOfBusiness(firstGroup(KIND_OF_BUSINESS, flat));
        data.setLicenseCategory(firstGroup(LICENSE_CATEGORY, flat));
        data.setIssuedOn(firstGroup(ISSUED_ON, flat));
        data.setValidUpto(firstGroup(VALID_UPTO, flat));

        // Owner details only exist in the "Person in charge of operations" block.
        int personIdx = indexOf(flat, "Person in charge of operations", 0);
        String personBlock = personIdx >= 0
                ? flat.substring(personIdx, Math.min(flat.length(), personIdx + 1200))
                : flat;

        String ownerName = firstGroup(OWNER_NAME, personBlock);
        if (FssaiCertificateData.isRealValue(ownerName)) {
            data.setOwnerName(ownerName.trim());
        }
        data.setMobileNumber(firstGroup(MOBILE, personBlock));
        if (data.getMobileNumber() == null) {
            data.setMobileNumber(firstGroup(MOBILE, flat));
        }
        data.setEmailId(firstGroup(EMAIL, personBlock));

        data.setState(firstGroup(STATE_LABEL, personBlock));
        data.setDistrict(firstGroup(DISTRICT_LABEL, personBlock));
        data.setPincode(lastPincode(data.getPremisesAddress()));
        if (data.getPincode() == null) {
            data.setPincode(firstGroup(PIN_CODE_LABEL, personBlock));
        }
        return data;
    }

    /**
     * The licensee name sits between its label and the start of the street
     * address ("M/s. ACME STORES PVT LTD Plot Nos. 40 to 43 ...").
     */
    private static String extractLicenseeName(String flat) {
        int label = indexOf(flat, "Registered Office address of Licensee", 0);
        String rest;
        if (label >= 0) {
            int colon = flat.indexOf(':', label);
            if (colon < 0) {
                return null;
            }
            rest = flat.substring(colon + 1).trim();
        } else {
            int start = indexOf(flat, "Registered Office address of ", 0);
            int lic = start < 0 ? -1 : indexOf(flat, "Licensee", start);
            int colon = lic < 0 ? -1 : flat.indexOf(':', lic);
            if (colon < 0) {
                return null;
            }
            // Column-sorted text interleaves the label and the value, so join
            // both sides of "Licensee:" back together before cutting the name.
            rest = (flat.substring(start + "Registered Office address of ".length(), lic)
                    + " " + flat.substring(colon + 1)).trim();
        }
        int cut = firstMarker(rest);
        Matcher proprietor = PROPRIETOR.matcher(rest);
        if (proprietor.find() && (cut < 0 || proprietor.start() < cut)) {
            cut = proprietor.start();
        }
        String name = (cut > 0 ? rest.substring(0, cut) : rest).trim();
        name = name.replaceFirst("(?i)^M\\s*/\\s*S\\.?\\s*[-.]?\\s*", "");
        name = name.replaceFirst("^[,;.\\-\\s]+", "");
        name = name.replaceAll("[,;\\-\\s]+$", "").trim();
        if (name.length() < 3 || name.length() > 140 || !hasLetter(name)
                || name.equalsIgnoreCase("N/A")) {
            return null;
        }
        return name;
    }

    private static int firstMarker(String value) {
        int best = -1;
        for (String marker : ADDRESS_MARKERS) {
            int idx = indexOfWord(value, marker, 0);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    /** Value between a label and the earliest of the given stop labels. */
    private static String sliceAfter(String flat, String label, String... stops) {
        int idx = indexOf(flat, label, 0);
        if (idx < 0) {
            return null;
        }
        int colon = flat.indexOf(':', idx);
        int start = colon >= 0 && colon < idx + label.length() + 3 ? colon + 1 : idx + label.length();
        if (start >= flat.length()) {
            return null;
        }
        int end = flat.length();
        for (String stop : stops) {
            int stopIdx = indexOf(flat, stop, start);
            if (stopIdx >= 0 && stopIdx < end) {
                end = stopIdx;
            }
        }
        String value = flat.substring(start, end).trim()
                .replaceAll("\\s+\\d{1,2}\\.$", "")
                .replaceAll("[,;\\-\\s]+$", "");
        return value.isBlank() ? null : value;
    }

    private static String firstGroup(Pattern pattern, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        String group = matcher.group(1).trim();
        return group.isEmpty() ? null : group;
    }

    private static String lastPincode(String address) {
        if (address == null) {
            return null;
        }
        Matcher matcher = PIN_CODE.matcher(address);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        return last;
    }

    private static boolean hasLetter(String value) {
        return value.chars().anyMatch(Character::isLetter);
    }

    private static int indexOf(String haystack, String needle, int from) {
        return haystack.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT), from);
    }

    /** Whole-word search, so "near" does not match inside "LINEAR". */
    private static int indexOfWord(String haystack, String needle, int from) {
        Pattern pattern = Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(needle.trim())
                + "(?![A-Za-z0-9])", FLAGS);
        Matcher matcher = pattern.matcher(haystack);
        return matcher.find(from) ? matcher.start() : -1;
    }

    /** State name found in the premises address, e.g. "... Telangana-500081". */
    public static String detectState(String address) {
        if (address == null) {
            return null;
        }
        for (String state : INDIAN_STATES) {
            if (indexOfWord(address, state, 0) >= 0) {
                return state;
            }
        }
        return null;
    }
}
