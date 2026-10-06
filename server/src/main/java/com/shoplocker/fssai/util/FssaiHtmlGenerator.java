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
                "  .header-ministry { font-size:7.5pt; letter-spacing:2px; text-transform:uppercase; color:#666; margin-bottom:1px; }\n" +
                "  .header-govt { font-size:11.5pt; font-weight:bold; letter-spacing:1.5px; text-transform:uppercase; color:#1B3A5C; margin-bottom:2px; }\n" +
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
                "    <div style=\"text-align: center; margin-top: 16px;\">\n" +
                "      <span style=\"color: #28a745; font-size: 20px; font-weight: bold; margin-right: 6px;\">✓</span>\n" +
                "      <span style=\"font-size: 9pt; color: #1B3A5C; font-weight: bold; letter-spacing: 0.5px;\">Digitally signed and verified by DukaanLocker</span>\n" +
                "    </div>\n" +
                "    <div style=\"text-align: right; font-size: 8pt; color: #666; margin-top: 16px;\">\n" +
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
