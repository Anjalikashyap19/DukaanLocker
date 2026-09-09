package com.shoplocker.fssai.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Generates professional XHTML templates for GST Registration Certificate PDFs.
 * Similar to {@link MsmeDataParser} but focused on HTML generation for GST documents.
 */
public class GstHtmlGenerator {

    private GstHtmlGenerator() {}

    /**
     * Generates a complete XHTML document for a GST Registration Certificate.
     *
     * @param gstin           GSTIN number
     * @param legalName       Legal name of the taxpayer
     * @param tradeName       Trade name (business name)
     * @param registrationDate Date of registration
     * @param status          Registration status (e.g., "Active")
     * @param state           State jurisdiction
     * @param constitutionOfBusiness Constitution of business (e.g., "Proprietorship")
     * @param principalPlaceAddress Address of principal place of business
     * @param centralJurisdiction Central jurisdiction for approving authority
     * @param centralJurisdictionCode Central jurisdiction code
     * @param stateJurisdictionCode State jurisdiction code
     * @param dateOfIssue Date of issue of certificate
     * @param periodOfValidity Period of validity string
     * @param typeOfRegistration Type of registration (e.g., "Regular")
     * @return Complete XHTML string ready for PDF conversion
     */
    public static String generateCertificateHtml(String gstin, String legalName, String tradeName,
                                                  String registrationDate, String status, String state,
                                                  String stateJurisdictionCode,
                                                  String constitutionOfBusiness, String principalPlaceAddress,
                                                  String centralJurisdiction, String centralJurisdictionCode,
                                                  String dateOfIssue, String periodOfValidity,
                                                  String typeOfRegistration) {
        String printDate = new SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.ENGLISH).format(new Date());

        String displayLegalName = escapeXml(legalName != null ? legalName : "-");
        String displayTradeName = escapeXml(tradeName != null ? tradeName : "-");
        String displayRegDate = escapeXml(registrationDate != null ? registrationDate : "-");
        String displayStatus = escapeXml(status != null ? status : "-");
        String displayState = escapeXml(state != null ? state : "-");
        String displayStateCode = escapeXml(stateJurisdictionCode != null ? stateJurisdictionCode : "-");
        String displayGstin = escapeXml(gstin != null ? gstin : "-");
        String displayConstitution = escapeXml(constitutionOfBusiness != null ? constitutionOfBusiness : "-");
        String displayAddress = escapeXml(principalPlaceAddress != null ? principalPlaceAddress : "-");
        String displayJurisdiction = escapeXml(centralJurisdiction != null ? centralJurisdiction : "-");
        String displayJurisdictionCode = escapeXml(centralJurisdictionCode != null ? centralJurisdictionCode : "-");
        String displayDateOfIssue = escapeXml(dateOfIssue != null ? dateOfIssue : "-");
        String displayPeriod = escapeXml(periodOfValidity != null ? periodOfValidity : "-");
        String displayRegType = escapeXml(typeOfRegistration != null ? typeOfRegistration : "-");

