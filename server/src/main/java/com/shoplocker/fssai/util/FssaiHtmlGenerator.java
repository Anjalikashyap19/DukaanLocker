package com.shoplocker.fssai.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Generates professional XHTML templates for FSSAI Food License / Certificate of
 * Registration PDFs, styled consistently with {@link GstHtmlGenerator}.
 */
public class FssaiHtmlGenerator {

    private FssaiHtmlGenerator() {}

    /**
     * Generates a complete XHTML document for an FSSAI Certificate of Registration.
     *
     * @param licenseNumber   14-digit FSSAI license number
     * @param companyName     Food business operator / company name
     * @param contactPerson   Contact person for the FBO
     * @param kindOfBusiness  Kind of business (e.g., "Trade/Retail - Retailer")
     * @param licenseCategory License category (e.g., "Registration", "State License")
     * @param status          License status description
     * @param licenseActive   whether the license is currently active
     * @param state           State of the premises
     * @param district        District of the premises
     * @param taluk           Taluk of the premises
     * @param village         Village of the premises
     * @param address         Premises address
     * @param pincode         Premises pincode
     * @param contactEmail    Contact email of the FBO
     * @param panNo           PAN of the FBO
     * @param expiryDate      License expiry / valid-till date (dd-MM-yyyy)
     * @param fboId           Food Business Operator ID
     * @param refId           Reference ID of the license record
     * @return Complete XHTML string ready for PDF conversion
     */
    public static String generateCertificateHtml(String licenseNumber, String companyName, String contactPerson,
                                                  String kindOfBusiness, String licenseCategory, String status,
                                                  Boolean licenseActive, String state, String district, String taluk,
                                                  String village, String address, String pincode, String contactEmail,
                                                  String panNo, String expiryDate, String fboId, String refId) {
        String printDate = new SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.ENGLISH).format(new Date());

        String displayLicense = escapeXml(orDash(licenseNumber));
        String displayCompany = escapeXml(orDash(companyName));
        String displayContact = escapeXml(orDash(contactPerson));
        String displayKob = escapeXml(orDash(kindOfBusiness));
        String displayCategory = escapeXml(orDash(licenseCategory));
        String displayStatus = escapeXml(orDash(status));
        String displayState = escapeXml(orDash(state));
        String displayDistrict = escapeXml(orDash(district));
        String displayTaluk = escapeXml(orDash(taluk));
        String displayVillage = escapeXml(orDash(village));
        String displayAddress = escapeXml(orDash(address));
        String displayPincode = escapeXml(orDash(pincode));
        String displayEmail = escapeXml(orDash(contactEmail));
        String displayPan = escapeXml(orDash(panNo));
        String displayExpiry = escapeXml(orDash(expiryDate));
        String displayFboId = escapeXml(orDash(fboId));
        String displayRefId = escapeXml(orDash(refId));

        boolean active = licenseActive == null || licenseActive;
        boolean hasExpiry = expiryDate != null && !expiryDate.trim().isEmpty();

        StringBuilder premisesRows = new StringBuilder();
        premisesRows.append(row("Address of Premises", displayAddress));
        if (village != null && !village.trim().isEmpty()) {
            premisesRows.append(row("Village", displayVillage));
        }
        if (taluk != null && !taluk.trim().isEmpty()) {
            premisesRows.append(row("Taluk", displayTaluk));
        }
        premisesRows.append(row("District", displayDistrict));
        premisesRows.append(row("State / UT", displayState));
        premisesRows.append(row("Pincode", displayPincode));

        StringBuilder fboRows = new StringBuilder();
        fboRows.append(row("Name of Food Business Operator", displayCompany));
        if (contactPerson != null && !contactPerson.trim().isEmpty()) {
            fboRows.append(row("Contact Person", displayContact));
        }
        if (contactEmail != null && !contactEmail.trim().isEmpty()) {
            fboRows.append(row("Contact Email", displayEmail));
        }
        if (panNo != null && !panNo.trim().isEmpty()) {
            fboRows.append(row("PAN No.", displayPan));
        }
        if (kindOfBusiness != null && !kindOfBusiness.trim().isEmpty()) {
            fboRows.append(row("Kind of Business (KOB Name)", displayKob));
        }

