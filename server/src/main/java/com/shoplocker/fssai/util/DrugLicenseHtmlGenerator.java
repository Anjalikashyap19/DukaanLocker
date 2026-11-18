package com.shoplocker.fssai.util;

/**
 * Generates the Drug License certificate XHTML from the shared {@link CertificateTemplate}
 * layout, so it is rendered in exactly the same format as the FSSAI, GST and MSME certificates.
 *
 * @see CertificateTemplate
 */
public class DrugLicenseHtmlGenerator {

    private DrugLicenseHtmlGenerator() {}

    public static String generateCertificateHtml(String licenseNumber, String licenseHolderName,
                                                  String kindOfBusiness, String issueDate,
                                                  String expiryDate, String state, String district,
                                                  String flatDoor, String premises, String contactPerson,
                                                  String registrationNumber) {
        return generateCertificateHtml(licenseNumber, licenseHolderName, kindOfBusiness, issueDate,
                expiryDate, state, district, flatDoor, premises, contactPerson, registrationNumber, false);
    }

    public static String generateCertificateHtml(String licenseNumber, String licenseHolderName,
                                                  String kindOfBusiness, String issueDate,
                                                  String expiryDate, String state, String district,
                                                  String flatDoor, String premises, String contactPerson,
                                                  String registrationNumber, boolean compact) {
        boolean hasExpiry = expiryDate != null && !expiryDate.trim().isEmpty();

        StringBuilder premisesRows = new StringBuilder();
        if (isPresent(flatDoor)) {
            premisesRows.append(CertificateTemplate.row("Flat / Door No.", flatDoor));
        }
        if (isPresent(premises)) {
            premisesRows.append(CertificateTemplate.row("Premises", premises));
        }
        premisesRows.append(CertificateTemplate.row("District", district));
        premisesRows.append(CertificateTemplate.row("State / UT", state));

        StringBuilder holderRows = new StringBuilder();
        holderRows.append(CertificateTemplate.row("License Holder", licenseHolderName));
        if (isPresent(contactPerson)) {
            holderRows.append(CertificateTemplate.row("Contact Person", contactPerson));
        }

        StringBuilder licenceRows = new StringBuilder();
        licenceRows.append(CertificateTemplate.row("License Number", licenseNumber));
        if (isPresent(registrationNumber)) {
            licenceRows.append(CertificateTemplate.row("Registration Number", registrationNumber));
        }
        if (isPresent(kindOfBusiness)) {
            licenceRows.append(CertificateTemplate.row("Kind of Business", kindOfBusiness));
        }
        if (isPresent(issueDate)) {
            licenceRows.append(CertificateTemplate.row("Issue Date", issueDate));
        }
        licenceRows.append(CertificateTemplate.row("Validity / Expiry Date", hasExpiry ? expiryDate : "Not Available"));

        String body = CertificateTemplate.header("Drug Control Administration", "DCA")
                + CertificateTemplate.title("Drug License Certificate")
                + CertificateTemplate.idBlock("Drug License Number", licenseNumber)
                + CertificateTemplate.contentStart()
                + CertificateTemplate.sectionTable("License Holder Details", holderRows)
                + CertificateTemplate.sectionTable("Premises Details", premisesRows)
                + CertificateTemplate.sectionTable("License Particulars", licenceRows)
                + CertificateTemplate.certBox(
                        CertificateTemplate.certParagraph("This is to certify that the above-named license holder "
                                + "is authorized to deal in drugs under the provisions of the Drugs and Cosmetics "
                                + "Act, 1940 and the rules made thereunder. The license status and validity "
                                + "reflected herein are as on the date of generation of this certificate.")
                        + CertificateTemplate.certNote("This is a computer-generated certificate and does not "
                                + "require a physical signature."))
                + CertificateTemplate.contentEnd()
                + CertificateTemplate.footer("DRUG:" + licenseNumber);

        return CertificateTemplate.document(body, compact);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
