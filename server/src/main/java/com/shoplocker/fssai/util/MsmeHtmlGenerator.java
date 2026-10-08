package com.shoplocker.fssai.util;

import com.shoplocker.fssai.dto.MsmeParsedData;

/**
 * Generates the MSME (Udyam) Registration Certificate XHTML from the shared
 * {@link CertificateTemplate} layout, so it is rendered in exactly the same
 * format as the FSSAI and GST certificates.
 *
 * @see CertificateTemplate
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
        return generateCertificateHtml(data, false);
    }

    /**
     * As above, with a {@code compact} flag for data-heavy enterprises (a long
     * premises address, every optional row): same design, denser, so the
     * certificate still lands on a single page. Applied by the caller only after
     * a normal render spilled over.
     */
    public static String generateCertificateHtml(MsmeParsedData data, boolean compact) {
        StringBuilder enterpriseRows = new StringBuilder();
        enterpriseRows.append(CertificateTemplate.row("Name of Enterprise", data.getEnterpriseName()));
        enterpriseRows.append(CertificateTemplate.row("Entrepreneur Name", data.getEntrepreneurName()));
        enterpriseRows.append(CertificateTemplate.row("Date of Registration", data.getDateOfRegistration()));
        enterpriseRows.append(CertificateTemplate.row("Enterprise Type", data.getEnterpriseType()));
        enterpriseRows.append(CertificateTemplate.row("Major Activity", data.getMajorActivity()));
        enterpriseRows.append(CertificateTemplate.row("Type of Organization", data.getTypeOfOrganization()));

        StringBuilder addressRows = new StringBuilder();
        addressRows.append(CertificateTemplate.row("Address", data.getAddress()));
        addressRows.append(CertificateTemplate.row("Village / Town / City", data.getCity()));
        addressRows.append(CertificateTemplate.row("District", data.getDistrict()));
        addressRows.append(CertificateTemplate.row("State / UT", data.getState()));
        addressRows.append(CertificateTemplate.row("Pincode", data.getPincode()));

        StringBuilder contactRows = new StringBuilder();
        contactRows.append(CertificateTemplate.row("Mobile Number", data.getMobileNumber()));
        contactRows.append(CertificateTemplate.row("Email ID", data.getEmailId()));

        String body = CertificateTemplate.header("Ministry of Micro, Small & Medium Enterprises", "MSME")
                + CertificateTemplate.title("Udyam Registration Certificate")
                + CertificateTemplate.idBlock("Udyam Registration Number", data.getUdyamNumber())
                + CertificateTemplate.contentStart()
                + CertificateTemplate.sectionTable("Enterprise Details", enterpriseRows)
                + CertificateTemplate.sectionTable("Official Address of Enterprise", addressRows)
                + CertificateTemplate.sectionTable("Contact Details", contactRows)
                + CertificateTemplate.certBox(
                        CertificateTemplate.certParagraph("This is to certify that the above-named enterprise is "
                                + "registered under the provisions of the Micro, Small and Medium Enterprises "
                                + "Development Act, 2006.")
                        + CertificateTemplate.certNote("This is a computer-generated certificate and does not "
                                + "require any physical or digital signature. It is generated based on details "
                                + "furnished by the enterprise on the Udyam Registration portal."))
                + CertificateTemplate.contentEnd()
                + CertificateTemplate.footer("UDYAM:" + data.getUdyamNumber());

        return CertificateTemplate.document(body, compact);
    }
}
