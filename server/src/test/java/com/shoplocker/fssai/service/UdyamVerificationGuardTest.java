package com.shoplocker.fssai.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the Udyam verification guards that stop a failed CAPTCHA
 * from being treated as a successful verification.
 *
 * <p>The portal reports a wrong CAPTCHA as
 * {@code <span id="ctl00_ContentPlaceHolder1_lblCaptcha" class="msgRed">Incorrect
 * verification code. Please try again.</span>}. That phrase was not matched by the
 * old literal checks, so the flow fell back to the print URL, fetched the portal
 * landing page (HTTP 200), parsed nothing, and created an "MSME Owner" account whose
 * shop was named after the Udyam number with a certificate full of placeholders.</p>
 */
public class UdyamVerificationGuardTest {

    /** Real shape of the verify delta response when the CAPTCHA was wrong. */
    private static final String WRONG_CAPTCHA_RESPONSE =
            "1|#||4|7948|updatePanel|ctl00_ContentPlaceHolder1_UpdatePaneldd1|\n"
            + "<div class=\"captcha-image\">\n"
            + "  <span id=\"ctl00_ContentPlaceHolder1_lblCaptcha\" class=\"msgRed\">"
            + "Incorrect verification code. Please try again.</span>\n"
            + "</div>\n"
            + "|0|hiddenField|__LASTFOCUS||0|hiddenField|__EVENTTARGET|";

    /** Verify delta when verification succeeded - it redirects to the print page. */
    private static final String SUCCESS_RESPONSE =
            "1|#||4|29|pageRedirect||%2fPrintUdyamApplication.aspx|\n"
            + "<span id=\"ctl00_ContentPlaceHolder1_lblCaptcha\" class=\"msgGreen\"></span>";

    /** Shape of the portal landing/home page served when verification never happened. */
    private static final String HOMEPAGE_HTML =
            "<!DOCTYPE html><html><head><title>Udyam Registration : Zero cost, No Fee and "
            + "Free Registration of MSMEs</title></head><body>\n"
            + "<p>MSME Units are requested to onboard on TReDS Platform</p>\n"
            + "<p>Useful Documents Important</p>\n"
            + "<p>Office of DCMSME, Ministry of MSME, Kartavya Bhawan - New Delhi</p>\n"
            + "<p>Visitor Count 6213076 Get in Touch</p>\n"
            + "<p>champions@gov.in</p>\n"
            + "</body></html>";

    /** The real certificate print page carries these markers. */
    private static final String CERTIFICATE_HTML =
            "<!DOCTYPE html><html><body>\n"
            + "<h2>Udyam Registration Certificate</h2>\n"
            + "<table><tr><td>Udyam Registration Number</td><td>UDYAM-UP-50-0042056</td></tr>\n"
            + "<tr><td>Name of Enterprise</td><td>ACME TRADERS</td></tr></table>\n"
            + "</body></html>";

    private UdyamVerificationService newService() {
        return new UdyamVerificationService(null);
    }

    private boolean invokeBoolean(UdyamVerificationService service, String method, Class<?> argType, Object arg)
            throws Exception {
        Method m = UdyamVerificationService.class.getDeclaredMethod(method, argType);
        m.setAccessible(true);
        return (boolean) m.invoke(service, arg);
    }

    @Test
    public void detectsWrongCaptchaFromPortalMessage() throws Exception {
        UdyamVerificationService service = newService();
        assertTrue(invokeBoolean(service, "isCaptchaError", String.class, WRONG_CAPTCHA_RESPONSE),
                "The portal's 'Incorrect verification code' message must be detected as a CAPTCHA failure");
    }

    @Test
    public void doesNotFlagSuccessfulVerificationAsCaptchaError() throws Exception {
        UdyamVerificationService service = newService();
        assertFalse(invokeBoolean(service, "isCaptchaError", String.class, SUCCESS_RESPONSE),
                "A successful verification (pageRedirect, empty lblCaptcha) must not be flagged");
    }

    @Test
    public void landingPageIsNotTreatedAsACertificate() throws Exception {
        UdyamVerificationService service = newService();
        assertFalse(invokeBoolean(service, "isCertificatePage", String.class, HOMEPAGE_HTML),
                "The portal landing page must not pass the certificate guard");
    }

    @Test
    public void realCertificatePassesTheGuard() throws Exception {
        UdyamVerificationService service = newService();
        assertTrue(invokeBoolean(service, "isCertificatePage", String.class, CERTIFICATE_HTML),
                "A real certificate print page must pass the certificate guard");
    }

    @Test
    public void guardRejectsNullAndBlankHtml() throws Exception {
        UdyamVerificationService service = newService();
        assertFalse(invokeBoolean(service, "isCertificatePage", String.class, null));
        assertFalse(invokeBoolean(service, "isCertificatePage", String.class, "   "));
        assertNotNull(service);
    }
}
