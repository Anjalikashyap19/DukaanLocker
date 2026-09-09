package com.shoplocker.fssai.util;

import com.shoplocker.fssai.dto.MsmeParsedData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Generates professional XHTML templates for MSME (Udyam) Registration Certificate PDFs.
 * Takes parsed fields as parameters instead of re-parsing raw HTML from the government portal.
 */
public class MsmeHtmlGenerator {

    private MsmeHtmlGenerator() {}

    /**
     * Generates a complete XHTML document for an MSME Registration Certificate.
     *
     * @param data parsed MSME data from the government portal
     * @return Complete XHTML string ready for PDF conversion
     */
    public static String generateCertificateHtml(MsmeParsedData data) {
        String printDate = new SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.ENGLISH).format(new Date());

        String udyamNumber = escapeXml(data.getUdyamNumber() != null ? data.getUdyamNumber() : "-");
        String enterpriseName = escapeXml(data.getEnterpriseName() != null ? data.getEnterpriseName() : "-");
        String entrepreneurName = escapeXml(data.getEntrepreneurName() != null ? data.getEntrepreneurName() : "-");
        String enterpriseType = escapeXml(data.getEnterpriseType() != null ? data.getEnterpriseType() : "-");
        String majorActivity = escapeXml(data.getMajorActivity() != null ? data.getMajorActivity() : "-");
        String typeOfOrganization = escapeXml(data.getTypeOfOrganization() != null ? data.getTypeOfOrganization() : "-");
        String address = escapeXml(data.getAddress() != null ? data.getAddress() : "-");
        String city = escapeXml(data.getCity() != null ? data.getCity() : "-");
        String state = escapeXml(data.getState() != null ? data.getState() : "-");
        String district = escapeXml(data.getDistrict() != null ? data.getDistrict() : "-");
        String pincode = escapeXml(data.getPincode() != null ? data.getPincode() : "-");
        String mobile = escapeXml(data.getMobileNumber() != null ? data.getMobileNumber() : "-");
        String email = escapeXml(data.getEmailId() != null ? data.getEmailId() : "-");

        // Determine enterprise type checkmarks
        String microCheck = enterpriseType.toLowerCase().contains("micro") ? "\u2713" : "-";
        String smallCheck = enterpriseType.toLowerCase().contains("small") ? "\u2713" : "-";
        String mediumCheck = enterpriseType.toLowerCase().contains("medium") ? "\u2713" : "-";

