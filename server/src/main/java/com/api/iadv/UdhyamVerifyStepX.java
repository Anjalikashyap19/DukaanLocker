package com.api.iadv;


import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdhyamVerifyStepX {

    private static final Logger logger = LoggerFactory.getLogger(UdhyamVerifyStepX.class);

    private static String VIEWSTATE;
    private static String VIEWSTATEGENERATOR;
    private static String EVENTVALIDATION;
    private static String sessionid;

    public static void main(String[] args) {

        CookieStore cookieStore = new BasicCookieStore();

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(30000))
                .setResponseTimeout(Timeout.ofMilliseconds(30000))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(30000))
                .build();

        CloseableHttpClient client = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .setDefaultRequestConfig(config)
                .build();

        try {

            // ===================== STEP 1 =====================
            HttpGet request = new HttpGet("https://www.udyamregistration.gov.in/Udyam_Verify.aspx");

            request.setHeader("Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
            request.setHeader("Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8");
            request.setHeader("Cache-Control", "max-age=0");
            request.setHeader("Connection", "keep-alive");
            request.setHeader("Referer", "https://www.udyamregistration.gov.in/");
            request.setHeader("Upgrade-Insecure-Requests", "1");
            request.setHeader("User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36");
            request.setHeader("Sec-Fetch-Dest", "document");
            request.setHeader("Sec-Fetch-Mode", "navigate");
            request.setHeader("Sec-Fetch-Site", "same-origin");
            request.setHeader("Sec-Fetch-User", "?1");
            request.setHeader("sec-ch-ua",
                    "\"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"150\", \"Google Chrome\";v=\"150\"");
            request.setHeader("sec-ch-ua-mobile", "?0");
            request.setHeader("sec-ch-ua-platform", "\"macOS\"");

            logger.info("Calling Udyam Verify Page...");

            try (CloseableHttpResponse response = client.execute(request)) {

                logger.info("-----------------------------------");
                logger.info("HTTP Status : {}", response.getCode());
                logger.info("-----------------------------------");

                logger.info("Response Headers:");
                for (Header header : response.getHeaders()) {
                    logger.info("{} : {}", header.getName(), header.getValue());
                }

                byte[] htmlBytes = EntityUtils.toByteArray(response.getEntity());
                String html = new String(htmlBytes, StandardCharsets.UTF_8);

                VIEWSTATE = extractHiddenValue(html, "__VIEWSTATE");
                VIEWSTATEGENERATOR = extractHiddenValue(html, "__VIEWSTATEGENERATOR");
                EVENTVALIDATION = extractHiddenValue(html, "__EVENTVALIDATION");

                logger.info("VIEWSTATE Length : {}", VIEWSTATE.length());
                logger.info("VIEWSTATEGENERATOR : {}", VIEWSTATEGENERATOR);
                logger.info("EVENTVALIDATION : {}", EVENTVALIDATION);

                logger.info("-----------------------------------");
                logger.info("HTML Length : {}", html.length());
                logger.info("-----------------------------------");

                FileWriter writer = new FileWriter("Udyam_Verify.html");
                writer.write(html);
                writer.close();
                logger.info("HTML saved as Udyam_Verify.html");

                logger.info("-----------------------------------");
                logger.info("Cookies Received");
                logger.info("-----------------------------------");
                for (Cookie cookie : cookieStore.getCookies()) {
                    logger.info("Name : {}", cookie.getName());
                    logger.info("Value : {}", cookie.getValue());
                    logger.info("Domain : {}", cookie.getDomain());
                    logger.info("Path : {}", cookie.getPath());
                    logger.info("-----------------------------------");
                }
            }

            try {
                String captchaPath = downloadCaptcha(client, cookieStore);
                logger.info("Captcha saved at : {}", captchaPath);

                verifyUdyam(client, cookieStore);   // STEP 3

                // ===================== STEP 4 =====================
                printUdyamApplication(client, cookieStore);

            } catch (Exception e) {
                logger.error("Error during Udyam verification process", e);
            }

            client.close();
            logger.info("Completed Successfully.");

        } catch (IOException e) {
            logger.error("IO Error during Udyam verification", e);
        }
    }

    public static String downloadCaptcha(CloseableHttpClient client, CookieStore cookieStore) throws Exception {

        logger.info("");
        logger.info("======================================");
        logger.info("STEP 2 - DOWNLOAD CAPTCHA");
        logger.info("======================================");

        logger.info("Cookies Available:");
        for (Cookie cookie : cookieStore.getCookies()) {
            logger.info("----------------------------------");
            logger.info("Name : {}", cookie.getName());
            sessionid = cookie.getValue();
            logger.info("Value : {}", cookie.getValue());
            logger.info("Domain : {}", cookie.getDomain());
            logger.info("Path : {}", cookie.getPath());
        }

        SimpleDateFormat sdf = new SimpleDateFormat("M/d/yyyy h:mm:ss a");
        String timestamp = URLEncoder.encode(sdf.format(new Date()), "UTF-8");

        String captchaUrl = "https://www.udyamregistration.gov.in/Captcha/CaptchaControl.aspx?id=" + timestamp;

        logger.info("");
        logger.info("Captcha URL:");
        logger.info(captchaUrl);

        HttpGet captchaRequest = new HttpGet(captchaUrl);

        captchaRequest.setHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
        captchaRequest.setHeader("Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8");
        captchaRequest.setHeader("Connection", "keep-alive");
        captchaRequest.setHeader("Referer", "https://www.udyamregistration.gov.in/");
        captchaRequest.setHeader("Sec-Fetch-Dest", "image");
        captchaRequest.setHeader("Sec-Fetch-Mode", "no-cors");
        captchaRequest.setHeader("Sec-Fetch-Site", "same-origin");
        captchaRequest.setHeader("User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36");
        captchaRequest.setHeader("sec-ch-ua",
                "\"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"150\", \"Google Chrome\";v=\"150\"");
        captchaRequest.setHeader("sec-ch-ua-mobile", "?0");
        captchaRequest.setHeader("sec-ch-ua-platform", "\"macOS\"");

        try (CloseableHttpResponse captchaResponse = client.execute(captchaRequest)) {

            logger.info("");
            logger.info("HTTP Status : {}", captchaResponse.getCode());

            if (captchaResponse.getCode() != 200) {
                throw new RuntimeException("Unable to download captcha.");
            }

            String downloadPath = System.getProperty("user.home") + File.separator + "Downloads";
            File downloadFolder = new File(downloadPath);
            if (!downloadFolder.exists()) {
                downloadFolder.mkdirs();
            }

            File captchaFile = new File(downloadFolder, "captcha.png");

            try (InputStream inputStream = captchaResponse.getEntity().getContent();
                 FileOutputStream outputStream = new FileOutputStream(captchaFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }

            logger.info("");
            logger.info("======================================");
            logger.info("Captcha Downloaded Successfully");
            logger.info("Saved At :");
            logger.info(captchaFile.getAbsolutePath());
            logger.info("======================================");

            return captchaFile.getAbsolutePath();
        }
    }

    public static void verifyUdyam(CloseableHttpClient client, CookieStore cookieStore) throws Exception {

        Scanner scanner = new Scanner(System.in);

        logger.info("");
        logger.info("==============================");
        logger.info("STEP 3 - VERIFY UDYAM");
        logger.info("==============================");

        System.out.print("Enter Udyam Number : ");
        String udyamNo = scanner.nextLine();

        System.out.print("Enter Captcha : ");
        String captcha = scanner.nextLine();

        HttpPost post = new HttpPost("https://www.udyamregistration.gov.in/Udyam_Verify.aspx");

        post.setHeader("Accept", "*/*");
        post.setHeader("Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8");
        post.setHeader("Cache-Control", "no-cache");
        post.setHeader("Connection", "keep-alive");
        post.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        post.setHeader("Origin", "https://www.udyamregistration.gov.in");
        post.setHeader("Referer", "https://www.udyamregistration.gov.in/");
        post.setHeader("X-MicrosoftAjax", "Delta=true");
        post.setHeader("X-Requested-With", "XMLHttpRequest");
        post.setHeader("User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36");

        List<NameValuePair> params = new ArrayList<>();

        params.add(new BasicNameValuePair(
                "ctl00$sm",
                "ctl00$ContentPlaceHolder1$UpdatePaneldd1|ctl00$ContentPlaceHolder1$btnVerify"));
        params.add(new BasicNameValuePair("__EVENTTARGET", ""));
        params.add(new BasicNameValuePair("__EVENTARGUMENT", ""));
        params.add(new BasicNameValuePair("__VIEWSTATE", VIEWSTATE));
        params.add(new BasicNameValuePair("__VIEWSTATEGENERATOR", VIEWSTATEGENERATOR));
        params.add(new BasicNameValuePair("__VIEWSTATEENCRYPTED", ""));
        params.add(new BasicNameValuePair("ctl00$ContentPlaceHolder1$hdnSetPassword", ""));
        params.add(new BasicNameValuePair("ctl00$ContentPlaceHolder1$txtUdyamNo", udyamNo));
        params.add(new BasicNameValuePair("ctl00$ContentPlaceHolder1$txtCaptcha", captcha));
        params.add(new BasicNameValuePair("__ASYNCPOST", "true"));
        params.add(new BasicNameValuePair("ctl00$ContentPlaceHolder1$btnVerify", "Verify"));

        if (EVENTVALIDATION != null && EVENTVALIDATION.length() > 0) {
            params.add(new BasicNameValuePair("__EVENTVALIDATION", EVENTVALIDATION));
        }

        post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));

        logger.info("");
        logger.info("Submitting request...");

        try (CloseableHttpResponse response = client.execute(post)) {

            String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            logger.info("");
            logger.info("==============================");
            logger.info("SERVER RESPONSE (STEP 3)");
            logger.info("==============================");
            logger.info(result);

            // Optional: save Step-3 AJAX response
            try (FileWriter fw = new FileWriter("Udyam_Verify_Response.html")) {
                fw.write(result);
            }
            logger.info("Step-3 response saved as Udyam_Verify_Response.html");
        }
    }

    /**
     * STEP 4 – GET PrintUdyamApplication.aspx
     * Uses the same session / cookies that were established in Steps 1-3.
     */
    public static void printUdyamApplication(CloseableHttpClient client, CookieStore cookieStore) throws Exception {

        logger.info("");
        logger.info("======================================");
        logger.info("STEP 4 - PRINT UDYAM APPLICATION");
        logger.info("======================================");

        // Debug: show cookies that will be sent
        logger.info("Cookies that will be sent automatically:");
        for (Cookie cookie : cookieStore.getCookies()) {
            logger.info("  {} = {}", cookie.getName(), cookie.getValue());
        }

        HttpGet printRequest = new HttpGet("https://www.udyamregistration.gov.in/PrintUdyamApplication.aspx");

        // Headers matching the curl you provided
        printRequest.setHeader("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        printRequest.setHeader("Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8");
        printRequest.setHeader("Connection", "keep-alive");
        printRequest.setHeader("Referer", "https://www.udyamregistration.gov.in/");
        printRequest.setHeader("Sec-Fetch-Dest", "document");
        printRequest.setHeader("Sec-Fetch-Mode", "navigate");
        printRequest.setHeader("Sec-Fetch-Site", "same-origin");
        printRequest.setHeader("Sec-Fetch-User", "?1");
        printRequest.setHeader("Upgrade-Insecure-Requests", "1");
        printRequest.setHeader("User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36");
        printRequest.setHeader("sec-ch-ua",
                "\"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"150\", \"Google Chrome\";v=\"150\"");
        printRequest.setHeader("sec-ch-ua-mobile", "?0");
        printRequest.setHeader("sec-ch-ua-platform", "\"macOS\"");

        try (CloseableHttpResponse printResponse = client.execute(printRequest)) {

            logger.info("");
            logger.info("HTTP Status : {}", printResponse.getCode());

            String printHtml = EntityUtils.toString(printResponse.getEntity(), StandardCharsets.UTF_8);

            logger.info("HTML Length : {}", printHtml.length());

            // Save the final HTML
            try (FileWriter fw = new FileWriter("PrintUdyamApplication.html")) {
                fw.write(printHtml);
            }
            logger.info("Print page saved as PrintUdyamApplication.html");

            // Optional: log a short preview
            logger.info("");
            logger.info("----- HTML Preview (first 800 chars) -----");
            File file = new File("/Users/India Advocacy/Downloads/msme.html");
            try (FileWriter fw = new FileWriter(file)) {
                fw.write(printHtml);
            }
            logger.info(printHtml);
        }
    }

    private static String extractHiddenValue(String html, String fieldName) {

        Pattern inputPattern = Pattern.compile(
                "<input[^>]*>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Matcher inputMatcher = inputPattern.matcher(html);

        while (inputMatcher.find()) {
            String input = inputMatcher.group();

            if (input.contains("id=\"" + fieldName + "\"")) {
                Matcher valueMatcher = Pattern.compile(
                                "value=\"([^\"]*)\"",
                                Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                        .matcher(input);

                if (valueMatcher.find()) {
                    return valueMatcher.group(1);
                }
            }
        }
        return "";
    }
}