        StringBuilder licenceRows = new StringBuilder();
        licenceRows.append(row("License Category", displayCategory));
        licenceRows.append(row("Status",
                "<span class=\"status-badge " + (active ? "badge-active" : "badge-inactive") + "\">"
                        + displayStatus.toUpperCase() + "</span>"));
        // Expiry date is mandatory - show even if not available
        licenceRows.append(row("Validity / Expiry Date", hasExpiry ? displayExpiry : "Not Available"));
        if (fboId != null && !fboId.trim().isEmpty()) {
            licenceRows.append(row("FBO ID", displayFboId));
        }
        if (refId != null && !refId.trim().isEmpty()) {
            licenceRows.append(row("Reference ID", displayRefId));
        }
        // Add kind of business as KOB Name if available
        if (kindOfBusiness != null && !kindOfBusiness.trim().isEmpty()) {
            licenceRows.append(row("Kind of Business (KOB)", displayKob));
        }

        return "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>\n" +
                "<style>\n" +
                "  @page { size: A4 portrait; margin: 10mm 14mm; }\n" +
                "  * { box-sizing: border-box; margin:0; padding:0; }\n" +
                "  body { font-family: 'Times New Roman', Times, Georgia, serif; font-size:9pt; color:#1a1a1a; line-height:1.3; background:#fff; }\n" +
                "\n" +
                "  /* ===== HEADER ===== */\n" +
                "  .header { text-align:center; padding:8px 16px 6px; border-bottom:2px solid #1B3A5C; }\n" +
                "  .header-dept { font-size:8.5pt; letter-spacing:1px; color:#444; margin-bottom:1px; }\n" +
                "  .header-board { font-size:8pt; letter-spacing:0.8px; color:#666; }\n" +
                "\n" +
                "  /* ===== TITLE ===== */\n" +
                "  .title { text-align:center; font-size:13pt; font-weight:bold; color:#1B3A5C; text-transform:uppercase; letter-spacing:2px; padding:7px 0 5px; border-bottom:1px solid #ccc; margin-bottom:7px; }\n" +
                "\n" +
                "  /* ===== LICENSE NUMBER ===== */\n" +
                "  .lic-box { text-align:center; padding:5px 16px; margin:0 0 7px; }\n" +
                "  .lic-label { font-size:7pt; color:#666; text-transform:uppercase; letter-spacing:2px; margin-bottom:2px; }\n" +
                "  .lic-value { font-family: 'Courier New', Courier, monospace; font-size:14pt; font-weight:bold; color:#1B3A5C; letter-spacing:3px; }\n" +
                "\n" +
                "  /* ===== CONTENT ===== */\n" +
                "  .content { padding: 0 4px; }\n" +
                "\n" +
                "  /* ===== SECTION HEADERS ===== */\n" +
                "  .section { background:#1B3A5C; color:#fff; font-size:8pt; font-weight:bold; padding:4px 10px; margin-top:7px; letter-spacing:1.5px; text-transform:uppercase; }\n" +
                "\n" +
                "  /* ===== TABLES ===== */\n" +
                "  table { width:100%; border-collapse:collapse; margin:0; }\n" +
                "  td { padding:4px 8px; vertical-align:top; border-bottom:1px solid #E2E8F0; font-size:8.5pt; }\n" +
                "  tr:last-child td { border-bottom:none; }\n" +
                "\n" +
                "  /* ===== FIELD ROWS ===== */\n" +
                "  .lbl { width:36%; font-weight:bold; color:#4A5568; font-size:8pt; text-transform:uppercase; letter-spacing:0.3px; padding-right:8px; vertical-align:middle; }\n" +
                "  .val { color:#1a1a1a; font-weight:600; font-size:9pt; }\n" +
                "\n" +
                "  /* Status */\n" +
                "  .status-badge { display:inline-block; padding:1px 8px; border-radius:3px; font-size:7.5pt; font-weight:bold; letter-spacing:0.5px; }\n" +
                "  .badge-active { background:#E6F4EA; color:#1B7A3D; border:1px solid #A8DAB5; }\n" +
                "  .badge-inactive { background:#FEE2E2; color:#C53030; border:1px solid #F5B7B7; }\n" +
                "\n" +
                "  /* ===== CERTIFICATION ===== */\n" +
                "  .cert-box { background:#FAFBFC; border:1px solid #E2E8F0; margin:7px 0; padding:7px 12px; font-size:8pt; color:#4A5568; line-height:1.35; }\n" +
                "  .cert-box .heading { font-size:8pt; font-weight:bold; color:#1B3A5C; margin-bottom:4px; text-transform:uppercase; letter-spacing:1px; }\n" +
                "\n" +
                "  /* ===== SIGNATURE ===== */\n" +
                "  .signature-area { margin-top:8px; padding-top:6px; border-top:1px solid #E2E8F0; }\n" +
                "  .sig-row { display:flex; justify-content:space-between; }\n" +
                "  .sig-box { width:45%; text-align:center; }\n" +
                "  .sig-line { border-top:1px solid #1a1a1a; margin-top:16px; padding-top:3px; font-size:7.5pt; color:#666; text-transform:uppercase; letter-spacing:0.5px; }\n" +
                "\n" +
                "  /* ===== FOOTER ===== */\n" +
                "  .footer { border-top:1px solid #ccc; padding:6px 14px 2px; font-size:7pt; color:#999; text-align:center; letter-spacing:0.3px; margin-top:7px; }\n" +
                "\n" +
                "  .mb-0 { margin-bottom:0; }\n" +
                "</style></head><body>\n" +
                "<div class=\"header\">\n" +
                "  <p class=\"header-dept\">Food Safety and Standards Authority of India</p>\n" +
                "  <p class=\"header-board\">FSSAI</p>\n" +
                "</div>\n" +
                "\n" +
                "<div class=\"title\">Certificate of Registration</div>\n" +
                "\n" +
                "<div class=\"lic-box\">\n" +
                "  <div class=\"lic-label\">FSSAI License / Registration Number</div>\n" +
                "  <div class=\"lic-value\">" + displayLicense + "</div>\n" +
                "</div>\n" +
                "\n" +
                "<div class=\"content\">\n" +
                "  <div class=\"section\">Food Business Operator</div>\n" +
                "  <table>\n" + fboRows + "  </table>\n" +
                "\n" +
                "  <div class=\"section\">Address of Premises</div>\n" +
                "  <table>\n" + premisesRows + "  </table>\n" +
                "\n" +
                "  <div class=\"section\">License Particulars</div>\n" +
                "  <table>\n" + licenceRows + "  </table>\n" +
                "\n" +
                "  <div class=\"cert-box\">\n" +
                "    <div class=\"heading\">Certification</div>\n" +
                "    <p>This is to certify that the above-named Food Business Operator is registered under the provisions of the Food Safety and Standards Act, 2006 and the regulations made thereunder. The license status and validity reflected herein are as on the date of generation of this certificate.</p>\n" +
                "    <p class=\"mb-0\" style=\"margin-top:6px;\"><strong>Note:</strong> This is a computer-generated certificate and does not require a physical signature.</p>\n" +
                "  </div>\n" +
                "\n" +
                "  <div class=\"signature-area\">\n" +
                "    <div style=\"margin-top: 16px;\">\n" +
                "      <img src=\"data:image/png;base64,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\" alt=\"Verified\" style=\"width: 16px; height: 16px; vertical-align: middle; margin-right: 6px;\" />\n" +
                "      <span style=\"font-size: 9pt; color: #1B3A5C; font-weight: bold; letter-spacing: 0.5px; vertical-align: middle;\">Digitally signed and verified by DukaanLocker</span>\n" +
                "    </div>\n" +
                "    <div style=\"text-align: right; font-size: 8pt; color: #666; margin-top: -16px;\">\n" +
                "      Date: " + printDate + " IST\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</div>\n" +
                "\n" +

                "</body></html>";
    }

    private static String row(String label, String value) {
        return "    <tr><td class=\"lbl\">" + label + "</td><td class=\"val\">" + value + "</td></tr>\n";
    }

    private static String orDash(String text) {
        return (text == null || text.trim().isEmpty()) ? "-" : text;
    }

    /**
     * Escapes special XML characters for safe HTML embedding.
     */
    private static String escapeXml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
