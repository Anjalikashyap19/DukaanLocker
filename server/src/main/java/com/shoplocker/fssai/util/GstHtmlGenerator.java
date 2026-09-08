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
     * @param dateOfIssue Date of issue of certificate
     * @param periodOfValidity Period of validity string
     * @param typeOfRegistration Type of registration (e.g., "Regular")
     * @return Complete XHTML string ready for PDF conversion
     */
    public static String generateCertificateHtml(String gstin, String legalName, String tradeName,
                                                  String registrationDate, String status, String state,
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
                "  * { box-sizing: border-box; }\n" +
                "  body { margin:0; padding:0; font-family: Arial, Helvetica, sans-serif; font-size:10pt; color:#111; line-height:1.4; }\n" +
                "  .certificate { padding:4px; background:#fff; }\n" +
                "\n" +
                "  /* ===== HEADER ===== */\n" +
                "  .top { text-align:center; padding:12px 10px; border-bottom:2px solid #333; margin-bottom:8px; }\n" +
                "  .top-title { font-size:10pt; font-weight:bold; letter-spacing:2px; margin:0; text-transform:uppercase; }\n" +
                "  .top-govt { font-size:11pt; font-weight:bold; margin:4px 0 2px; letter-spacing:1px; text-transform:uppercase; }\n" +
                "  .top-dept { font-size:9pt; margin:1px 0; letter-spacing:0.5px; }\n" +
                "  .top-sub { font-size:8pt; margin:3px 0 0; font-style:italic; }\n" +
                "\n" +
                "  /* ===== TITLE ===== */\n" +
                "  .title { text-align:center; font-family:'Times New Roman',Times,serif; font-size:16pt; font-weight:bold; color:#111; padding:8px 0 4px; text-transform:uppercase; letter-spacing:1px; border-bottom:1px solid #333; margin-bottom:6px; }\n" +
                "\n" +
                "  /* ===== GSTIN ===== */\n" +
                "  .gstin-row { text-align:center; margin:6px 0 10px; }\n" +
                "  .gstin-label { font-size:9pt; color:#555; text-transform:uppercase; letter-spacing:1px; margin-bottom:3px; }\n" +
                "  .gstin-value { font-family:'Courier New',Courier,monospace; font-size:16pt; font-weight:bold; color:#111; letter-spacing:2px; }\n" +
                "\n" +
                "  /* ===== TABLES ===== */\n" +
                "  table { width:100%; border-collapse:collapse; margin-bottom:6px; }\n" +
                "  td, th { border:1px solid #999; padding:6px 10px; vertical-align:middle; }\n" +
                "  .noborder td { border:none; padding:7px 10px; }\n" +
                "\n" +
                "  /* ===== FIELD ROWS ===== */\n" +
                "  .label { width:40%; font-family:'Times New Roman',Times,serif; font-weight:bold; font-size:10pt; color:#333; background:#f5f5f5; }\n" +
                "  .value { font-weight:bold; font-size:10.5pt; color:#111; }\n" +
                "\n" +
                "  /* Status Badge */\n" +
                "  .status-active { color:#2E7D32; font-weight:bold; }\n" +
                "  .status-inactive { color:#C62828; font-weight:bold; }\n" +
                "\n" +
                "  /* Section Header */\n" +
                "  .section { background:#444; color:#fff; text-align:center; font-family:'Times New Roman',Times,serif; font-size:10pt; font-weight:bold; padding:4px; margin:8px 0; letter-spacing:0.5px; }\n" +
                "\n" +
                "  /* Notes */\n" +
                "  .note { font:9pt 'Times New Roman',Times,serif; line-height:1.4; padding:8px 10px; background:#fafafa; border:1px solid #ddd; margin-top:8px; }\n" +
                "  .note p { margin:0 0 5px; }\n" +
                "  .note .bold { font-weight:bold; }\n" +
                "\n" +
                "  .space { height:4px; }\n" +
                "</style></head><body><div class=\"certificate\">\n" +
                "  <div class=\"top\">\n" +
                "    <p class=\"top-title\">GOODS AND SERVICES TAX</p>\n" +
                "    <p class=\"top-govt\">Government of India</p>\n" +
                "    <p class=\"top-dept\">Central Board of Indirect Taxes and Customs</p>\n" +
                "    <p class=\"top-sub\">Ministry of Finance, Department of Revenue</p>\n" +
                "  </div>\n" +
                "\n" +
                "  <div class=\"title\">GST REGISTRATION CERTIFICATE</div>\n" +
                "\n" +
                "  <div class=\"gstin-row\">\n" +
                "    <div class=\"gstin-label\">GST Identification Number (GSTIN)</div>\n" +
                "    <div class=\"gstin-value\">" + displayGstin + "</div>\n" +
                "  </div>\n" +
                "\n" +
                "  <div class=\"section\">TAXPAYER DETAILS</div>\n" +
                "\n" +
                "  <table>\n" +
                "    <tr><td class=\"label\">Legal Name of Taxpayer</td><td class=\"value\">" + displayLegalName + "</td></tr>\n" +
                "    <tr><td class=\"label\">Trade Name / Business Name</td><td class=\"value\">" + displayTradeName + "</td></tr>\n" +
                "    <tr><td class=\"label\">Date of Registration</td><td class=\"value\">" + displayRegDate + "</td></tr>\n" +
                "    <tr><td class=\"label\">Registration Status</td><td class=\"value " + (displayStatus.equalsIgnoreCase("Active") ? "status-active" : "status-inactive") + "\">" + displayStatus + "</td></tr>\n" +
                "    <tr><td class=\"label\">State / Union Territory</td><td class=\"value\">" + displayState + "</td></tr>\n" +
                "  </table>\n" +
                "\n" +
                "  <div class=\"space\"></div>\n" +
                "\n" +
                "  <div class=\"section\">REGISTRATION DETAILS</div>\n" +
                "\n" +
                "  <table>\n" +
                "    <tr><td class=\"label\">Type of Registration</td><td class=\"value\">" + displayRegType + "</td></tr>\n" +
                "    <tr><td class=\"label\">Period of Validity</td><td class=\"value\">" + displayPeriod + "</td></tr>\n" +
                "    <tr><td class=\"label\">Date of Issue of Certificate</td><td class=\"value\">" + displayDateOfIssue + "</td></tr>\n" +
                "  </table>\n" +
                "\n" +
                "  <div class=\"space\"></div>\n" +
                "\n" +
                "  <div class=\"section\">BUSINESS DETAILS</div>\n" +
                "\n" +
                "  <table>\n" +
                "    <tr><td class=\"label\">Constitution of Business</td><td class=\"value\">" + displayConstitution + "</td></tr>\n" +
                "    <tr><td class=\"label\">Address of Principal Place of Business</td><td class=\"value\">" + displayAddress + "</td></tr>\n" +
                "  </table>\n" +
                "\n" +
                "  <div class=\"space\"></div>\n" +
                "\n" +
                "  <div class=\"section\">APPROVING AUTHORITY</div>\n" +
                "\n" +
                "  <table>\n" +
                "    <tr><td class=\"label\">Authority</td><td class=\"value\">Central Board of Indirect Taxes and Customs</td></tr>\n" +
                "    <tr><td class=\"label\">Central Jurisdiction</td><td class=\"value\">" + displayJurisdiction + "</td></tr>\n" +
                "    <tr><td class=\"label\">Central Jurisdiction Code</td><td class=\"value\">" + displayJurisdictionCode + "</td></tr>\n" +
                "  </table>\n" +
                "\n" +
                "  <div class=\"space\"></div>\n" +
                "\n" +
                "  <div class=\"section\">CERTIFICATION</div>\n" +
                "\n" +
                "  <div class=\"note\">\n" +
                "    <p>This is to certify that the above-mentioned taxpayer is registered under the Goods and Services Tax Act, 2017.</p>\n" +
                "    <p>The GSTIN is valid and the registration status is as on the date of generation of this certificate.</p>\n" +
                "    <p><span class=\"bold\">Note:</span> This certificate is computer-generated and does not require a physical signature.</p>\n" +
                "  </div>\n" +
                "\n" +
                "  <div style=\"font-size:8pt; color:#888; text-align:right; padding:6px 10px 2px; border-top:1px solid #ddd; margin-top:8px;\">Generated by DukaanLocker on " + printDate + "</div>\n" +
                "</div></body></html>";
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
