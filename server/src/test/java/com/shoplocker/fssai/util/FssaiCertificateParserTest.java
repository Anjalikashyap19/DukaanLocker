package com.shoplocker.fssai.util;

import com.shoplocker.fssai.dto.FssaiCertificateData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FssaiCertificateParser")
class FssaiCertificateParserTest {

    private static final String FULL_CERTIFICATE =
            "1. Name & Registered Office address of Licensee: GREEN STEPS AGRO FARMS PRIVATE LIMITED "
                    + "(PURE O NATURAL) Plot Nos. 40 to 43, Industrial Area, Hyderabad, Telangana "
                    + "2. Address of Authorized Premises: Plot Nos. 40 to 43, Industrial Area, Hyderabad "
                    + "Telangana-500081 3. "
                    + "3. Kind of Business: Trade/Retail - Retailer "
                    + "5. Category of License: State License This license is granted subject to the conditions. "
                    + "Issued On: 24-11-2026 Valid Upto: 24-11-2028 "
                    + "Person in charge of operations Name: K MALLIKARJUNA PRASAD "
                    + "Qualification: Degree Mobile No: 9959055143 "
                    + "Email-ID: srinucherukuri73@gmail.com Address : Plot Nos. 40 to 43 "
                    + "State: Telangana District: Hyderabad Pin Code: 500081";

    @Test
    @DisplayName("extracts every field from a full certificate")
    void parsesFullCertificate() {
        FssaiCertificateData data = FssaiCertificateParser.parse(FULL_CERTIFICATE, "13621013001877.pdf");

        assertThat(data.getLicenseNumber()).isEqualTo("13621013001877");
        assertThat(data.getShopName()).isEqualTo("GREEN STEPS AGRO FARMS PRIVATE LIMITED (PURE O NATURAL)");
        assertThat(data.getKindOfBusiness()).isEqualTo("Trade/Retail - Retailer");
        assertThat(data.getLicenseCategory()).isEqualTo("State License");
        assertThat(data.getIssuedOn()).isEqualTo("24-11-2026");
        assertThat(data.getValidUpto()).isEqualTo("24-11-2028");
        assertThat(data.getOwnerName()).isEqualTo("K MALLIKARJUNA PRASAD");
        assertThat(data.getMobileNumber()).isEqualTo("9959055143");
        assertThat(data.getEmailId()).isEqualTo("srinucherukuri73@gmail.com");
        assertThat(data.getState()).isEqualTo("Telangana");
        assertThat(data.getDistrict()).isEqualTo("Hyderabad");
        assertThat(data.getPincode()).isEqualTo("500081");
        assertThat(data.getPremisesAddress()).contains("Telangana-500081");
        assertThat(data.hasOwnerDetails()).isTrue();
    }

    @Test
    @DisplayName("reads the business name when the label and value are contiguous")
    void businessNameWithProprietor() {
        String certificate =
                "Name & Registered Office address of Licensee: M/S.SRI SARADA AGENCIES, "
                        + "PROP:D.VENKATA JAGANNADHAM NORTHERN SIDE OF SHAR BYPASS ROAD, "
                        + "HSC.NO.16444, SULLURPET, Andhra Pradesh-524121 "
                        + "2. Address of Authorized Premises: NORTHERN SIDE OF SHAR BYPASS ROAD "
                        + "3. Kind of Business: Trade/Retail - Distributor";

        assertThat(FssaiCertificateParser.parse(certificate, "10118009000370.pdf").getShopName())
                .isEqualTo("SRI SARADA AGENCIES");
    }

    @Test
    @DisplayName("rejoins a column-interleaved label before reading the business name")
    void businessNameFromInterleavedLabel() {
        String certificate =
                "Name & Registered Office address of SHREEJI PRODUCTS PRO: RAJ TRALSAWALA "
                        + "Licensee: PLOT NO. C-4/5, UDHYOG NAGAR, BHARUCH, Gujarat-392002 "
                        + "2. Address of Authorized Premises: PLOT NO. C-4/5 "
                        + "3. Kind of Business: Manufacturer - Substances Added to Food";

        assertThat(FssaiCertificateParser.parse(certificate, "10721007000398.pdf").getShopName())
                .isEqualTo("SHREEJI PRODUCTS");
    }

