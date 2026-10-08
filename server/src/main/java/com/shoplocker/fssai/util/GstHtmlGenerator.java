package com.shoplocker.fssai.util;

/**
 * Generates the GST Registration Certificate XHTML from the shared
 * {@link CertificateTemplate} layout, so it is rendered in exactly the same
 * format as the FSSAI and MSME certificates.
 *
 * @see CertificateTemplate
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
     * @param stateJurisdictionCode State jurisdiction code
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
                                                  String stateJurisdictionCode,
                                                  String constitutionOfBusiness, String principalPlaceAddress,
                                                  String centralJurisdiction, String centralJurisdictionCode,
                                                  String dateOfIssue, String periodOfValidity,
                                                  String typeOfRegistration) {
        return generateCertificateHtml(gstin, legalName, tradeName, registrationDate, status, state,
                stateJurisdictionCode, constitutionOfBusiness, principalPlaceAddress, centralJurisdiction,
                centralJurisdictionCode, dateOfIssue, periodOfValidity, typeOfRegistration, false);
    }

    /**
     * As above, with a {@code compact} flag for data-heavy taxpayers (a long
     * principal-place address, every optional row): same design, denser, so the
     * certificate still lands on a single page. Applied by the caller only after a
     * normal render spilled over.
     */
    public static String generateCertificateHtml(String gstin, String legalName, String tradeName,
                                                  String registrationDate, String status, String state,
                                                  String stateJurisdictionCode,
                                                  String constitutionOfBusiness, String principalPlaceAddress,
                                                  String centralJurisdiction, String centralJurisdictionCode,
                                                  String dateOfIssue, String periodOfValidity,
                                                  String typeOfRegistration, boolean compact) {
        boolean active = "Active".equalsIgnoreCase(status);

        StringBuilder taxpayerRows = new StringBuilder();
        taxpayerRows.append(CertificateTemplate.row("Legal Name", legalName));
        taxpayerRows.append(CertificateTemplate.row("Trade Name", tradeName));
        taxpayerRows.append(CertificateTemplate.row("Date of Registration", registrationDate));
        taxpayerRows.append(CertificateTemplate.rowHtml("Status", CertificateTemplate.badge(status, active)));
        taxpayerRows.append(CertificateTemplate.row("State / UT", state));
        taxpayerRows.append(CertificateTemplate.row("State Jurisdiction Code", stateJurisdictionCode));

        StringBuilder particularsRows = new StringBuilder();
        particularsRows.append(CertificateTemplate.row("Type of Registration", typeOfRegistration));
        particularsRows.append(CertificateTemplate.row("Period of Validity", periodOfValidity));
        particularsRows.append(CertificateTemplate.row("Date of Issue", dateOfIssue));

        StringBuilder placeRows = new StringBuilder();
        placeRows.append(CertificateTemplate.row("Constitution of Business", constitutionOfBusiness));
        placeRows.append(CertificateTemplate.row("Address", principalPlaceAddress));

        StringBuilder jurisdictionRows = new StringBuilder();
        jurisdictionRows.append(CertificateTemplate.row("Approving Authority",
                "Central Board of Indirect Taxes and Customs"));
        jurisdictionRows.append(CertificateTemplate.row("State Jurisdiction",
                composite(state, stateJurisdictionCode)));
        jurisdictionRows.append(CertificateTemplate.row("Central Jurisdiction",
                composite(centralJurisdiction, centralJurisdictionCode)));

        String body = CertificateTemplate.header("Central Board of Indirect Taxes and Customs", "GST")
                + CertificateTemplate.title("Certificate of Registration under GST")
                + CertificateTemplate.idBlock("GST Identification Number (GSTIN)", gstin)
                + CertificateTemplate.contentStart()
                + CertificateTemplate.sectionTable("Taxpayer Information", taxpayerRows)
                + CertificateTemplate.sectionTable("Registration Particulars", particularsRows)
                + CertificateTemplate.sectionTable("Principal Place of Business", placeRows)
                + CertificateTemplate.sectionTable("Jurisdictional Authority", jurisdictionRows)
                + CertificateTemplate.certBox(
                        CertificateTemplate.certParagraph("This is to certify that the above-named taxpayer is "
                                + "registered under the provisions of the Goods and Services Tax Act, 2017. The "
                                + "GSTIN is valid and the registration status reflected herein is as on the date "
                                + "of generation of this certificate.")
                        + CertificateTemplate.certNote("This is a computer-generated certificate and does not "
                                + "require a physical signature."))
                + CertificateTemplate.contentEnd()
                + CertificateTemplate.footer("GSTIN:" + gstin);

        return CertificateTemplate.document(body, compact);
    }

    /** "Karnataka (27)" - drops the parentheses when the code is absent. */
    private static String composite(String name, String code) {
        boolean hasName = name != null && !name.trim().isEmpty() && !"-".equals(name.trim());
        boolean hasCode = code != null && !code.trim().isEmpty() && !"-".equals(code.trim());
        if (hasName && hasCode) return name.trim() + " (" + code.trim() + ")";
        if (hasName) return name.trim();
        if (hasCode) return code.trim();
        return "-";
    }
}
