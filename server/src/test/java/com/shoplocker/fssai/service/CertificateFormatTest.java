package com.shoplocker.fssai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.shoplocker.fssai.dto.GstVerificationResponse;
import com.shoplocker.fssai.dto.MsmeParsedData;
import com.shoplocker.fssai.util.FssaiHtmlGenerator;
import com.shoplocker.fssai.util.GstHtmlGenerator;
import com.shoplocker.fssai.util.MsmeHtmlGenerator;
import com.shoplocker.fssai.util.QrCodePng;

/**
 * The FSSAI, GST and MSME certificates must all come out of the shared
 * {@link com.shoplocker.fssai.util.CertificateTemplate} stylesheet: identical
 * header, footer (QR + logo + tick), divider and sizing on every cert type,
 * and every render must land on a single A4 page.
 */
class CertificateFormatTest {

    // --- helpers ------------------------------------------------------------

    private static String styleOf(String html) {
        int from = html.indexOf("<style>");
        int to = html.indexOf("</style>");
        assertTrue(from >= 0 && to > from, "certificate must carry its stylesheet inline");
        return html.substring(from, to);
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int from = haystack.indexOf(needle); from >= 0; from = haystack.indexOf(needle, from + needle.length())) {
            count++;
        }
        return count;
    }

    private static String fssaiHtml() {
        return FssaiHtmlGenerator.generateCertificateHtml(
                "21221160000115", "SHYAM SUNDAR", "SHYAM",
                "Proprietorship", "Basic", "Active",
                true, "Karnataka", "Mysuru",
                "Mysuru", "Mysuru", "12, JLB Road, Mysuru",
                "570001", "info@example.com", "ABCDE1234F",
                "10-09-2028", "FBO123", "REF123");
    }

    private static GstVerificationResponse gstData() {
        return GstVerificationResponse.ok(
                "27AAACB1234F1Z5",
                "CANARA JUICE JUNCTION PRIVATE LIMITED",
                "CANARA JUICE JUNCTION",
                "01-04-2018",
                "ACTIVE",
                "Maharashtra",
                "27AAACB1234F1",
                "Private Limited Company",
                "Shop No 4, Ground Floor, Veer Savarkar Marg, Dadar West, Mumbai, Maharashtra 400028, India",
                "Mumbai South",
                "2700",
                "01-04-2018",
                "31-03-2025",
                "Regular",
                null,
                null);
    }

    private static MsmeParsedData msmeData() {
        MsmeParsedData data = new MsmeParsedData();
        data.setUdyamNumber("UDYAM-MH-00-0000123");
        data.setEnterpriseName("Canara Juice Junction");
        data.setEntrepreneurName("Shyam Sundar");
        data.setMobileNumber("9876543210");
        data.setEmailId("info@example.com");
        data.setAddress("Shop No 4, Ground Floor, Veer Savarkar Marg, Dadar West, Mumbai, Maharashtra 400028, India");
        data.setCity("Mumbai");
        data.setState("Maharashtra");
        data.setDistrict("Mumbai");
        data.setPincode("400028");
        data.setMajorActivity("Manufacturing");
        data.setEnterpriseType("Micro");
        data.setTypeOfOrganization("Proprietorship");
        data.setDateOfRegistration("01-04-2018");
        return data;
    }

    // --- format parity ------------------------------------------------------

    @Test
    void allThreeCertificatesShareOneStylesheet() {
        String fssai = styleOf(fssaiHtml());
        String gst = styleOf(GstHtmlGenerator.generateCertificateHtml(
                "27AAACB1234F1Z5", "CANARA JUICE JUNCTION PRIVATE LIMITED", "CANARA JUICE JUNCTION",
                "01-04-2018", "ACTIVE", "Maharashtra", "27AAACB1234F1",
                "Private Limited Company", "Shop No 4, Dadar West, Mumbai 400028",
                "Mumbai South", "2700", "01-04-2018", "31-03-2025", "Regular"));
        String msme = styleOf(MsmeHtmlGenerator.generateCertificateHtml(msmeData()));

        assertEquals(fssai, gst, "GST certificate must reuse the exact FSSAI stylesheet");
        assertEquals(fssai, msme, "MSME certificate must reuse the exact FSSAI stylesheet");
    }

    @Test
    void gstCertificateFollowsTheSharedFormat() throws Exception {
        String html = GstHtmlGenerator.generateCertificateHtml(
                "27AAACB1234F1Z5", "CANARA JUICE JUNCTION PRIVATE LIMITED", "CANARA JUICE JUNCTION",
                "01-04-2018", "ACTIVE", "Maharashtra", "27AAACB1234F1",
                "Private Limited Company", "Shop No 4, Dadar West, Mumbai 400028",
                "Mumbai South", "2700", "01-04-2018", "31-03-2025", "Regular");

        assertTrue(html.contains("Central Board of Indirect Taxes and Customs"), "GST header line");
        assertTrue(html.contains("GST Identification Number (GSTIN)"), "id block");
        assertFalse(html.contains("border-radius:10px"), "no rounded page border");

        // footer: QR + logo + tick, divider between logo and signature block
        assertEquals(3, occurrences(html, "data:image/png;base64,"), "QR + logo + tick");
        assertTrue(html.contains(QrCodePng.pngDataUri("GSTIN:27AAACB1234F1Z5")), "QR encodes the GSTIN");
        assertTrue(html.contains("Digitally signed and verified by"), "signature line");
        assertTrue(html.contains("class=\"divider\""), "footer rule");

        // shared sizing from the visual review
        assertTrue(html.contains(".header-dept { font-size:15pt"), "header font size");
        assertTrue(html.contains(".f-qr .qr { width:32mm"), "QR size");
    }

    @Test
    void msmeCertificateFollowsTheSharedFormat() throws Exception {
        String html = MsmeHtmlGenerator.generateCertificateHtml(msmeData());

        assertTrue(html.contains("Ministry of Micro, Small &amp; Medium Enterprises"), "MSME header line");
        assertTrue(html.contains("Udyam Registration Number"), "id block");
        assertFalse(html.contains("border-radius:10px"), "no rounded page border");

        assertEquals(3, occurrences(html, "data:image/png;base64,"), "QR + logo + tick");
        assertTrue(html.contains(QrCodePng.pngDataUri("UDYAM:UDYAM-MH-00-0000123")), "QR encodes the Udyam number");
        assertTrue(html.contains("Digitally signed and verified by"), "signature line");
        assertTrue(html.contains("class=\"divider\""), "footer rule");

        assertTrue(html.contains(".header-dept { font-size:15pt"), "header font size");
        assertTrue(html.contains(".f-qr .qr { width:32mm"), "QR size");
    }

    // --- single page --------------------------------------------------------

    @Test
    void gstCertificateRendersOnOnePage() {
        GstVerificationResponse data = gstData();
        GstVerificationService service = new GstVerificationService(null);

        byte[] pdf = service.renderCertificate(data, data.getGstin());

        assertEquals(1, service.pageCount(pdf), "GST certificate must fit one page");
        assertTrue(data.getCertificateHtml() != null && data.getCertificateHtml().contains("GST"),
                "the stored HTML is the one that produced the PDF");
    }

    @Test
    void msmeCertificateRendersOnOnePage() throws Exception {
        UdyamVerificationService service = new UdyamVerificationService(null);
        Method render = UdyamVerificationService.class.getDeclaredMethod(
                "renderCertificate", MsmeParsedData.class, String.class);
        render.setAccessible(true);

        byte[] pdf = (byte[]) render.invoke(service, msmeData(), "UDYAM-MH-00-0000123");

        org.apache.pdfbox.pdmodel.PDDocument document =
                org.apache.pdfbox.pdmodel.PDDocument.load(pdf);
        try {
            assertEquals(1, document.getNumberOfPages(), "MSME certificate must fit one page");
        } finally {
            document.close();
        }
    }
}
