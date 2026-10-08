package com.shoplocker.fssai.util;

/**
 * Generates the FSSAI Food License / Certificate of Registration XHTML from the
 * shared {@link CertificateTemplate} layout, so it is rendered in exactly the
 * same format as the GST and MSME certificates.
 *
 * @see CertificateTemplate
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
        return generateCertificateHtml(licenseNumber, companyName, contactPerson, kindOfBusiness, licenseCategory,
                status, licenseActive, state, district, taluk, village, address, pincode, contactEmail, panNo,
                expiryDate, fboId, refId, false);
    }

    /**
     * As above, with a {@code compact} flag for data-heavy FBOs: same design, but the
     * ceremonial top gap and every box's padding tighten so the certificate still lands
     * on a single page. Applied by the caller only after a normal render spilled over.
     */
    public static String generateCertificateHtml(String licenseNumber, String companyName, String contactPerson,
                                                  String kindOfBusiness, String licenseCategory, String status,
                                                  Boolean licenseActive, String state, String district, String taluk,
                                                  String village, String address, String pincode, String contactEmail,
                                                  String panNo, String expiryDate, String fboId, String refId,
                                                  boolean compact) {
        boolean active = licenseActive == null || licenseActive;
        boolean hasExpiry = expiryDate != null && !expiryDate.trim().isEmpty();

        StringBuilder premisesRows = new StringBuilder();
        premisesRows.append(CertificateTemplate.row("Address of Premises", address));
        if (isPresent(village)) {
            premisesRows.append(CertificateTemplate.row("Village", village));
        }
        if (isPresent(taluk)) {
            premisesRows.append(CertificateTemplate.row("Taluk", taluk));
        }
        premisesRows.append(CertificateTemplate.row("District", district));
        premisesRows.append(CertificateTemplate.row("State / UT", state));
        premisesRows.append(CertificateTemplate.row("Pincode", pincode));

        StringBuilder fboRows = new StringBuilder();
        fboRows.append(CertificateTemplate.row("Name of Food Business Operator", companyName));
        if (isPresent(contactPerson)) {
            fboRows.append(CertificateTemplate.row("Contact Person", contactPerson));
        }
        if (isPresent(contactEmail)) {
            fboRows.append(CertificateTemplate.row("Contact Email", contactEmail));
        }
        if (isPresent(panNo)) {
            fboRows.append(CertificateTemplate.row("PAN No.", panNo));
        }
        if (isPresent(kindOfBusiness)) {
            fboRows.append(CertificateTemplate.row("Kind of Business (KOB Name)", kindOfBusiness));
        }

        StringBuilder licenceRows = new StringBuilder();
        licenceRows.append(CertificateTemplate.row("License Category", licenseCategory));
        licenceRows.append(CertificateTemplate.rowHtml("Status", CertificateTemplate.badge(status, active)));
        // Expiry date is mandatory - show even if not available
        licenceRows.append(CertificateTemplate.row("Validity / Expiry Date", hasExpiry ? expiryDate : "Not Available"));
        if (isPresent(fboId)) {
            licenceRows.append(CertificateTemplate.row("FBO ID", fboId));
        }
        if (isPresent(refId)) {
            licenceRows.append(CertificateTemplate.row("Reference ID", refId));
        }
        if (isPresent(kindOfBusiness)) {
            licenceRows.append(CertificateTemplate.row("Kind of Business (KOB)", kindOfBusiness));
        }

        String body = CertificateTemplate.header("Food Safety and Standards Authority of India", "FSSAI")
                + CertificateTemplate.title("Certificate of Registration")
                + CertificateTemplate.idBlock("FSSAI License / Registration Number", licenseNumber)
                + CertificateTemplate.contentStart()
                + CertificateTemplate.sectionTable("Food Business Operator", fboRows)
                + CertificateTemplate.sectionTable("Address of Premises", premisesRows)
                + CertificateTemplate.sectionTable("License Particulars", licenceRows)
                + CertificateTemplate.certBox(
                        CertificateTemplate.certParagraph("This is to certify that the above-named Food Business "
                                + "Operator is registered under the provisions of the Food Safety and Standards "
                                + "Act, 2006 and the regulations made thereunder. The license status and validity "
                                + "reflected herein are as on the date of generation of this certificate.")
                        + CertificateTemplate.certNote("This is a computer-generated certificate and does not "
                                + "require a physical signature."))
                + CertificateTemplate.contentEnd()
                + CertificateTemplate.footer("FSSAI:" + licenseNumber);

        return CertificateTemplate.document(body, compact);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