    @Test
    @DisplayName("cuts the business name before proprietor and address text")
    void businessNameBeforeAddress() {
        assertBusinessName(
                "Name & Registered Office address of Licensee: NEW RAIPUR BHAJIYA HOUSE / Proprieter "
                        + "LODHA DIPAKKUMAR LALSINGBHAI 22/A/2 GHASWALA SHOP ,NR, SARANGPUR GATE , "
                        + "Ahmedabad, Gujarat-380002 2. Address of Authorized Premises: NR GATE",
                "NEW RAIPUR BHAJIYA HOUSE");
        assertBusinessName(
                "Name & Registered Office address of Licensee: TANDOOR KINGS RESTAURANT & CATERING "
                        + "Opp. McDonald's, G.T. Road, Dasuya, Punjab-144205 2. "
                        + "Address of Authorized Premises: G.T. Road",
                "TANDOOR KINGS RESTAURANT & CATERING");
        assertBusinessName(
                "Name & Registered Office address of Licensee: A J EGG CENTER No.6, 4th Main Road, "
                        + "D.B. Street, Bangalore, Karnataka-560003 2. Address of Authorized Premises: Main Road",
                "A J EGG CENTER");
        assertBusinessName(
                "Name & Registered Office address of Licensee: SERENDIPITY FLAVOURS HOSPITALITY LLP "
                        + "SY NO-75 AND SY NO-172/1, NS PALYA, BANGALORE, Karnataka-560076 2. "
                        + "Address of Authorized Premises: NS PALYA",
                "SERENDIPITY FLAVOURS HOSPITALITY LLP");
        assertBusinessName(
                "Name & Registered Office address of Licensee: M/S T.T. ENTERPRISES, THUPTEN TSERING "
                        + "RAMA CAMP, DIRANG, Arunachal Pradesh-790101 2. "
                        + "Address of Authorized Premises: DIRANG",
                "T.T. ENTERPRISES");
        assertBusinessName(
                "Name & Registered Office address of Licensee: SHUBH KAMNA AGENCIES CHAK PURE MIYAN, "
                        + "Prayagraj, Uttar Pradesh-212402 2. Address of Authorized Premises: MIYAN",
                "SHUBH KAMNA AGENCIES");
    }

    private static void assertBusinessName(String certificate, String expected) {
        assertThat(FssaiCertificateParser.parse(certificate, "x.pdf").getShopName()).isEqualTo(expected);
    }

    @Test
    @DisplayName("falls back to the file name for the licence number")
    void licenceNumberFromFileName() {
        String text = FULL_CERTIFICATE.replace("Issued On:", "Issued On:")
                .replaceAll("License Number[^P]*", "");
        // strip any embedded licence number by removing the leading digits token
        String noLicence = FULL_CERTIFICATE.replaceAll("\\b13621013001877\\b", "");
        FssaiCertificateData data = FssaiCertificateParser.parse(noLicence, "13621013001877.pdf");
        assertThat(data.getLicenseNumber()).isEqualTo("13621013001877");
    }

    @Test
    @DisplayName("skips when the owner block is absent")
    void skipsWithoutOwnerDetails() {
        String noOwner = FULL_CERTIFICATE.replaceAll("Person in charge of operations.*", "");
        FssaiCertificateData data = FssaiCertificateParser.parse(noOwner, "no-owner.pdf");
        assertThat(data.hasOwnerDetails()).isFalse();
        assertThat(data.getOwnerName()).isNull();
    }

    @Test
    @DisplayName("skips when owner fields are masked")
    void skipsMaskedOwner() {
        String masked = FULL_CERTIFICATE
                .replace("K MALLIKARJUNA PRASAD", "N/A")
                .replace("9959055143", "0000000000")
                .replace("srinucherukuri73@gmail.com", "-");
        FssaiCertificateData data = FssaiCertificateParser.parse(masked, "masked.pdf");
        assertThat(data.hasOwnerDetails()).isFalse();
    }

    @Test
    @DisplayName("never throws on null or empty input")
    void neverThrowsOnEmpty() {
        FssaiCertificateData empty = FssaiCertificateParser.parse("", "x.pdf");
        assertThat(empty).isNotNull();
        assertThat(empty.hasOwnerDetails()).isFalse();

        FssaiCertificateData nil = FssaiCertificateParser.parse(null, null);
        assertThat(nil).isNotNull();
        assertThat(nil.getLicenseNumber()).isNull();
    }

    @Test
    @DisplayName("detectState finds a state name in an address")
    void detectState() {
        assertThat(FssaiCertificateParser.detectState("Plot 4, Hyderabad Telangana-500081"))
                .isEqualTo("Telangana");
        assertThat(FssaiCertificateParser.detectState("no state here")).isNull();
        assertThat(FssaiCertificateParser.detectState(null)).isNull();
    }
}
