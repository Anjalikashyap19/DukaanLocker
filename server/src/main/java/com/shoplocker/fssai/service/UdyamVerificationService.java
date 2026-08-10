package com.shoplocker.fssai.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
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
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.shoplocker.fssai.dto.UdyamInitResponse;
import com.shoplocker.fssai.dto.UdyamVerifyRequest;
import com.shoplocker.fssai.dto.UdyamVerifyResponse;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

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
 * (https://www.udyamregistration.gov.in/) to:
 *
 * 1. Initialise an HTTP session and download a CAPTCHA image.
 * 2. Verify a Udyam number + CAPTCHA answer against the portal.
 * 3. Fetch the HTML certificate page.
 * 4. Convert the government HTML into a PDF.
 * 5. Upload the generated PDF to S3.
 *
 * Session state is kept in an in-memory map keyed by a random UUID.
 * Sessions automatically expire after 5 minutes.
 */
@Service
public class UdyamVerificationService {

    private static final Logger log =
            LoggerFactory.getLogger(UdyamVerificationService.class);

    /*
     * ============================================================
     * UDYAM URLs
     * ============================================================
     */

    private static final String BASE_URL =
            "https://www.udyamregistration.gov.in";

    private static final String VERIFY_PAGE =
            BASE_URL + "/Udyam_Verify.aspx";

    private static final String CAPTCHA_URL_PREFIX =
            BASE_URL + "/Captcha/CaptchaControl.aspx?id=";

    private static final String PRINT_PAGE =
            BASE_URL + "/PrintUdyamApplication.aspx";


    /*
     * ============================================================
     * SESSION SETTINGS
     * ============================================================
     */

    private static final long SESSION_TTL_MS =
            5 * 60 * 1000; // 5 minutes

    private static final int MAX_SESSIONS =
            1000;


    /*
     * ============================================================
     * USER AGENT
     * ============================================================
     */

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/150.0.0.0 Safari/537.36";


    /*
     * ============================================================
     * SESSION STORE
     * ============================================================
     */

    private final ConcurrentHashMap<String, UdyamSession> sessions =
            new ConcurrentHashMap<>();


    /*
     * ============================================================
     * S3 SERVICE
     * ============================================================
     */

    private final S3Service s3Service;


    public UdyamVerificationService(S3Service s3Service) {
        this.s3Service = s3Service;
    }


    /*
     * ============================================================
     * SESSION CLEANER
     * ============================================================
     */

    @PostConstruct
    void startSessionCleaner() {

        Thread t = new Thread(() -> {

            while (!Thread.currentThread().isInterrupted()) {

                try {

                    Thread.sleep(60_000);

                    long now =
                            System.currentTimeMillis();

                    sessions.entrySet().removeIf(
                            entry ->
                                    now - entry.getValue().createdAt
                                            > SESSION_TTL_MS
                    );

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();

                } catch (Exception e) {

                    log.error(
                            "Error while cleaning Udyam sessions",
                            e
                    );
                }
            }

        }, "udyam-session-cleaner");

        t.setDaemon(true);
        t.start();
    }


    @PreDestroy
    void shutdown() {

        log.info(
                "Shutting down Udyam verification service"
        );

        sessions.clear();
    }


    // ============================================================
    // PUBLIC API
    // ============================================================


    /**
     * STEP 1
     *
     * Initialise session with Udyam portal.
     *
     * Gets:
     * - ASP.NET cookies
     * - VIEWSTATE
     * - VIEWSTATEGENERATOR
     * - EVENTVALIDATION
     * - CAPTCHA
     */
    public UdyamInitResponse initSession() {

        BasicCookieStore cookieStore =
                new BasicCookieStore();

        RequestConfig config =
                RequestConfig.custom()
                        .setConnectTimeout(
                                Timeout.ofMilliseconds(30_000)
                        )
                        .setResponseTimeout(
                                Timeout.ofMilliseconds(60_000)
                        )
                        .setConnectionRequestTimeout(
                                Timeout.ofMilliseconds(30_000)
                        )
                        .build();


        try (CloseableHttpClient client =
                     HttpClients.custom()
                             .setDefaultCookieStore(cookieStore)
                             .setDefaultRequestConfig(config)
                             .build()) {


            // ====================================================
            // STEP 1 - LOAD VERIFY PAGE
            // ====================================================

            HttpGet pageRequest =
                    new HttpGet(VERIFY_PAGE);

            addBrowserHeaders(pageRequest);

            log.info(
                    "Udyam init - fetching verify page: {}",
                    VERIFY_PAGE
            );


            String html =
                    client.execute(
                            pageRequest,
                            response -> {

                                int status =
                                        response.getCode();

                                log.info(
                                        "Udyam verify page HTTP status: {}",
                                        status
                                );

                                if (status != 200) {

                                    throw new IOException(
                                            "Failed to fetch Udyam verify page, HTTP "
                                                    + status
                                    );
                                }

                                byte[] bytes =
                                        EntityUtils.toByteArray(
                                                response.getEntity()
                                        );

                                return new String(
                                        bytes,
                                        StandardCharsets.UTF_8
                                );
                            }
                    );


            // ====================================================
            // EXTRACT ASP.NET HIDDEN VALUES
            // ====================================================

            String viewState =
                    extractHiddenValue(
                            html,
                            "__VIEWSTATE"
                    );

            String viewStateGenerator =
                    extractHiddenValue(
                            html,
                            "__VIEWSTATEGENERATOR"
                    );

            String eventValidation =
                    extractHiddenValue(
                            html,
                            "__EVENTVALIDATION"
                    );


            if (viewState.isEmpty()) {

                log.warn(
                        "Udyam init - VIEWSTATE is empty"
                );
            }


            log.info(
                    "Udyam init - VIEWSTATE length={}, EVENTVALIDATION length={}",
                    viewState.length(),
                    eventValidation.length()
            );


            // ====================================================
            // STEP 2 - DOWNLOAD CAPTCHA
            // ====================================================

            SimpleDateFormat sdf =
                    new SimpleDateFormat(
                            "M/d/yyyy h:mm:ss a"
                    );


            String timestamp =
                    URLEncoder.encode(
                            sdf.format(new Date()),
                            StandardCharsets.UTF_8
                    );


            String captchaUrl =
                    CAPTCHA_URL_PREFIX + timestamp;


            log.info(
                    "Udyam init - downloading captcha"
            );


            HttpGet captchaRequest =
                    new HttpGet(captchaUrl);

            addBrowserHeaders(captchaRequest);


            captchaRequest.setHeader(
                    "Accept",
                    "image/avif,image/webp,image/apng," +
                            "image/svg+xml,image/*,*/*;q=0.8"
            );

            captchaRequest.setHeader(
                    "Sec-Fetch-Dest",
                    "image"
            );

            captchaRequest.setHeader(
                    "Sec-Fetch-Mode",
                    "no-cors"
            );

            captchaRequest.setHeader(
                    "Sec-Fetch-Site",
                    "same-origin"
            );


            byte[] captchaBytes =
                    client.execute(
                            captchaRequest,
                            response -> {

                                int status =
                                        response.getCode();

                                log.info(
                                        "Udyam captcha HTTP status: {}",
                                        status
                                );


                                if (status != 200) {

                                    throw new IOException(
                                            "Failed to download captcha, HTTP "
                                                    + status
                                    );
                                }


                                Header contentTypeHeader =
                                        response.getFirstHeader(
                                                "Content-Type"
                                        );


                                String contentType =
                                        contentTypeHeader != null
                                                ? contentTypeHeader.getValue()
                                                : "unknown";


                                log.info(
                                        "Udyam captcha Content-Type: {}",
                                        contentType
                                );


                                if (!contentType
                                        .toLowerCase()
                                        .contains("image/")) {

                                    throw new IOException(
                                            "Government portal returned non-image content. "
                                                    + "Content-Type: "
                                                    + contentType
                                    );
                                }


                                byte[] bytes =
                                        EntityUtils.toByteArray(
                                                response.getEntity()
                                        );


                                log.info(
                                        "Udyam captcha downloaded: {} bytes",
                                        bytes.length
                                );


                                if (bytes.length < 100) {

                                    throw new IOException(
                                            "Government portal returned an invalid captcha image."
                                    );
                                }


                                return bytes;
                            }
                    );


            // ====================================================
            // CAPTCHA BASE64
            // ====================================================

            String captchaBase64 =
                    "data:image/png;base64,"
                            + Base64.getEncoder()
                            .encodeToString(captchaBytes);


            // ====================================================
            // SESSION LIMIT
            // ====================================================

            if (sessions.size() >= MAX_SESSIONS) {

                throw new FssaiException(
                        "Too many active sessions. Please try again in a minute.",
                        FailureCode.TEXTRACT_FAILURE
                );
            }


            // ====================================================
            // CREATE SESSION
            // ====================================================

            String sessionId =
                    UUID.randomUUID().toString();


            UdyamSession session =
                    new UdyamSession();


            session.createdAt =
                    System.currentTimeMillis();


            session.viewState =
                    viewState;

            session.viewStateGenerator =
                    viewStateGenerator;

            session.eventValidation =
                    eventValidation;


            session.cookies =
                    new ArrayList<>();


            for (Cookie cookie :
                    cookieStore.getCookies()) {

                session.cookies.add(cookie);
            }


            session.captchaImage =
                    captchaBytes;


            sessions.put(
                    sessionId,
                    session
            );


            log.info(
                    "Udyam session created: {}",
                    sessionId
            );


            return new UdyamInitResponse(
                    sessionId,
                    captchaBase64
            );


        } catch (Exception e) {

            log.error(
                    "Failed to initialise Udyam session",
                    e
            );


            throw new FssaiException(
                    "We couldn't connect to the Udyam verification portal right now. "
                            + "Please try again in a few minutes.",
                    FailureCode.TEXTRACT_FAILURE,
                    e
            );
        }
    }


    /**
     * STEP 3 + STEP 4
     *
     * Verify Udyam number + CAPTCHA.
     *
     * Then:
     *
     * Udyam portal
     *      ↓
     * PrintUdyamApplication.aspx
     *      ↓
     * HTML
     *      ↓
     * PDF
     *      ↓
     * S3
     */
    public UdyamVerifyResponse verifyAndGeneratePdf(
            UdyamVerifyRequest request
    ) {


        // ========================================================
        // GET AND CONSUME SESSION
        // ========================================================

        UdyamSession session =
                sessions.remove(
                        request.getSessionId()
                );


        if (session == null) {

            throw new FssaiException(
                    "Your session has expired. Please go back and load a new CAPTCHA.",
                    FailureCode.INVALID_REQUEST
            );
        }


        // ========================================================
        // RESTORE COOKIES
        // ========================================================

        BasicCookieStore cookieStore =
                new BasicCookieStore();


        for (Cookie cookie :
                session.cookies) {

            cookieStore.addCookie(cookie);
        }


        RequestConfig config =
                RequestConfig.custom()
                        .setConnectTimeout(
                                Timeout.ofMilliseconds(30_000)
                        )
                        .setResponseTimeout(
                                Timeout.ofMilliseconds(60_000)
                        )
                        .setConnectionRequestTimeout(
                                Timeout.ofMilliseconds(30_000)
                        )
                        .build();


        try (CloseableHttpClient client =
                     HttpClients.custom()
                             .setDefaultCookieStore(cookieStore)
                             .setDefaultRequestConfig(config)
                             .build()) {


            // ====================================================
            // STEP 3 - VERIFY UDYAM
            // ====================================================

            HttpPost post =
                    new HttpPost(VERIFY_PAGE);


            addBrowserHeaders(post);


            post.setHeader(
                    "Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8"
            );


            post.setHeader(
                    "Origin",
                    BASE_URL
            );


            post.setHeader(
                    "X-MicrosoftAjax",
                    "Delta=true"
            );


            post.setHeader(
                    "X-Requested-With",
                    "XMLHttpRequest"
            );


            List<NameValuePair> params =
                    new ArrayList<>();


            params.add(
                    new BasicNameValuePair(
                            "ctl00$sm",
                            "ctl00$ContentPlaceHolder1$UpdatePaneldd1|"
                                    + "ctl00$ContentPlaceHolder1$btnVerify"
                    )
            );


            params.add(
                    new BasicNameValuePair(
                            "__EVENTTARGET",
                            ""
                    )
            );


            params.add(
                    new BasicNameValuePair(
                            "__EVENTARGUMENT",
                            ""
                    )
            );


            params.add(
                    new BasicNameValuePair(
                            "__VIEWSTATE",
                            session.viewState
                    )
            );


            params.add(
                    new BasicNameValuePair(
                            "__VIEWSTATEGENERATOR",
                            session.viewStateGenerator
                    )
            );


            params.add(
                    new BasicNameValuePair(
                            "__VIEWSTATEENCRYPTED",
                            ""
                    )
            );


            params.add(
                    new BasicNameValuePair(
                            "ctl00$ContentPlaceHolder1$hdnSetPassword",
                            ""
                    )
            );


            params.add(
                    new BasicNameValuePair(
                            "ctl00$ContentPlaceHolder1$txtUdyamNo",
                            request.getUdyamNumber()
                    )
            );


            params.add(
                    new BasicNameValuePair(
                            "ctl00$ContentPlaceHolder1$txtCaptcha",
                            request.getCaptchaText()
                    )
            );


            params.add(
                    new BasicNameValuePair(
                            "__ASYNCPOST",
                            "true"
                    )
            );


            params.add(
                    new BasicNameValuePair(
                            "ctl00$ContentPlaceHolder1$btnVerify",
                            "Verify"
                    )
            );


            if (session.eventValidation != null
                    && !session.eventValidation.isEmpty()) {

                params.add(
                        new BasicNameValuePair(
                                "__EVENTVALIDATION",
                                session.eventValidation
                        )
                );
            }


            post.setEntity(
                    new UrlEncodedFormEntity(
                            params,
                            StandardCharsets.UTF_8
                    )
            );


            String verifyResult =
                    client.execute(
                            post,
                            response -> {

                                int status =
                                        response.getCode();

                                log.info(
                                        "Udyam verify HTTP status: {}",
                                        status
                                );


                                byte[] bytes =
                                        EntityUtils.toByteArray(
                                                response.getEntity()
                                        );


                                return new String(
                                        bytes,
                                        StandardCharsets.UTF_8
                                );
                            }
                    );


            log.info(
                    "Udyam verify response length={}",
                    verifyResult.length()
            );


            // ====================================================
            // CHECK CAPTCHA ERRORS
            // ====================================================

            String verifyLower =
                    verifyResult.toLowerCase();


            if (verifyLower.contains("invalid captcha")
                    || verifyLower.contains("wrong captcha")
                    || verifyLower.contains("captcha mismatch")
                    || verifyLower.contains("please enter captcha")) {

                return UdyamVerifyResponse.error(
                        "The CAPTCHA you entered is incorrect. Please try again."
                );
            }


            // ====================================================
            // CHECK UDYAM ERRORS
            // ====================================================

            if (verifyLower.contains("udyam number not found")
                    || verifyLower.contains("invalid udyam")
                    || verifyLower.contains("no record")) {

                return UdyamVerifyResponse.error(
                        "The Udyam number was not found on the government portal. "
                                + "Please check and try again."
                );
            }


            // ====================================================
            // CHECK BLOCKING
            // ====================================================

            if (verifyLower.contains("access denied")
                    || verifyLower.contains("forbidden")
                    || verifyLower.contains("blocked")
                    || verifyLower.contains("too many requests")) {

                return UdyamVerifyResponse.error(
                        "The government portal is temporarily blocking automated requests. "
                                + "Please try again in a few minutes."
                );
            }


            // ====================================================
            // CHECK SESSION
            // ====================================================

            if (verifyLower.contains("session has expired")
                    || verifyLower.contains("invalid viewstate")
                    || verifyLower.contains(
                    "validation of viewstate mac failed"
            )) {

                return UdyamVerifyResponse.error(
                        "Your verification session has expired. "
                                + "Please go back and load a new CAPTCHA."
                );
            }


            // ====================================================
            // STEP 4 - FETCH PRINT PAGE
            // ====================================================

            HttpGet printRequest =
                    new HttpGet(PRINT_PAGE);


            addBrowserHeaders(printRequest);


            printRequest.setHeader(
                    "Upgrade-Insecure-Requests",
                    "1"
            );


            log.info(
                    "Fetching Udyam certificate print page"
            );


            String printHtml =
                    client.execute(
                            printRequest,
                            response -> {

                                int status =
                                        response.getCode();

                                log.info(
                                        "Udyam print page HTTP status: {}",
                                        status
                                );


                                byte[] bytes =
                                        EntityUtils.toByteArray(
                                                response.getEntity()
                                        );


                                return new String(
                                        bytes,
                                        StandardCharsets.UTF_8
                                );
                            }
                    );


            log.info(
                    "Udyam print page HTML length={}",
                    printHtml.length()
            );


            // ====================================================
            // VALIDATE HTML
            // ====================================================

            if (printHtml.length() < 200) {

                return UdyamVerifyResponse.error(
                        "Could not retrieve the MSME certificate from the government portal. "
                                + "Please try again or contact support."
                );
            }


            String printLower =
                    printHtml.toLowerCase();


            if (printLower.contains("login")
                    && printLower.contains("password")) {

                return UdyamVerifyResponse.error(
                        "The government portal session expired before "
                                + "we could retrieve the certificate. "
                                + "Please try again with a fresh CAPTCHA."
                );
            }


            // ====================================================
            // STEP 5 - HTML -> PDF
            // ====================================================

            byte[] pdfBytes =
                    convertHtmlToPdf(
                            printHtml,
                            request.getUdyamNumber()
                    );


            // ====================================================
            // STEP 6 - UPLOAD PDF TO S3
            // ====================================================

            String fileKey =
                    "msme/verify/"
                            + request.getUdyamNumber()
                            .toLowerCase()
                            .replace(" ", "_")
                            + "/udyam_certificate.pdf";


            String pdfUrl =
                    s3Service.uploadFile(
                            pdfBytes,
                            ContentType.APPLICATION_PDF.getMimeType(),
                            fileKey
                    );


            log.info(
                    "Udyam certificate uploaded successfully: {}",
                    fileKey
            );


            // ====================================================
            // RETURN RESPONSE
            // ====================================================

            return UdyamVerifyResponse.ok(
                    pdfUrl,
                    printHtml,
                    request.getUdyamNumber()
            );


        } catch (FssaiException e) {

            throw e;

        } catch (Exception e) {

            log.error(
                    "Failed to verify Udyam number",
                    e
            );


            throw new FssaiException(
                    "We couldn't complete the Udyam verification right now. "
                            + "Please try again in a few minutes.",
                    FailureCode.TEXTRACT_FAILURE,
                    e
            );
        }
    }


    // ============================================================
    // HTML -> PDF
    // ============================================================

    /**
     * Converts the ORIGINAL government Udyam HTML into PDF.
     *
     * IMPORTANT:
     *
     * We do NOT extract the certificate fields into a new custom
     * table anymore.
     *
     * Instead, we preserve the original government HTML tables.
     *
     * This prevents layouts such as:
     *
     * Organisation Type | Organisation Type
     * Gender             | Gender
     *
     * and keeps the original 4-column layout.
     */
    private byte[] convertHtmlToPdf(
            String rawHtml,
            String udyamNumber
    ) {

        try {

            log.info(
                    "Starting HTML -> PDF conversion for Udyam {}",
                    udyamNumber
            );


            // ====================================================
            // 1. PARSE GOVERNMENT HTML
            // ====================================================

            Document doc =
                    Jsoup.parse(
                            rawHtml,
                            BASE_URL + "/"
                    );


            /*
             * ====================================================
             * 2. REMOVE ELEMENTS THAT SHOULD NOT BE IN PDF
             * ====================================================
             */

            doc.select("script").remove();

            doc.select("noscript").remove();

            doc.select("form").unwrap();

            // Interactive controls
            doc.select("input").remove();

            doc.select("button").remove();

            doc.select("select").remove();

            doc.select("textarea").remove();


            // Common print controls
            doc.select(".btn").remove();

            doc.select(".button").remove();

            doc.select(".print").remove();

            doc.select(".print-button").remove();

            doc.select(".no-print").remove();

            doc.select(".hide-print").remove();


            /*
             * Do NOT remove all images.
             *
             * The government header/logo is usually an image.
             */


            /*
             * ====================================================
             * 3. FIX IMAGE URLS
             * ====================================================
             */

            for (Element img :
                    doc.select("img[src]")) {

                String src =
                        img.absUrl("src");


                if (src != null
                        && !src.isEmpty()) {

                    img.attr(
                            "src",
                            src
                    );
                }
            }


            /*
             * ====================================================
             * 4. FIX LINK URLS
             * ====================================================
             */

            for (Element link :
                    doc.select("link[href]")) {

                String href =
                        link.absUrl("href");


                if (href != null
                        && !href.isEmpty()) {

                    link.attr(
                            "href",
                            href
                    );
                }
            }


            /*
             * ====================================================
             * 5. REMOVE GOVERNMENT EXTERNAL CSS
             *
             * We keep the HTML structure but use CSS suitable
             * for OpenHTMLtoPDF.
             * ====================================================
             */

            doc.select(
                    "link[rel=stylesheet]"
            ).remove();


            /*
             * Remove old style blocks because they can contain
             * browser-specific CSS which OpenHTMLtoPDF does not
             * understand correctly.
             */
            doc.select("style").remove();


            /*
             * ====================================================
             * 6. NORMALIZE TABLE WIDTHS
             * ====================================================
             */

            for (Element table :
                    doc.select("table")) {

                table.removeAttr("width");

                table.attr(
                        "style",
                        "width:100%;"
                );
            }


            /*
             * Normalize cells.
             *
             * IMPORTANT:
             * We do not remove colspan/rowspan because those are
             * important for the original government layout.
             */

            for (Element cell :
                    doc.select("td, th")) {

                cell.removeAttr("width");
            }


            /*
             * ====================================================
             * 7. CLEAN ONLY PROBLEMATIC INLINE CSS
             * ====================================================
             */

            for (Element element :
                    doc.select("[style]")) {

                String styleValue =
                        element.attr("style");


                if (styleValue == null
                        || styleValue.isEmpty()) {

                    continue;
                }


                /*
                 * Remove browser-only positioning.
                 *
                 * We intentionally DO NOT remove all inline CSS.
                 */
                styleValue =
                        styleValue
                                .replaceAll(
                                        "(?i)position\\s*:\\s*fixed\\s*;?",
                                        ""
                                )
                                .replaceAll(
                                        "(?i)position\\s*:\\s*absolute\\s*;?",
                                        ""
                                )
                                .replaceAll(
                                        "(?i)float\\s*:\\s*(left|right)\\s*;?",
                                        ""
                                );


                element.attr(
                        "style",
                        styleValue
                );
            }


            /*
             * ====================================================
             * 8. ADD OUR PDF CSS
             * ====================================================
             */

            Element head =
                    doc.head();


            if (head == null) {

                head =
                        doc.prependElement("head");
            }


            Element style =
                    doc.createElement("style");


            style.append(
                    """
                    @page {
                        size: A4 portrait;
                        margin: 7mm 9mm 9mm 9mm;
                    }

                    * {
                        box-sizing: border-box;
                    }

                    html {
                        margin: 0;
                        padding: 0;
                        background: #ffffff;
                    }

                    body {
                        margin: 0;
                        padding: 0;
                        background: #ffffff;
                        color: #000000;
                        font-family: Arial, Helvetica, sans-serif;
                        font-size: 8px;
                        line-height: 1.25;
                    }

                    /*
                     * Main certificate wrapper
                     */
                    .certificate-container {
                        width: 100%;
                        margin: 0;
                        padding: 0;
                    }

                    /*
                     * Images
                     */
                    img {
                        max-width: 100%;
                        height: auto;
                    }

                    /*
                     * Tables
                     */
                    table {
                        width: 100% !important;
                        max-width: 100% !important;
                        border-collapse: collapse !important;
                        border-spacing: 0 !important;
                        margin-top: 0 !important;
                        margin-bottom: 6px !important;
                        page-break-inside: auto;
                    }

                    tr {
                        page-break-inside: avoid;
                        page-break-after: auto;
                    }

                    td,
                    th {
                        border: 1px solid #7aa5d8 !important;
                        padding: 3px 5px !important;
                        vertical-align: middle !important;
                        font-family: Arial, Helvetica, sans-serif !important;
                        font-size: 8px !important;
                        line-height: 1.25 !important;
                        color: #000000 !important;
                    }

                    th {
                        font-weight: bold !important;
                        background: #ffffff !important;
                    }

                    /*
                     * Section headings
                     */
                    h1,
                    h2,
                    h3,
                    h4,
                    h5,
                    h6 {
                        font-family: Arial, Helvetica, sans-serif !important;
                        font-size: 9px !important;
                        font-weight: bold !important;
                        color: #000000 !important;
                        margin: 7px 0 3px 0 !important;
                        padding: 4px 0 !important;
                        border-bottom: 1px solid #dddddd;
                        page-break-after: avoid;
                    }

                    /*
                     * Paragraphs
                     */
                    p {
                        margin: 2px 0 !important;
                        padding: 0 !important;
                    }

                    /*
                     * Links
                     */
                    a {
                        color: #000000 !important;
                        text-decoration: none !important;
                    }

                    /*
                     * Prevent huge text
                     */
                    span,
                    div {
                        max-width: 100%;
                    }

                    /*
                     * Long text should wrap
                     */
                    td,
                    th,
                    div,
                    span,
                    p {
                        word-wrap: break-word;
                        overflow-wrap: break-word;
                    }

                    /*
                     * Hide interactive elements
                     */
                    input,
                    button,
                    select,
                    textarea {
                        display: none !important;
                    }

                    /*
                     * Hide common navigation/footer controls
                     */
                    nav,
                    footer,
                    .no-print,
                    .print,
                    .print-button,
                    .btn,
                    .button {
                        display: none !important;
                    }

                    /*
                     * Keep the certificate header together
                     */
                    .header,
                    .certificate-header,
                    .top-header {
                        page-break-inside: avoid;
                    }

                    /*
                     * Keep address sections together where possible
                     */
                    .address,
                    .address-section {
                        page-break-inside: avoid;
                    }

                    /*
                     * NIC table
                     */
                    .nic-table {
                        width: 100% !important;
                        border-collapse: collapse !important;
                    }

                    .nic-table td,
                    .nic-table th {
                        border: 1px solid #7aa5d8 !important;
                        padding: 3px 5px !important;
                    }

                    /*
                     * Small text
                     */
                    .small,
                    .small-text {
                        font-size: 7px !important;
                    }
                    """
            );


            head.appendChild(style);


            /*
             * ====================================================
             * 9. CREATE WRAPPER
             * ====================================================
             *
             * We keep ALL original government elements.
             *
             * We are NOT rebuilding the certificate.
             */

            Element body =
                    doc.body();


            if (body == null) {

                throw new IOException(
                        "Government HTML does not contain a body element."
                );
            }


            Element wrapper =
                    doc.createElement(
                            "div"
                    );


            wrapper.attr(
                    "class",
                    "certificate-container"
            );


            /*
             * Copy current body children into wrapper.
             *
             * Use a snapshot to avoid modifying the collection
             * while iterating.
             */

            List<Element> bodyElements =
                    new ArrayList<>(
                            body.children()
                    );


            for (Element child :
                    bodyElements) {

                child.remove();

                wrapper.appendChild(
                        child
                );
            }


            body.appendChild(
                    wrapper
            );


            /*
             * ====================================================
             * 10. ENSURE UDYAM NUMBER EXISTS
             * ====================================================
             *
             * Normally the government HTML already contains it.
             *
             * If it does not, add it at the top.
             */

            String bodyText =
                    body.text();


            if (!bodyText
                    .toLowerCase()
                    .contains(
                            udyamNumber.toLowerCase()
                    )) {

                Element number =
                        doc.createElement(
                                "div"
                        );


                number.attr(
                        "style",
                        "text-align:center;" +
                                "font-weight:bold;" +
                                "font-size:9px;" +
                                "margin-bottom:5px;"
                );


                number.text(
                        "Udyam Registration Number : "
                                + udyamNumber
                );


                wrapper.insertChildren(
                        0,
                        List.of(number)
                );
            }


            /*
             * ====================================================
             * 11. CONVERT TO XHTML/XML
             * ====================================================
             */

            doc.outputSettings()
                    .syntax(
                            Document.OutputSettings.Syntax.xml
                    )
                    .charset(
                            StandardCharsets.UTF_8
                    )
                    .prettyPrint(false);


            String xhtml =
                    doc.html();


            /*
             * ====================================================
             * 12. CREATE PDF
             * ====================================================
             */

            ByteArrayOutputStream baos =
                    new ByteArrayOutputStream();


            PdfRendererBuilder builder =
                    new PdfRendererBuilder();


            builder.withHtmlContent(
                    xhtml,
                    BASE_URL + "/"
            );


            builder.toStream(
                    baos
            );


            /*
             * ====================================================
             * 13. LOAD SYSTEM FONT
             * ====================================================
             */

            try {

                String[] fontPaths = {

                        // Windows
                        "C:/Windows/Fonts/arial.ttf",

                        // Linux
                        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",

                        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",

                        "/usr/share/fonts/TTF/DejaVuSans.ttf",

                        // macOS
                        "/System/Library/Fonts/Helvetica.ttc"
                };


                for (String fontPath :
                        fontPaths) {

                    java.io.File fontFile =
                            new java.io.File(
                                    fontPath
                            );


                    if (fontFile.exists()) {

                        builder.useFont(
                                fontFile,
                                "Arial"
                        );


                        log.info(
                                "PDF font loaded: {}",
                                fontPath
                        );


                        break;
                    }
                }


            } catch (Exception fontException) {

                log.warn(
                        "Could not load system font. "
                                + "Using default PDF font.",
                        fontException
                );
            }


            /*
             * ====================================================
             * 14. RUN PDF RENDERER
             * ====================================================
             */

            builder.run();


            byte[] pdfBytes =
                    baos.toByteArray();


            /*
             * ====================================================
             * 15. VALIDATE PDF
             * ====================================================
             */

            if (pdfBytes.length < 1000) {

                throw new IOException(
                        "Generated PDF is unexpectedly small: "
                                + pdfBytes.length
                                + " bytes"
                );
            }


            /*
             * Check PDF signature.
             *
             * A real PDF normally starts with:
             *
             * %PDF-
             */

            if (pdfBytes.length >= 5) {

                String pdfHeader =
                        new String(
                                pdfBytes,
                                0,
                                5,
                                StandardCharsets.US_ASCII
                        );


                if (!pdfHeader.startsWith("%PDF-")) {

                    log.warn(
                            "Generated file does not start with %PDF-. Header={}",
                            pdfHeader
                    );
                }
            }


            log.info(
                    "Generated Udyam PDF successfully: {} bytes for {}",
                    pdfBytes.length,
                    udyamNumber
            );


            return pdfBytes;


        } catch (Exception e) {

            log.error(
                    "Failed to convert Udyam HTML to PDF for {}",
                    udyamNumber,
                    e
            );


            throw new FssaiException(
                    "The MSME certificate was verified but we couldn't generate the PDF. "
                            + "The HTML certificate has been stored. Please contact support.",
                    FailureCode.PDF_PROCESSING_ERROR,
                    e
            );
        }
    }


    // ============================================================
    // CAPTCHA IMAGE SERVING
    // ============================================================


    /**
     * Returns CAPTCHA image bytes for a session.
     */
    public byte[] getCaptchaImage(
            String sessionId
    ) {

        UdyamSession session =
                sessions.get(sessionId);


        if (session == null
                || session.captchaImage == null) {

            return null;
        }


        return session.captchaImage;
    }


    // ============================================================
    // BROWSER HEADERS
    // ============================================================


    private static void addBrowserHeaders(
            HttpGet request
    ) {

        request.setHeader(
                "Accept",
                "text/html,application/xhtml+xml," +
                        "application/xml;q=0.9," +
                        "image/avif,image/webp," +
                        "image/apng,*/*;q=0.8"
        );


        request.setHeader(
                "Accept-Language",
                "en-GB,en-US;q=0.9,en;q=0.8"
        );


        request.setHeader(
                "Connection",
                "keep-alive"
        );


        request.setHeader(
                "Referer",
                BASE_URL + "/"
        );


        request.setHeader(
                "User-Agent",
                USER_AGENT
        );


        request.setHeader(
                "Sec-Fetch-Dest",
                "document"
        );


        request.setHeader(
                "Sec-Fetch-Mode",
                "navigate"
        );


        request.setHeader(
                "Sec-Fetch-Site",
                "same-origin"
        );
    }


    private static void addBrowserHeaders(
            HttpPost request
    ) {

        request.setHeader(
                "Accept",
                "*/*"
        );


        request.setHeader(
                "Accept-Language",
                "en-GB,en-US;q=0.9,en;q=0.8"
        );


        request.setHeader(
                "Cache-Control",
                "no-cache"
        );


        request.setHeader(
                "Connection",
                "keep-alive"
        );


        request.setHeader(
                "Origin",
                BASE_URL
        );


        request.setHeader(
                "Referer",
                BASE_URL + "/"
        );


        request.setHeader(
                "User-Agent",
                USER_AGENT
        );
    }


    // ============================================================
    // HIDDEN FIELD EXTRACTION
    // ============================================================


    /**
     * Extracts an ASP.NET hidden field.
     *
     * Example:
     *
     * <input
     *      id="__VIEWSTATE"
     *      value="..."
     * />
     */
    private static String extractHiddenValue(
            String html,
            String fieldName
    ) {

        Pattern inputPattern =
                Pattern.compile(
                        "<input[^>]*>",
                        Pattern.CASE_INSENSITIVE
                                | Pattern.DOTALL
                );


        Matcher inputMatcher =
                inputPattern.matcher(html);


        while (inputMatcher.find()) {

            String input =
                    inputMatcher.group();


            if (input.contains(
                    "id=\"" + fieldName + "\""
            )) {

                Matcher valueMatcher =
                        Pattern.compile(
                                "value=\"([^\"]*)\"",
                                Pattern.CASE_INSENSITIVE
                                        | Pattern.DOTALL
                        ).matcher(input);


                if (valueMatcher.find()) {

                    return valueMatcher.group(1);
                }
            }
        }


        return "";
    }


    // ============================================================
    // SESSION CLASS
    // ============================================================


    private static class UdyamSession {

        long createdAt;

        String viewState;

        String viewStateGenerator;

        String eventValidation;

        List<Cookie> cookies;

        byte[] captchaImage;
    }
}