        return "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>\n" +
                "<style>\n" +
                "  @page { size: A4 portrait; margin: 12mm 14mm; }\n" +
                "  * { box-sizing: border-box; margin:0; padding:0; }\n" +
                "  body { font-family: 'Times New Roman', Times, Georgia, serif; font-size:10pt; color:#1a1a1a; line-height:1.45; background:#fff; }\n" +
                "\n" +
                "  /* ===== HEADER ===== */\n" +
                "  .header { text-align:center; padding:14px 16px 10px; border-bottom:2px solid #57528c; }\n" +
                "  .header-ministry { font-size:8pt; letter-spacing:2px; text-transform:uppercase; color:#666; margin-bottom:2px; }\n" +
                "  .header-govt { font-size:13pt; font-weight:bold; letter-spacing:1.5px; text-transform:uppercase; color:#57528c; margin-bottom:3px; }\n" +
                "  .header-dept { font-size:9pt; letter-spacing:1px; color:#444; margin-bottom:1px; }\n" +
                "  .header-office { font-size:8.5pt; letter-spacing:0.8px; color:#666; font-style:italic; }\n" +
                "\n" +
                "  /* ===== TITLE ===== */\n" +
                "  .title { text-align:center; font-size:15pt; font-weight:bold; color:#57528c; text-transform:uppercase; letter-spacing:2px; padding:12px 0 8px; border-bottom:1px solid #ccc; margin-bottom:6px; }\n" +
                "  .reg-number { text-align:right; font-size:9pt; font-weight:bold; color:#57528c; padding:4px 0 8px; }\n" +
                "\n" +
                "  /* ===== ENTERPRISE NAME ===== */\n" +
                "  .enterprise-box { text-align:center; padding:10px 16px; margin:0 0 10px; border:1px solid #ddd; background:#FAFAFA; }\n" +
                "  .enterprise-label { font-size:8pt; color:#666; text-transform:uppercase; letter-spacing:2px; margin-bottom:4px; }\n" +
                "  .enterprise-value { font-size:14pt; font-weight:bold; color:#57528c; letter-spacing:0.5px; }\n" +
                "\n" +
                "  /* ===== CLASSIFICATION TABLE ===== */\n" +
                "  .class-table { width:100%; border-collapse:collapse; margin:8px 0; }\n" +
                "  .class-table th { background:#F3F0F9; font-size:8pt; padding:5px 8px; border:1px solid #ccc; text-transform:uppercase; letter-spacing:0.5px; }\n" +
                "  .class-table td { font-size:9pt; padding:5px 8px; border:1px solid #ccc; text-align:center; }\n" +
                "  .class-table .left { text-align:left; }\n" +
                "\n" +
                "  /* ===== SECTION HEADERS ===== */\n" +
                "  .section { background:#57528c; color:#fff; font-size:8.5pt; font-weight:bold; padding:5px 10px; margin:10px 0 0; letter-spacing:1.5px; text-transform:uppercase; }\n" +
                "\n" +
                "  /* ===== DATA TABLES ===== */\n" +
                "  table { width:100%; border-collapse:collapse; margin:0; }\n" +
                "  td { padding:6px 10px; vertical-align:top; border-bottom:1px solid #E2E8F0; font-size:9.5pt; }\n" +
                "  tr:last-child td { border-bottom:none; }\n" +
                "\n" +
                "  /* ===== FIELD ROWS ===== */\n" +
                "  .lbl { width:38%; font-weight:bold; color:#4A5568; font-size:9pt; text-transform:uppercase; letter-spacing:0.3px; padding-right:8px; vertical-align:middle; }\n" +
                "  .val { color:#1a1a1a; font-weight:600; font-size:10pt; }\n" +
                "\n" +
                "  /* Address Table */\n" +
                "  .address-table td { padding:5px 10px; font-size:9pt; }\n" +
                "  .address-table .side { width:38%; font-weight:bold; color:#4A5568; font-size:9pt; text-transform:uppercase; background:#F3F0F9; text-align:center; vertical-align:middle; }\n" +
                "\n" +
                "  /* Contact Table */\n" +
                "  .contact-table td { padding:5px 10px; font-size:9pt; }\n" +
                "\n" +
                "  /* ===== CERTIFICATION ===== */\n" +
                "  .cert-box { background:#FAFBFC; border:1px solid #E2E8F0; margin:10px 0; padding:10px 14px; font-size:9pt; color:#4A5568; line-height:1.5; }\n" +
                "  .cert-box .heading { font-size:9pt; font-weight:bold; color:#57528c; margin-bottom:6px; text-transform:uppercase; letter-spacing:1px; }\n" +
                "\n" +
                "  /* ===== SIGNATURE ===== */\n" +
                "  .signature-area { margin-top:16px; padding-top:8px; border-top:1px solid #E2E8F0; }\n" +
                "  .sig-row { display:flex; justify-content:space-between; }\n" +
                "  .sig-box { width:45%; text-align:center; }\n" +
                "  .sig-line { border-top:1px solid #1a1a1a; margin-top:30px; padding-top:4px; font-size:8pt; color:#666; text-transform:uppercase; letter-spacing:0.5px; }\n" +
                "\n" +
                "  /* ===== FOOTER ===== */\n" +
                "  .footer { border-top:1px solid #ccc; padding:8px 14px 4px; font-size:7.5pt; color:#999; text-align:center; letter-spacing:0.3px; margin-top:10px; }\n" +
                "\n" +
                "  .mb-0 { margin-bottom:0; }\n" +
                "</style></head><body>\n" +
                "<div class=\"header\">\n" +
                "  <p class=\"header-ministry\">Ministry of Micro, Small &amp; Medium Enterprises</p>\n" +
                "  <p class=\"header-govt\">Government of India</p>\n" +
                "  <p class=\"header-dept\">Udyam Registration Portal</p>\n" +
                "  <p class=\"header-office\">Office of Development Commissioner (MSME)</p>\n" +
                "</div>\n" +
                "\n" +
                "<div class=\"title\">Udyam Registration Certificate</div>\n" +
                "<div class=\"reg-number\">UDYAM REGISTRATION NUMBER: " + udyamNumber + "</div>\n" +
                "\n" +
                "<div class=\"enterprise-box\">\n" +
                "  <div class=\"enterprise-label\">Name of Enterprise</div>\n" +
                "  <div class=\"enterprise-value\">" + enterpriseName + "</div>\n" +
                "</div>\n" +
                "\n" +
                "<div class=\"section\">Enterprise Classification</div>\n" +
                "<table class=\"class-table\">\n" +
                "  <tr><th>Type of Enterprise</th><th>Micro</th><th>Small</th><th>Medium</th><th>Date of Registration</th></tr>\n" +
                "  <tr><td></td><td>" + microCheck + "</td><td>" + smallCheck + "</td><td>" + mediumCheck + "</td><td>" + escapeXml(data.getUdyamNumber() != null ? "Registered" : "-") + "</td></tr>\n" +
                "</table>\n" +
                "\n" +
                "<div class=\"section\">Enterprise Details</div>\n" +
                "<table>\n" +
                "  <tr><td class=\"lbl\">Entrepreneur Name</td><td class=\"val\">" + entrepreneurName + "</td></tr>\n" +
                "  <tr><td class=\"lbl\">Major Activity</td><td class=\"val\">" + majorActivity + "</td></tr>\n" +
                "  <tr><td class=\"lbl\">Type of Organization</td><td class=\"val\">" + typeOfOrganization + "</td></tr>\n" +
                "  <tr><td class=\"lbl\">Enterprise Type</td><td class=\"val\">" + enterpriseType + "</td></tr>\n" +
                "</table>\n" +
                "\n" +
                "<div class=\"section\">Official Address of Enterprise</div>\n" +
                "<table class=\"address-table\">\n" +
                "  <tr><td class=\"side\" rowspan=\"4\">Address</td><td>" + address + "</td></tr>\n" +
                "  <tr><td>Village/Town/City: " + city + "</td></tr>\n" +
                "  <tr><td>District: " + district + ", State: " + state + "</td></tr>\n" +
                "  <tr><td>Pincode: " + pincode + "</td></tr>\n" +
                "</table>\n" +
                "\n" +
                "<div class=\"section\">Contact Details</div>\n" +
                "<table class=\"contact-table\">\n" +
                "  <tr><td class=\"lbl\">Mobile Number</td><td class=\"val\">" + mobile + "</td></tr>\n" +
                "  <tr><td class=\"lbl\">Email ID</td><td class=\"val\">" + email + "</td></tr>\n" +
                "</table>\n" +
                "\n" +
                "<div class=\"cert-box\">\n" +
                "  <div class=\"heading\">Certification</div>\n" +
                "  <p>This is to certify that the above-named enterprise is registered under the provisions of the Micro, Small and Medium Enterprises Development Act, 2006.</p>\n" +
                "  <p class=\"mb-0\" style=\"margin-top:6px;\"><strong>Note:</strong> This is a computer-generated certificate based on details furnished by the enterprise on the Udyam Registration portal.</p>\n" +
                "</div>\n" +
                "\n" +
                "<div class=\"signature-area\">\n" +
                "  <div class=\"sig-row\">\n" +
                "    <div class=\"sig-box\">\n" +
                "      <div class=\"sig-line\">Generating Authority</div>\n" +
                "    </div>\n" +
                "    <div class=\"sig-box\">\n" +
                "      <div class=\"sig-line\">Certifying Officer</div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</div>\n" +
                "\n" +
                "<div class=\"footer\">\n" +
                "  Generated by DukaanLocker on " + printDate + " | This certificate is for informational purposes only\n" +
                "</div>\n" +
                "</body></html>";
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
