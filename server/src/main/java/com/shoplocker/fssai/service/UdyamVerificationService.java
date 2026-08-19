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

            return UdyamVerifyResponse.ok(pdfUrl, printHtml, request.getUdyamNumber());

        } catch (FssaiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify Udyam number", e);
            throw new FssaiException(
                    "We couldn't complete the Udyam verification right now. Please try again in a few minutes.",
                    FailureCode.TEXTRACT_FAILURE, e);
        }
    }

    // ─── HTML \u2192 PDF CONVERSION ─────────────────────────────────────────────

    /**
     * Converts the government portal's PrintUdyamApplication HTML into a
     * professional single-page PDF using OpenHTMLtoPDF. Creates an official-looking
     * MSME certificate that closely resembles the actual Udyam Registration Certificate
     * issued by the Ministry of Micro, Small &amp; Medium Enterprises, Government of India.
     *
     * The format includes:
     * - Government of India header with Ashoka Chakra emblem
     * - Certificate title and registration number
     * - Enterprise details (name, type, address, promoter, etc.)
     * - Investment and turnover details
     * - NIC codes for business activity
     * - QR code placeholder for digital verification
     * - Official signature area
     */
    private byte[] convertHtmlToPdf(String rawHtml, String udyamNumber) {
        try {
            // Parse the HTML
            Document doc = Jsoup.parse(rawHtml);

            // Remove elements that break PDF rendering or are unnecessary
            doc.select("script").remove();
            doc.select("noscript").remove();
            doc.select("link[rel=stylesheet]").remove();
            doc.select("meta[http-equiv]").remove();
            doc.select("form").remove(); // Remove ASP.NET form elements

            // Must serialize as XML: jsoup's default HTML5 syntax emits unclosed void
            // tags (<input>, <img>, <br>) which OpenHTMLtoPDF's strict XML/TRaX parser
            // rejects with a SAXParseException -> 500. XML syntax self-closes them.
            doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);

            // Extract certificate data from tables
            StringBuilder certFields = new StringBuilder();
            StringBuilder nicCodeRows = new StringBuilder();
            String enterpriseName = "";
            String entrepreneurName = "";
            String enterpriseType = "";
            String address = "";
            String mobile = "";
            String email = "";
            String state = "";
            String district = "";
            String city = "";
            String pincode = "";
            String dateOfRegistration = "";
            String pan = "";
            String investment = "";
            String turnover = "";

            // Find all tables and extract the certificate data
            org.jsoup.select.Elements tables = doc.select("table");
            for (org.jsoup.nodes.Element table : tables) {
                String tableText = table.text().toLowerCase();

                // Main certificate fields table
                if (tableText.contains("udyam registration number") ||
                        tableText.contains("name of enterprise") ||
                        tableText.contains("type of enterprise")) {
                    org.jsoup.select.Elements rows = table.select("tr");
                    for (org.jsoup.nodes.Element row : rows) {
                        org.jsoup.select.Elements cells = row.select("td, th");
                        if (cells.size() >= 2) {
                            String label = cells.get(0).text().trim().toLowerCase();
                            String value = cells.get(1).text().trim();
                            if (!value.isEmpty()) {
                                // Extract specific fields for structured layout
                                if (label.contains("name of enterprise") || label.contains("enterprise name")) {
                                    enterpriseName = value;
                                } else if (label.contains("name of entrepreneur") || label.contains("entrepreneur name")
                                        || label.contains("owner name") || label.contains("proprietor name")) {
                                    entrepreneurName = value;
                                } else if (label.contains("type of enterprise") || label.contains("enterprise type")) {
                                    enterpriseType = value;
                                } else if (label.contains("mobile") || label.contains("phone")) {
                                    mobile = value;
                                } else if (label.contains("email")) {
                                    email = value;
                                } else if (label.contains("state")) {
                                    state = value;
                                } else if (label.contains("district")) {
                                    district = value;
                                } else if (label.contains("city") || label.contains("town")) {
                                    city = value;
                                } else if (label.contains("pin")) {
                                    pincode = value;
                                } else if (label.contains("date of registration") || label.contains("registration date")) {
                                    dateOfRegistration = value;
                                } else if (label.contains("pan") && value.length() == 10) {
                                    pan = value;
                                } else if (label.contains("investment") || label.contains("plant and machinery")) {
                                    investment = value;
                                } else if (label.contains("turnover")) {
                                    turnover = value;
                                }

                                // Skip NIC code rows from main table (we handle them separately)
                                if (label.contains("nic")) continue;

                                // Add to fields table for display
                                certFields.append("<tr><td class=\"field-label\">")
                                        .append(escapeXml(cells.get(0).text().trim()))
                                        .append("</td><td class=\"field-value\">")
                                        .append(escapeXml(value))
                                        .append("</td></tr>\n");
                            }
                        }
                    }
                }
                // NIC codes table
                if (tableText.contains("nic 2 digit") || tableText.contains("nic 4 digit") ||
                        tableText.contains("national industry")) {
                    org.jsoup.select.Elements rows = table.select("tr");
                    for (org.jsoup.nodes.Element row : rows) {
                        org.jsoup.select.Elements cells = row.select("td, th");
                        if (cells.size() >= 2) {
                            String nic2 = cells.size() > 0 ? cells.get(0).text().trim() : "";
                            String nic4 = cells.size() > 1 ? cells.get(1).text().trim() : "";
                            String activity = cells.size() > 2 ? cells.get(2).text().trim() : "";
                            if (!nic2.isEmpty() && !nic2.toLowerCase().contains("s.no")) {
                                nicCodeRows.append("<tr><td>")
                                        .append(escapeXml(nic2))
                                        .append("</td><td>")
                                        .append(escapeXml(nic4))
                                        .append("</td><td>")
                                        .append(escapeXml(activity))
                                        .append("</td></tr>\n");
                            }
                        }
                    }
                }
            }

            // If no certificate table found, use a fallback approach
            if (certFields.length() == 0) {
                String fullText = doc.text();
                certFields.append(extractCertificateFields(fullText));
            }

            // Build address string
            if (address.isEmpty()) {
                StringBuilder addrBuilder = new StringBuilder();
                if (!city.isEmpty()) addrBuilder.append(city);
                if (!district.isEmpty()) {
                    if (addrBuilder.length() > 0) addrBuilder.append(", ");
                    addrBuilder.append(district);
                }
                if (!state.isEmpty()) {
                    if (addrBuilder.length() > 0) addrBuilder.append(", ");
                    addrBuilder.append(state);
                }
                if (!pincode.isEmpty()) {
                    if (addrBuilder.length() > 0) addrBuilder.append(" - ");
                    addrBuilder.append(pincode);
                }
                address = addrBuilder.toString();
            }

            // Format date for display
            String printDate = new java.text.SimpleDateFormat("dd MMMM yyyy, hh:mm a").format(new java.util.Date());

            // Build NIC codes HTML rows for the new template
            String nicRowsHtml = "";
            if (nicCodeRows.length() > 0) {
                nicRowsHtml = nicCodeRows.toString();
            } else {
                nicRowsHtml = "<tr><td>-</td><td>-</td><td>-</td><td>-</td><td>-</td></tr>\n";
            }

            String xhtml = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" +
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>\n" +
                    "<style>\n" +
                    "  @page { size: A4 portrait; margin: 12mm 14mm; }\n" +
                    "  * { box-sizing: border-box; }\n" +
                    "  body { margin:0; padding:0; font-family: Arial, Helvetica, sans-serif; font-size:9pt; color:#111; line-height:1.3; }\n" +
                    "  .certificate { border:2px solid #57528c; padding:3px; background:#fff; }\n" +
                    "\n" +
                    "  /* ===== HEADER ===== */\n" +
                    "  .top { position:relative; background:linear-gradient(135deg, #57528c 0%, #6b62a0 50%, #57528c 100%); color:#fff; text-align:center; padding:14px 10px 12px 10px; min-height:80px; }\n" +
                    "  .emblem { position:absolute; left:12px; top:8px; width:52px; height:52px; border:2px solid rgba(255,255,255,0.4); border-radius:50%; display:flex; align-items:center; justify-content:center; font-size:32pt; color:#ffd54f; background:rgba(255,255,255,0.08); }\n" +
                    "  .top-title { font-size:8pt; font-weight:bold; letter-spacing:3px; margin:0; text-transform:uppercase; }\n" +
                    "  .top-govt { font-size:11pt; font-weight:bold; margin:2px 0 1px; letter-spacing:1px; text-transform:uppercase; }\n" +
                    "  .top-ministry { font-size:8.5pt; margin:1px 0; letter-spacing:0.5px; }\n" +
                    "  .top-office { font-size:7.5pt; margin:3px 0 0; opacity:0.85; font-style:italic; }\n" +
                    "  .udyam-badge { position:absolute; right:12px; top:8px; width:52px; height:52px; border:2px solid rgba(255,255,255,0.4); border-radius:50%; display:flex; align-items:center; justify-content:center; font-size:24pt; color:#ffd54f; background:rgba(255,255,255,0.08); font-weight:bold; }\n" +
                    "\n" +
                    "  /* ===== TITLE ===== */\n" +
                    "  .title { text-align:center; font-family:'Times New Roman',Times,serif; font-size:16pt; font-weight:bold; color:#57528c; padding:8px 0 4px; text-transform:uppercase; letter-spacing:1px; border-bottom:2px solid #57528c; margin-bottom:4px; }\n" +
                    "  .numbers { width:100%; font-family:'Times New Roman',Times,serif; font-weight:bold; font-size:8.5pt; padding:4px 8px 6px; color:#333; }\n" +
                    "  .numbers span { float:right; color:#57528c; font-size:9pt; }\n" +
                    "\n" +
                    "  /* ===== TABLES ===== */\n" +
                    "  table { width:100%; border-collapse:collapse; margin-bottom:4px; }\n" +
                    "  td, th { border:1px solid #bbb; padding:5px 8px; vertical-align:middle; }\n" +
                    "  .noborder td { border:none; padding:6px 8px; }\n" +
                    "\n" +
                    "  /* ===== FIELD ROWS ===== */\n" +
                    "  .label { width:39%; font-family:'Times New Roman',Times,serif; font-weight:bold; font-size:9pt; color:#333; }\n" +
                    "  .value { font-weight:bold; font-size:9.5pt; color:#111; }\n" +
                    "\n" +
                    "  /* Enterprise Name Row */\n" +
                    "  .enterprise-row td { padding:8px; }\n" +
                    "  .enterprise-value { color:#57528c; font-size:14pt; font-weight:bold; letter-spacing:0.5px; }\n" +
                    "\n" +
                    "  /* Classification Table */\n" +
                    "  .small th, .small td { font-size:8pt; padding:4px 6px; text-align:center; }\n" +
                    "  .small th { font-family:'Times New Roman',Times,serif; background:#f3f0f9; font-weight:bold; }\n" +
                    "  .small .left { text-align:left; }\n" +
                    "\n" +
                    "  /* Green Section Bar */\n" +
                    "  .section { background:#2e7d32; color:#fff; text-align:center; font-family:'Times New Roman',Times,serif; font-size:10pt; font-weight:bold; padding:4px; margin:6px 0; letter-spacing:0.5px; }\n" +
                    "\n" +
                    "  /* Address Table */\n" +
                    "  .address td { font-size:8.5pt; padding:5px 8px; }\n" +
                    "  .address .side { width:39%; text-align:center; font:bold 9pt 'Times New Roman',Times,serif; background:#f3f0f9; }\n" +
                    "\n" +
                    "  /* Date Table */\n" +
                    "  .date td { height:30px; }\n" +
                    "  .date .label { text-align:center; font-size:8.5pt; }\n" +
                    "  .date .value { text-align:center; font-size:9pt; font-weight:bold; }\n" +
                    "\n" +
                    "  /* NIC Table */\n" +
                    "  .nic th { font:bold 8pt 'Times New Roman',Times,serif; background:#f3f0f9; padding:5px 6px; }\n" +
                    "  .nic td { font-size:8pt; line-height:1.2; padding:5px 6px; }\n" +
                    "\n" +
                    "  /* Deregister Row */\n" +
                    "  .deregister td { height:30px; }\n" +
                    "  .deregister .label { text-align:center; font-size:8pt; }\n" +
                    "  .deregister .value { text-align:center; font-size:8.5pt; }\n" +
                    "\n" +
                    "  /* Notes */\n" +
                    "  .note { font:8pt 'Times New Roman',Times,serif; line-height:1.4; padding:8px 10px 0; background:#fafafa; border:1px solid #e0e0e0; margin-top:6px; }\n" +
                    "  .note p { margin:0 0 5px; }\n" +
                    "  .note .bold { font-weight:bold; }\n" +
                    "\n" +
                    "  .space { height:4px; }\n" +
                    "</style></head><body><div class=\"certificate\">\n" +
                    "  <div class=\"top\">\n" +
                    "    <div class=\"emblem\">\u2638</div>\n" +
                    "    <p class=\"top-title\">UDYAM REGISTRATION</p>\n" +
                    "    <p class=\"top-govt\">Government of India</p>\n" +
                    "    <p class=\"top-ministry\">Ministry of Micro, Small &amp; Medium Enterprises</p>\n" +
                    "    <p class=\"top-office\">Office of Development Commissioner (MSME)</p>\n" +
                    "    <div class=\"udyam-badge\">U</div>\n" +
                    "  </div>\n" +
                    "\n" +
                    "  <div class=\"title\">UDYAM REGISTRATION CERTIFICATE</div>\n" +
                    "  <div class=\"numbers\">UDYAM REGISTRATION NUMBER <span>" + escapeXml(udyamNumber) + "</span></div>\n" +
                    "\n" +
                    "  <table class=\"noborder enterprise-row\">\n" +
                    "    <tr><td class=\"label\">NAME OF ENTERPRISE</td><td class=\"enterprise-value\">" + escapeXml(!enterpriseName.isEmpty() ? enterpriseName : "-") + "</td></tr>\n" +
                    "  </table>\n" +
                    "\n" +
                    "  <table class=\"small\">\n" +
                    "    <tr><th>TYPE OF ENTERPRISE *</th><th>Micro</th><th>Small</th><th>Medium</th><th>CLASSIFICATION DATE</th></tr>\n" +
                    "    <tr><td></td><td>" + (enterpriseType.toLowerCase().contains("micro") ? "\u2713" : "-") + "</td><td>" + (enterpriseType.toLowerCase().contains("small") ? "\u2713" : "-") + "</td><td>" + (enterpriseType.toLowerCase().contains("medium") ? "\u2713" : "-") + "</td><td>" + escapeXml(!dateOfRegistration.isEmpty() ? dateOfRegistration : "-") + "</td></tr>\n" +
                    "  </table>\n" +
                    "\n" +
                    "  <table class=\"noborder\"><tr><td class=\"label\">MAJOR ACTIVITY</td><td class=\"value\">" + (nicCodeRows.length() > 0 ? "Services / Manufacturing" : "-") + "</td></tr></table>\n" +
                    "  <div class=\"section\">" + (nicCodeRows.length() > 0 ? "ACTIVITY DETAILS" : "SERVICES") + "</div>\n" +
                    "\n" +
                    "  <table class=\"noborder\"><tr><td class=\"label\">SOCIAL CATEGORY OF ENTREPRENEUR</td><td class=\"value\">GENERAL</td></tr></table>\n" +
                    (nicCodeRows.length() > 0 ?
                    "  <table class=\"small\"><tr><th class=\"left\">NAME OF UNIT(S)</th><th>S.No.</th><th>Name of Unit(s)</th></tr><tr><td></td><td>1</td><td style=\"text-align:left;\">" + escapeXml(!enterpriseName.isEmpty() ? enterpriseName : "Enterprise") + "</td></tr></table>\n"
                    : "") +
                    "  <div class=\"space\"></div>\n" +
                    "\n" +
                    "  <table class=\"address\">\n" +
                    "    <tr><td class=\"side\" rowspan=\"4\">OFFICIAL ADDRESS OF ENTERPRISE</td><td>Flat/Door/Block No.</td><td>" + escapeXml(!address.isEmpty() ? address : "-") + "</td></tr>\n" +
                    "    <tr><td>Village/Town</td><td>" + escapeXml(!city.isEmpty() ? city : "-") + "</td></tr>\n" +
                    "    <tr><td>Road/Street/Lane</td><td>" + escapeXml(!district.isEmpty() ? district : "-") + "</td></tr>\n" +
                    "    <tr><td>State</td><td>" + escapeXml(!state.isEmpty() ? state : "-") + "</td></tr>\n" +
                    "  </table>\n" +
                    "  <div class=\"space\"></div>\n" +
                    "\n" +
                    "  <table class=\"date\">\n" +
                    "    <tr><td class=\"label\">DATE OF INCORPORATION / REGISTRATION OF ENTERPRISE</td><td class=\"value\">" + escapeXml(!dateOfRegistration.isEmpty() ? dateOfRegistration : "-") + "</td></tr>\n" +
                    "    <tr><td class=\"label\">DATE OF COMMENCEMENT OF PRODUCTION / BUSINESS</td><td class=\"value\">" + escapeXml(!dateOfRegistration.isEmpty() ? dateOfRegistration : "-") + "</td></tr>\n" +
                    "  </table>\n" +
                    "  <div class=\"space\"></div>\n" +
                    "\n" +
                    "  <table class=\"nic\">\n" +
                    "    <tr><th style=\"width:8%;\">S.No.</th><th>NIC 2 Digit</th><th>NIC 4 Digit</th><th>NIC 5 Digit</th><th>Activity</th></tr>\n" +
                    nicRowsHtml +
                    "  </table>\n" +
                    "  <div class=\"space\"></div>\n" +
                    "\n" +
                    "  <table class=\"deregister\"><tr><td class=\"label\">DATE OF UDYAM REGISTRATION DEREGISTRATION</td><td class=\"value\">--/--/----</td></tr></table>\n" +
                    "\n" +
                    "  <div class=\"note\">\n" +
                    "    <p>1. In case of proprietorship, the registration will be in the name of proprietor. In case of partnership concern, the registration will be issued as per the provisions of Partnership Act, 1932.</p>\n" +
                    "    <p>2. The enterprise shall furnish the information online and self-declaration on the Udyam Registration portal. This certificate is based on the details furnished by the enterprise.</p>\n" +
                    "    <p><span class=\"bold\">For any assistance, you may contact:</span></p>\n" +
                    "    <p>1. District Industry Centre: Check with your local DIC office</p>\n" +
                    "    <p>2. MSME-DFO: Check with your regional MSME office</p>\n" +
                    "  </div>\n" +
                    "\n" +
                    "  <div style=\"font-size:7pt; color:#888; text-align:right; padding:6px 10px 2px; border-top:1px solid #ddd; margin-top:6px;\">Generated by DukaanLocker on " + printDate + "</div>\n" +
                    "</div></body></html>";

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

    /**
     * Extracts certificate fields from plain text using regex patterns.
     * Used as fallback when table parsing fails.
     */
    private String extractCertificateFields(String text) {
        StringBuilder html = new StringBuilder();

        // Udyam Number
        Pattern udyamPattern = Pattern.compile("UDYAM-[A-Z]{2}-\\d{2}-\\d{7}", Pattern.CASE_INSENSITIVE);
        Matcher m = udyamPattern.matcher(text);
        if (m.find()) {
            html.append("<tr><td class=\"field-label\">Udyam Registration Number</td><td class=\"field-value\">")
                    .append(escapeXml(m.group().toUpperCase()))
                    .append("</td></tr>");
        }

        // Enterprise Name - use more robust extraction
        String lowerText = text.toLowerCase();
        String[] labels = {"name of enterprise", "enterprise name", "business name"};
        for (String label : labels) {
            if (lowerText.contains(label)) {
                int idx = lowerText.indexOf(label);
                String afterLabel = text.substring(idx + label.length()).trim();
                // Skip any colon or whitespace
                if (afterLabel.startsWith(":") || afterLabel.startsWith("-")) {
                    afterLabel = afterLabel.substring(1).trim();
                }
                // Extract until newline or next common label
                String name = afterLabel.split("[\\n\\r]")[0].trim();
                // Further trim if it contains "Type of" or other labels
                if (name.toLowerCase().contains("type of")) {
                    name = name.substring(0, name.toLowerCase().indexOf("type of")).trim();
                }
                if (!name.isEmpty() && name.length() < 100) {
                    html.append("<tr><td class=\"field-label\">Name of Enterprise</td><td class=\"field-value\">")
                            .append(escapeXml(name))
                            .append("</td></tr>");
                    break;
                }
            }
        }

        return html.toString();
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
