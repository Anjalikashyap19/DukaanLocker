package com.shoplocker.fssai.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Regression test for the Udyam (MSME) certificate PDF generation path.
 *
 * Exercises the REAL private UdyamVerificationService.convertHtmlToPdf()
 * (via reflection) against a realistic Udyam print-page snapshot. The
 * snapshot contains the constructs that used to make the portal HTML
 * fail OpenHTMLtoPDF's strict XML parser (unclosed &lt;img&gt;, &lt;input&gt;,
 * &lt;br&gt;, relative image URLs) -> HTTP 500 "couldn't generate the PDF".
 */
public class UdyamPdfReproTest {

    private String buildRealisticPrintHtml() {
        return "<!DOCTYPE html>\n"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n"
            + "<head><title>Udyam Registration Certificate</title>\n"
            + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/>\n"
            + "<link rel=\"stylesheet\" href=\"style.css\"/>\n"
            + "<script src=\"jquery.js\"></script>\n"
            + "</head>\n"
            + "<body>\n"
            + "<form id=\"form1\" runat=\"server\">\n"
            + "<div class=\"main\" style=\"font-family: sans-serif;\">\n"
            + "  <img src=\"Images/udyam_logo.png\" width=\"120\">\n"
            + "  <h3>GOVERNMENT OF INDIA</h3>\n"
            + "  <h4>MINISTRY OF MICRO, SMALL AND MEDIUM ENTERPRISES</h4>\n"
            + "  <br>\n"
            + "  <div class=\"cert-title\">Udyam Registration Certificate</div>\n"
            + "  <table border=\"1\">\n"
            + "    <tr><td>Udyam Registration Number</td><td>UDYAM-MH-00-0000000</td></tr>\n"
            + "    <tr><td>Name of Enterprise</td><td>Test Enterprises Pvt Ltd</td></tr>\n"
            + "    <tr><td>Type of Enterprise</td><td>Micro</td></tr>\n"
            + "  </table>\n"
            + "  <input type=\"hidden\" name=\"__VIEWSTATE\" id=\"__VIEWSTATE\" value=\"abc\">\n"
            + "  <p>\u0938\u0941\u0930\u0915\u094d\u0937\u093e \u0938\u0947\u0935\u093e</p>\n"
            + "</div>\n"
            + "</form>\n"
            + "</body>\n"
            + "</html>";
    }

    @Test
    public void convertRealUdyamPrintHtmlToPdf() throws Exception {
        UdyamVerificationService service = new UdyamVerificationService(null);
        Method m = UdyamVerificationService.class.getDeclaredMethod(
                "convertHtmlToPdf", String.class, String.class);
        m.setAccessible(true);

        try {
            byte[] pdf = (byte[]) m.invoke(service, buildRealisticPrintHtml(), "UDYAM-MH-00-0000000");
            assertTrue(pdf.length > 100,
                    "Expected a non-trivial PDF, got " + pdf.length + " bytes");
            System.out.println("SUCCESS: generated " + pdf.length + " bytes");
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            cause.printStackTrace();
            fail("UdyamVerificationService.convertHtmlToPdf threw: "
                    + cause.getClass().getName() + ": " + cause.getMessage());
        }
    }
}
