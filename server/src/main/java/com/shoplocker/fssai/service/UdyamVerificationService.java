package com.shoplocker.fssai.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.shoplocker.fssai.dto.UdyamInitResponse;
import com.shoplocker.fssai.dto.UdyamVerifyRequest;
import com.shoplocker.fssai.dto.UdyamVerifyResponse;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;

/**
 * Integrates with the Indian government's Udyam registration portal
 * ({@code https://www.udyamregistration.gov.in/}) to:
 * <ol>
 *   <li>Initialise an HTTP session and download a CAPTCHA image.</li>
 *   <li>Verify a Udyam number + CAPTCHA answer against the portal.</li>
 *   <li>Fetch the HTML certificate page (PrintUdyamApplication.aspx).</li>
 *   <li>Convert that HTML to a PDF using OpenHTMLtoPDF and upload to S3.</li>
 * </ol>
 *
 * <p>Session state (cookies, ASP.NET view-state, event-validation) is kept in
 * a in-memory map keyed by a random UUID so that concurrent users don't
 * interfere with each other. Sessions are auto-expired after 5 minutes.</p>
 */
@Service
public class UdyamVerificationService {

    private static final Logger log = LoggerFactory.getLogger(UdyamVerificationService.class);

    private static final String BASE_URL = "https://www.udyamregistration.gov.in";
    private static final String VERIFY_PAGE = BASE_URL + "/Udyam_Verify.aspx";
    private static final String CAPTCHA_URL_PREFIX = BASE_URL + "/Captcha/CaptchaControl.aspx?id=";
    private static final String PRINT_PAGE = BASE_URL + "/PrintUdyamApplication.aspx";
    private static final long SESSION_TTL_MS = 5 * 60 * 1000; // 5 minutes
    private static final int MAX_SESSIONS = 1000; // prevent memory exhaustion

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36";

    private final ConcurrentHashMap<String, UdyamSession> sessions = new ConcurrentHashMap<>();
    private final S3Service s3Service;