        return "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>\n" +
                "<style>\n" +
                "  @page { size: A4 portrait; margin: 14mm 16mm; }\n" +
                "  * { box-sizing: border-box; margin:0; padding:0; }\n" +
                "  body { font-family: 'Times New Roman', Times, Georgia, serif; font-size:10pt; color:#1a1a1a; line-height:1.45; background:#fff; }\n" +
                "\n" +
                "  /* ===== HEADER ===== */\n" +
                "  .header { text-align:center; padding:14px 16px 10px; border-bottom:2px solid #1B3A5C; }\n" +
                "  .header-ministry { font-size:8pt; letter-spacing:2px; text-transform:uppercase; color:#666; margin-bottom:2px; }\n" +
                "  .header-govt { font-size:13pt; font-weight:bold; letter-spacing:1.5px; text-transform:uppercase; color:#1B3A5C; margin-bottom:3px; }\n" +
                "  .header-dept { font-size:9pt; letter-spacing:1px; color:#444; margin-bottom:1px; }\n" +
                "  .header-board { font-size:8.5pt; letter-spacing:0.8px; color:#666; }\n" +
                "\n" +
                "  /* ===== TITLE ===== */\n" +
                "  .title { text-align:center; font-size:15pt; font-weight:bold; color:#1B3A5C; text-transform:uppercase; letter-spacing:2px; padding:12px 0 8px; border-bottom:1px solid #ccc; margin-bottom:10px; }\n" +
                "\n" +
                "  /* ===== GSTIN ===== */\n" +
                "  .gstin-box { text-align:center; padding:10px 16px; margin:0 0 12px; }\n" +
                "  .gstin-label { font-size:7.5pt; color:#666; text-transform:uppercase; letter-spacing:2px; margin-bottom:4px; }\n" +
                "  .gstin-value { font-family: 'Courier New', Courier, monospace; font-size:17pt; font-weight:bold; color:#1B3A5C; letter-spacing:3px; }\n" +
                "\n" +
                "  /* ===== CONTENT ===== */\n" +
                "  .content { padding: 0 4px; }\n" +
                "\n" +
                "  /* ===== SECTION HEADERS ===== */\n" +
                "  .section { background:#1B3A5C; color:#fff; font-size:8.5pt; font-weight:bold; padding:5px 10px; margin:10px 0 0; letter-spacing:1.5px; text-transform:uppercase; }\n" +
                "\n" +
                "  /* ===== TABLES ===== */\n" +
                "  table { width:100%; border-collapse:collapse; margin:0; }\n" +
                "  td { padding:6px 10px; vertical-align:top; border-bottom:1px solid #E2E8F0; font-size:9.5pt; }\n" +
                "  tr:last-child td { border-bottom:none; }\n" +
                "\n" +
                "  /* ===== FIELD ROWS ===== */\n" +
                "  .lbl { width:38%; font-weight:bold; color:#4A5568; font-size:9pt; text-transform:uppercase; letter-spacing:0.3px; padding-right:8px; vertical-align:middle; }\n" +
                "  .val { color:#1a1a1a; font-weight:600; font-size:10pt; }\n" +
                "\n" +
                "  /* Status */\n" +
                "  .status-active { color:#1B7A3D; font-weight:bold; }\n" +
                "  .status-inactive { color:#C53030; font-weight:bold; }\n" +
                "  .status-badge { display:inline-block; padding:1px 8px; border-radius:3px; font-size:8pt; font-weight:bold; letter-spacing:0.5px; }\n" +
                "  .badge-active { background:#E6F4EA; color:#1B7A3D; border:1px solid #A8DAB5; }\n" +
                "  .badge-inactive { background:#FEE2E2; color:#C53030; border:1px solid #F5B7B7; }\n" +
                "\n" +
                "  /* ===== CERTIFICATION ===== */\n" +
                "  .cert-box { background:#FAFBFC; border:1px solid #E2E8F0; margin:10px 0; padding:10px 14px; font-size:9pt; color:#4A5568; line-height:1.5; }\n" +
                "  .cert-box .heading { font-size:9pt; font-weight:bold; color:#1B3A5C; margin-bottom:6px; text-transform:uppercase; letter-spacing:1px; }\n" +
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
                "  <p class=\"header-ministry\">Ministry of Finance, Department of Revenue</p>\n" +
                "  <p class=\"header-govt\">Government of India</p>\n" +
                "  <p class=\"header-dept\">Central Board of Indirect Taxes and Customs</p>\n" +
                "  <p class=\"header-board\">Goods and Services Tax Network</p>\n" +
                "</div>\n" +
                "\n" +
                "<div class=\"title\">Certificate of Registration under GST</div>\n" +
                "\n" +
                "<div class=\"gstin-box\">\n" +
                "  <div class=\"gstin-label\">GST Identification Number (GSTIN)</div>\n" +
                "  <div class=\"gstin-value\">" + displayGstin + "</div>\n" +
                "</div>\n" +
                "\n" +
                "<div class=\"content\">\n" +
                "  <div class=\"section\">Taxpayer Information</div>\n" +
                "  <table>\n" +
                "    <tr><td class=\"lbl\">Legal Name</td><td class=\"val\">" + displayLegalName + "</td></tr>\n" +
                "    <tr><td class=\"lbl\">Trade Name</td><td class=\"val\">" + displayTradeName + "</td></tr>\n" +
                "    <tr><td class=\"lbl\">Date of Registration</td><td class=\"val\">" + displayRegDate + "</td></tr>\n" +
                "    <tr><td class=\"lbl\">Status</td><td class=\"val\"><span class=\"status-badge " + (displayStatus.equalsIgnoreCase("Active") ? "badge-active" : "badge-inactive") + "\">" + displayStatus.toUpperCase() + "</span></td></tr>\n" +
                "    <tr><td class=\"lbl\">State / UT</td><td class=\"val\">" + displayState + "</td></tr>\n" +
                "    <tr><td class=\"lbl\">State Jurisdiction Code</td><td class=\"val\">" + displayStateCode + "</td></tr>\n" +
                "  </table>\n" +
                "\n" +
                "  <div class=\"section\">Registration Particulars</div>\n" +
                "  <table>\n" +
                "    <tr><td class=\"lbl\">Type of Registration</td><td class=\"val\">" + displayRegType + "</td></tr>\n" +
                "    <tr><td class=\"lbl\">Period of Validity</td><td class=\"val\">" + displayPeriod + "</td></tr>\n" +
                "    <tr><td class=\"lbl\">Date of Issue</td><td class=\"val\">" + displayDateOfIssue + "</td></tr>\n" +
                "  </table>\n" +
                "\n" +
                "  <div class=\"section\">Principal Place of Business</div>\n" +
                "  <table>\n" +
                "    <tr><td class=\"lbl\">Constitution of Business</td><td class=\"val\">" + displayConstitution + "</td></tr>\n" +
                "    <tr><td class=\"lbl\">Address</td><td class=\"val\">" + displayAddress + "</td></tr>\n" +
                "  </table>\n" +
                "\n" +
                "  <div class=\"section\">Jurisdictional Authority</div>\n" +
                "  <table>\n" +
                "    <tr><td class=\"lbl\">Approving Authority</td><td class=\"val\">Central Board of Indirect Taxes and Customs</td></tr>\n" +
                "    <tr><td class=\"lbl\">State Jurisdiction</td><td class=\"val\">" + displayState + " (" + displayStateCode + ")</td></tr>\n" +
                "    <tr><td class=\"lbl\">Central Jurisdiction</td><td class=\"val\">" + displayJurisdiction + " (" + displayJurisdictionCode + ")</td></tr>\n" +
                "  </table>\n" +
                "\n" +
                "  <div class=\"cert-box\">\n" +
                "    <div class=\"heading\">Certification</div>\n" +
                "    <p>This is to certify that the above-named taxpayer is registered under the provisions of the Goods and Services Tax Act, 2017. The GSTIN is valid and the registration status reflected herein is as on the date of generation of this certificate.</p>\n" +
                "    <p class=\"mb-0\" style=\"margin-top:6px;\"><strong>Note:</strong> This is a computer-generated certificate and does not require a physical signature.</p>\n" +
                "  </div>\n" +
                "\n" +
                "  <div class=\"signature-area\">\n" +
                "    <div class=\"sig-row\">\n" +
                "      <div class=\"sig-box\">\n" +
                "        <div class=\"sig-line\">Generating Authority</div>\n" +
                "      </div>\n" +
                "      <div class=\"sig-box\">\n" +
                "        <div class=\"sig-line\">Certifying Officer</div>\n" +
                "      </div>\n" +
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