    public UdyamVerificationService(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    @PostConstruct
    void startSessionCleaner() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60_000); // every minute
                    long now = System.currentTimeMillis();
                    sessions.entrySet().removeIf(e -> now - e.getValue().createdAt > SESSION_TTL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "udyam-session-cleaner");
        t.setDaemon(true);
        t.start();
    }

    @PreDestroy
    void shutdown() {
        sessions.clear();
    }

    // ─── PUBLIC API ────────────────────────────────────────────────────────

    /**
     * STEP 1 — Initialise a session with the Udyam portal.
     * Visits the verify page to obtain ASP.NET session cookies and
     * view-state tokens, then downloads the CAPTCHA image.
     */
    public UdyamInitResponse initSession() {
        BasicCookieStore cookieStore = new BasicCookieStore();
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(30_000))
                .setResponseTimeout(Timeout.ofMilliseconds(60_000))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(30_000))
                .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .setDefaultRequestConfig(config)
                .build()) {

            // ── STEP 1: Load verify page to get VIEWSTATE etc. ──
            HttpGet pageRequest = new HttpGet(VERIFY_PAGE);
            addBrowserHeaders(pageRequest);

            log.info("Udyam init — fetching verify page: {}", VERIFY_PAGE);
            String html = client.execute(pageRequest, response -> {
                int status = response.getCode();
                log.info("Udyam verify page HTTP status: {}", status);
                if (status != 200) {
                    throw new IOException("Failed to fetch Udyam verify page, HTTP " + status);
                }
                byte[] bytes = EntityUtils.toByteArray(response.getEntity());
                return new String(bytes, StandardCharsets.UTF_8);
            });

            String viewState = extractHiddenValue(html, "__VIEWSTATE");
            String viewStateGenerator = extractHiddenValue(html, "__VIEWSTATEGENERATOR");
            String eventValidation = extractHiddenValue(html, "__EVENTVALIDATION");

            if (viewState.isEmpty()) {
                log.warn("Udyam init — VIEWSTATE is empty; the portal may have changed its page structure.");
            }
            log.info("Udyam init — VIEWSTATE length={}, EVENTVALIDATION length={}",
                    viewState.length(), eventValidation.length());

            // ── STEP 2: Download CAPTCHA image ──
            SimpleDateFormat sdf = new SimpleDateFormat("M/d/yyyy h:mm:ss a");
            String timestamp = URLEncoder.encode(sdf.format(new Date()), StandardCharsets.UTF_8);
            String captchaUrl = CAPTCHA_URL_PREFIX + timestamp;
            log.info("Udyam init — downloading captcha: {}", captchaUrl);

            HttpGet captchaRequest = new HttpGet(captchaUrl);
            addBrowserHeaders(captchaRequest);
            captchaRequest.setHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
            captchaRequest.setHeader("Sec-Fetch-Dest", "image");
            captchaRequest.setHeader("Sec-Fetch-Mode", "no-cors");
            captchaRequest.setHeader("Sec-Fetch-Site", "same-origin");

            byte[] captchaBytes = client.execute(captchaRequest, response -> {
                int status = response.getCode();
                log.info("Udyam captcha HTTP status: {}", status);
                if (status != 200) {
                    throw new IOException("Failed to download captcha, HTTP " + status);
                }
                // Validate that the response is actually an image
                Header contentTypeHeader = response.getFirstHeader("Content-Type");
                String contentType = contentTypeHeader != null ? contentTypeHeader.getValue() : "unknown";
                log.info("Udyam captcha Content-Type: {}", contentType);
                if (!contentType.toLowerCase().contains("image/")) {
                    // The portal may have returned an HTML error/challenge page
                    log.error("Udyam captcha returned non-image content (Content-Type: {})."
                            + " The portal may be temporarily blocking automated requests.",
                            contentType);
                    throw new IOException(
                            "Government portal returned non-image content (Content-Type: " + contentType 
                            + "). The portal may be temporarily blocking automated requests."
                            + " Try again in a few minutes.");
                }
                byte[] bytes = EntityUtils.toByteArray(response.getEntity());
                log.info("Udyam captcha downloaded: {} bytes", bytes.length);
                if (bytes.length < 100) {
                    log.error("Udyam captcha suspiciously small ({} bytes) — likely not a real captcha image.", bytes.length);
                    throw new IOException("Government portal returned an invalid captcha image.");
                }
                return bytes;
            });

            String captchaBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(captchaBytes);
            log.info("Udyam init — captcha prepared (base64 length={})", captchaBase64.length());

            // ── Store session state (with size cap) ──
            if (sessions.size() >= MAX_SESSIONS) {
                throw new FssaiException(
                        "Too many active sessions. Please try again in a minute.",
                        FailureCode.TEXTRACT_FAILURE);
            }
            String sessionId = UUID.randomUUID().toString();
            UdyamSession session = new UdyamSession();
            session.createdAt = System.currentTimeMillis();
            session.viewState = viewState;
            session.viewStateGenerator = viewStateGenerator;
            session.eventValidation = eventValidation;
            session.cookies = new ArrayList<>();
            for (Cookie c : cookieStore.getCookies()) {
                session.cookies.add(c);
            }
            session.captchaImage = captchaBytes;  // store raw bytes for direct image serving
            sessions.put(sessionId, session);

            return new UdyamInitResponse(sessionId, captchaBase64);

        } catch (Exception e) {
            log.error("Failed to initialise Udyam session", e);
            throw new FssaiException(
                    "We couldn't connect to the Udyam verification portal right now. Please try again in a few minutes.",
                    FailureCode.TEXTRACT_FAILURE, e);
        }
    }

    /**
     * STEP 3 + 4 — Verify a Udyam number + CAPTCHA, then fetch and convert
     * the certificate HTML to a PDF.
     */
    public UdyamVerifyResponse verifyAndGeneratePdf(UdyamVerifyRequest request) {
        UdyamSession session = sessions.remove(request.getSessionId());
        if (session == null) {
            throw new FssaiException(
                    "Your session has expired. Please go back and load a new CAPTCHA.",
                    FailureCode.INVALID_REQUEST);
        }

        BasicCookieStore cookieStore = new BasicCookieStore();
        for (Cookie c : session.cookies) {
            cookieStore.addCookie(c);
        }

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(30_000))
                .setResponseTimeout(Timeout.ofMilliseconds(60_000))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(30_000))
                .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .setDefaultRequestConfig(config)
                .build()) {

            // ── STEP 3: Submit verification ──
            HttpPost post = new HttpPost(VERIFY_PAGE);
            addBrowserHeaders(post);
            post.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            post.setHeader("Origin", BASE_URL);
            post.setHeader("X-MicrosoftAjax", "Delta=true");
            post.setHeader("X-Requested-With", "XMLHttpRequest");

            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("ctl00$sm",
                    "ctl00$ContentPlaceHolder1$UpdatePaneldd1|ctl00$ContentPlaceHolder1$btnVerify"));
            params.add(new BasicNameValuePair("__EVENTTARGET", ""));
            params.add(new BasicNameValuePair("__EVENTARGUMENT", ""));
            params.add(new BasicNameValuePair("__VIEWSTATE", session.viewState));
            params.add(new BasicNameValuePair("__VIEWSTATEGENERATOR", session.viewStateGenerator));
            params.add(new BasicNameValuePair("__VIEWSTATEENCRYPTED", ""));
            params.add(new BasicNameValuePair("ctl00$ContentPlaceHolder1$hdnSetPassword", ""));
            params.add(new BasicNameValuePair("ctl00$ContentPlaceHolder1$txtUdyamNo", request.getUdyamNumber()));
            params.add(new BasicNameValuePair("ctl00$ContentPlaceHolder1$txtCaptcha", request.getCaptchaText()));
            params.add(new BasicNameValuePair("__ASYNCPOST", "true"));
            params.add(new BasicNameValuePair("ctl00$ContentPlaceHolder1$btnVerify", "Verify"));
            if (session.eventValidation != null && !session.eventValidation.isEmpty()) {
                params.add(new BasicNameValuePair("__EVENTVALIDATION", session.eventValidation));
            }

            post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));

            String verifyResult = client.execute(post, response -> {
                int status = response.getCode();
                log.info("Udyam verify HTTP status: {}", status);
                byte[] bytes = EntityUtils.toByteArray(response.getEntity());
                return new String(bytes, StandardCharsets.UTF_8);
            });

            log.info("Udyam verify response length={}", verifyResult.length());

            // Check for common error patterns (case-insensitive)
            String verifyLower = verifyResult.toLowerCase();
            if (verifyLower.contains("invalid captcha") || verifyLower.contains("wrong captcha")
                    || verifyLower.contains("captcha mismatch") || verifyLower.contains("please enter captcha")) {
                return UdyamVerifyResponse.error("The CAPTCHA you entered is incorrect. Please try again.");
            }
            if (verifyLower.contains("udyam number not found") || verifyLower.contains("invalid udyam")
                    || verifyLower.contains("no record")) {
                return UdyamVerifyResponse.error("The Udyam number was not found on the government portal. Please check and try again.");
            }
            // Check if the portal returned an error page or challenge
            if (verifyLower.contains("access denied") || verifyLower.contains("forbidden")
                    || verifyLower.contains("blocked") || verifyLower.contains("too many requests")) {
                return UdyamVerifyResponse.error(
                        "The government portal is temporarily blocking automated requests. " +
                        "Please try again in a few minutes.");
            }
            // Check for session-expired or viewstate errors
            if (verifyLower.contains("session has expired") || verifyLower.contains("invalid viewstate")
                    || verifyLower.contains("validation of viewstate mac failed")) {
                return UdyamVerifyResponse.error(
                        "Your verification session has expired. Please go back and load a new CAPTCHA.");
            }

            // ── STEP 4: Fetch print page ──
            HttpGet printRequest = new HttpGet(PRINT_PAGE);
            addBrowserHeaders(printRequest);
            printRequest.setHeader("Upgrade-Insecure-Requests", "1");

            String printHtml = client.execute(printRequest, response -> {
                int status = response.getCode();
                log.info("Udyam print page HTTP status: {}", status);
                byte[] bytes = EntityUtils.toByteArray(response.getEntity());
                return new String(bytes, StandardCharsets.UTF_8);
            });

            log.info("Udyam print page HTML length={}", printHtml.length());

            if (printHtml.length() < 200) {
                return UdyamVerifyResponse.error(
                        "Could not retrieve the MSME certificate from the government portal. " +
                        "Please try again or contact support.");
            }

            // Check if the print page is actually an error page or login page
            String printLower = printHtml.toLowerCase();
            if (printLower.contains("login") && printLower.contains("password")) {
                return UdyamVerifyResponse.error(
                        "The government portal session expired before we could retrieve the certificate. " +
                        "Please try again with a fresh CAPTCHA.");
            }

            // ── Convert HTML to PDF ──
            byte[] pdfBytes = convertHtmlToPdf(printHtml, request.getUdyamNumber());

            // ── Upload PDF to S3 ──
            String fileKey = "msme/verify/" + request.getUdyamNumber().toLowerCase()
                    .replace(" ", "_") + "/udyam_certificate.pdf";
            String pdfUrl = s3Service.uploadFile(pdfBytes, ContentType.APPLICATION_PDF.getMimeType(), fileKey);

            return UdyamVerifyResponse.ok(pdfUrl, null, request.getUdyamNumber());

        } catch (FssaiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify Udyam number", e);
            throw new FssaiException(
                    "We couldn't complete the Udyam verification right now. Please try again in a few minutes.",
                    FailureCode.TEXTRACT_FAILURE, e);
        }
    }

    // ─── HTML → PDF CONVERSION ─────────────────────────────────────────────

    /**
     * Converts the government portal's PrintUdyamApplication HTML into a
     * single-page PDF using OpenHTMLtoPDF.  The raw HTML is first cleaned
     * up with jsoup (strip scripts, fix structure) and wrapped in a
     * minimal XHTML envelope so OpenHTMLtoPDF can render it.
     */
    private byte[] convertHtmlToPdf(String rawHtml, String udyamNumber) {
        try {
            // Parse and clean up the HTML
            Document doc = Jsoup.parse(rawHtml);

            // Remove elements that break PDF rendering or are unnecessary
            doc.select("script").remove();
            doc.select("noscript").remove();
            doc.select("link[rel=stylesheet]").remove();
            doc.select("meta[http-equiv]").remove();

            // Must serialize as XML: jsoup's default HTML5 syntax emits unclosed void
            // tags (<input>, <img>, <br>) which OpenHTMLtoPDF's strict XML/TRaX parser
            // rejects with a SAXParseException -> 500. XML syntax self-closes them.
            doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);

            // Find the certificate content — typically inside a div with id containing
            // "ContentPlaceHolder1" or a table with the certificate
            String bodyContent = doc.body().html();

            // Build a clean XHTML document for OpenHTMLtoPDF
            String xhtml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\"\n" +
                    "  \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" +
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                    "<head>\n" +
                    "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>\n" +
                    "  <style>\n" +
                    "    @page { size: A4; margin: 20mm; }\n" +
                    "    body { font-family: sans-serif; font-size: 12px; color: #000; }\n" +
                    "    table { width: 100%; border-collapse: collapse; }\n" +
                    "    td, th { padding: 4px 8px; border: 1px solid #ccc; font-size: 11px; }\n" +
                    "    th { background: #f0f0f0; font-weight: bold; }\n" +
                    "    h1, h2, h3 { text-align: center; }\n" +
                    "    .header { text-align: center; margin-bottom: 16px; }\n" +
                    "    .header img { max-width: 120px; }\n" +
                    "    .govt-text { font-size: 10px; text-align: center; color: #555; }\n" +
                    "    .cert-title { font-size: 18px; font-weight: bold; text-align: center;\n" +
                    "                   margin: 12px 0; text-transform: uppercase; }\n" +
                    "    .watermark { position: fixed; top: 40%; left: 25%; font-size: 60px;\n" +
                    "                 color: rgba(0,0,0,0.04); transform: rotate(-30deg);\n" +
                    "                 z-index: -1; white-space: nowrap; }\n" +
                    "    .footer { text-align: center; margin-top: 20px; font-size: 10px; color: #888; }\n" +
                    "  </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "  <div class=\"govt-text\">Government of India — Ministry of Micro, Small &amp; Medium Enterprises</div>\n" +
                    "  <div class=\"cert-title\">Udyam Registration Certificate</div>\n" +
                    "  <div class=\"watermark\">UDYAM VERIFIED</div>\n" +
                    bodyContent + "\n" +
                    "  <div class=\"footer\">\n" +
                    "    Generated by DukaanLocker — " + udyamNumber + "\n" +
                    "  </div>\n" +
                    "</body>\n" +
                    "</html>";

            // Render to PDF
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(xhtml, BASE_URL + "/");
            builder.toStream(baos);
            // Try system fonts, fall back gracefully if not found
            try {
                String[] fontPaths = {
                    "C:/Windows/Fonts/arial.ttf",
                    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
                    "/usr/share/fonts/TTF/DejaVuSans.ttf",
                    "/System/Library/Fonts/Helvetica.ttc"
                };
                for (String fontPath : fontPaths) {
                    java.io.File fontFile = new java.io.File(fontPath);
                    if (fontFile.exists()) {
                        builder.useFont(fontFile, "Arial");
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Could not load system font, PDF may use default font", e);
            }
            builder.run();

            byte[] pdfBytes = baos.toByteArray();
            log.info("Generated MSME PDF: {} bytes for Udyam {}", pdfBytes.length, udyamNumber);
            return pdfBytes;

        } catch (Exception e) {
            log.error("Failed to convert Udyam HTML to PDF for {}", udyamNumber, e);
            throw new FssaiException(
                    "The MSME certificate was verified but we couldn't generate the PDF. " +
                    "The HTML certificate has been stored. Please contact support.",
                    FailureCode.PDF_PROCESSING_ERROR, e);
        }
    }

    // ─── HELPERS ───────────────────────────────────────────────────────────

    private static void addBrowserHeaders(HttpGet request) {
        request.setHeader("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        request.setHeader("Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8");
        request.setHeader("Connection", "keep-alive");
        request.setHeader("Referer", BASE_URL + "/");
        request.setHeader("User-Agent", USER_AGENT);
        request.setHeader("Sec-Fetch-Dest", "document");
        request.setHeader("Sec-Fetch-Mode", "navigate");
        request.setHeader("Sec-Fetch-Site", "same-origin");
    }

    private static void addBrowserHeaders(HttpPost request) {
        request.setHeader("Accept", "*/*");
        request.setHeader("Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8");
        request.setHeader("Cache-Control", "no-cache");
        request.setHeader("Connection", "keep-alive");
        request.setHeader("Origin", BASE_URL);
        request.setHeader("Referer", BASE_URL + "/");
        request.setHeader("User-Agent", USER_AGENT);
    }

    /**
     * Extracts the value of a hidden {@code <input>} field from an HTML string
     * by matching {@code id="<fieldName>"} and then the {@code value="..."}.
     */
    private static String extractHiddenValue(String html, String fieldName) {
        Pattern inputPattern = Pattern.compile("<input[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher inputMatcher = inputPattern.matcher(html);

        while (inputMatcher.find()) {
            String input = inputMatcher.group();
            if (input.contains("id=\"" + fieldName + "\"")) {
                Matcher valueMatcher = Pattern.compile(
                        "value=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                        .matcher(input);
                if (valueMatcher.find()) {
                    return valueMatcher.group(1);
                }
            }
        }
        return "";
    }

    // ─── CAPTCHA IMAGE SERVING ───────────────────────────────────────────

    /**
     * Returns the raw captcha image bytes for the given session, or {@code null}
     * if the session doesn't exist or has expired.
     */
    public byte[] getCaptchaImage(String sessionId) {
        UdyamSession session = sessions.get(sessionId);
        if (session == null || session.captchaImage == null) {
            return null;
        }
        return session.captchaImage;
    }

    // ─── SESSION STATE ─────────────────────────────────────────────────────

    private static class UdyamSession {
        long createdAt;
        String viewState;
        String viewStateGenerator;
        String eventValidation;
        List<Cookie> cookies;
        byte[] captchaImage;  // raw image bytes for direct serving
    }
}
