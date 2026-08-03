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

public class UdhyamVerifyStepX {

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

            System.out.println("Calling Udyam Verify Page...");

            try (CloseableHttpResponse response = client.execute(request)) {

                System.out.println("-----------------------------------");
                System.out.println("HTTP Status : " + response.getCode());
                System.out.println("-----------------------------------");

                System.out.println("Response Headers:");
                for (Header header : response.getHeaders()) {
                    System.out.println(header.getName() + " : " + header.getValue());
                }

                byte[] htmlBytes = EntityUtils.toByteArray(response.getEntity());
                String html = new String(htmlBytes, StandardCharsets.UTF_8);

                VIEWSTATE = extractHiddenValue(html, "__VIEWSTATE");
                VIEWSTATEGENERATOR = extractHiddenValue(html, "__VIEWSTATEGENERATOR");
                EVENTVALIDATION = extractHiddenValue(html, "__EVENTVALIDATION");

                System.out.println("VIEWSTATE Length : " + VIEWSTATE.length());
                System.out.println("VIEWSTATEGENERATOR : " + VIEWSTATEGENERATOR);
                System.out.println("EVENTVALIDATION : " + EVENTVALIDATION);

                System.out.println("-----------------------------------");
                System.out.println("HTML Length : " + html.length());
                System.out.println("-----------------------------------");

                FileWriter writer = new FileWriter("Udyam_Verify.html");
                writer.write(html);
                writer.close();
                System.out.println("HTML saved as Udyam_Verify.html");

                System.out.println("-----------------------------------");
                System.out.println("Cookies Received");
                System.out.println("-----------------------------------");
                for (Cookie cookie : cookieStore.getCookies()) {
                    System.out.println("Name : " + cookie.getName());
                    System.out.println("Value : " + cookie.getValue());
                    System.out.println("Domain : " + cookie.getDomain());
                    System.out.println("Path : " + cookie.getPath());
                    System.out.println("-----------------------------------");
                }
            }

            try {
                String captchaPath = downloadCaptcha(client, cookieStore);
                System.out.println("Captcha saved at : " + captchaPath);

                verifyUdyam(client, cookieStore);   // STEP 3

                // ===================== STEP 4 =====================
                printUdyamApplication(client, cookieStore);

            } catch (Exception e) {
                e.printStackTrace();
            }

            client.close();
            System.out.println("Completed Successfully.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String downloadCaptcha(CloseableHttpClient client, CookieStore cookieStore) throws Exception {

        System.out.println();
        System.out.println("======================================");
        System.out.println("STEP 2 - DOWNLOAD CAPTCHA");
        System.out.println("======================================");

        System.out.println("Cookies Available:");
        for (Cookie cookie : cookieStore.getCookies()) {
            System.out.println("----------------------------------");
            System.out.println("Name : " + cookie.getName());
            sessionid = cookie.getValue();
            System.out.println("Value : " + cookie.getValue());
            System.out.println("Domain : " + cookie.getDomain());
            System.out.println("Path : " + cookie.getPath());
        }

        SimpleDateFormat sdf = new SimpleDateFormat("M/d/yyyy h:mm:ss a");
        String timestamp = URLEncoder.encode(sdf.format(new Date()), "UTF-8");

        String captchaUrl = "https://www.udyamregistration.gov.in/Captcha/CaptchaControl.aspx?id=" + timestamp;

        System.out.println();
        System.out.println("Captcha URL:");
        System.out.println(captchaUrl);

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

            System.out.println();
            System.out.println("HTTP Status : " + captchaResponse.getCode());

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

            System.out.println();
            System.out.println("======================================");
            System.out.println("Captcha Downloaded Successfully");
            System.out.println("Saved At :");
            System.out.println(captchaFile.getAbsolutePath());
            System.out.println("======================================");

            return captchaFile.getAbsolutePath();
        }
    }

    public static void verifyUdyam(CloseableHttpClient client, CookieStore cookieStore) throws Exception {

        Scanner scanner = new Scanner(System.in);

        System.out.println();
        System.out.println("==============================");
        System.out.println("STEP 3 - VERIFY UDYAM");
        System.out.println("==============================");

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

        System.out.println();
        System.out.println("Submitting request...");

        try (CloseableHttpResponse response = client.execute(post)) {

            String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            System.out.println();
            System.out.println("==============================");
            System.out.println("SERVER RESPONSE (STEP 3)");
            System.out.println("==============================");
            System.out.println(result);

            // Optional: save Step-3 AJAX response
            try (FileWriter fw = new FileWriter("Udyam_Verify_Response.html")) {
                fw.write(result);
            }
            System.out.println("Step-3 response saved as Udyam_Verify_Response.html");
        }
    }

    /**
     * STEP 4 – GET PrintUdyamApplication.aspx
     * Uses the same session / cookies that were established in Steps 1-3.
     */
    public static void printUdyamApplication(CloseableHttpClient client, CookieStore cookieStore) throws Exception {

        System.out.println();
        System.out.println("======================================");
        System.out.println("STEP 4 - PRINT UDYAM APPLICATION");
        System.out.println("======================================");

        // Debug: show cookies that will be sent
        System.out.println("Cookies that will be sent automatically:");
        for (Cookie cookie : cookieStore.getCookies()) {
            System.out.println("  " + cookie.getName() + " = " + cookie.getValue());
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

            System.out.println();
            System.out.println("HTTP Status : " + printResponse.getCode());

            String printHtml = EntityUtils.toString(printResponse.getEntity(), StandardCharsets.UTF_8);

            System.out.println("HTML Length : " + printHtml.length());

            // Save the final HTML
            try (FileWriter fw = new FileWriter("PrintUdyamApplication.html")) {
                fw.write(printHtml);
            }
            System.out.println("Print page saved as PrintUdyamApplication.html");

            // Optional: print a short preview
            System.out.println();
            System.out.println("----- HTML Preview (first 800 chars) -----");
            File file = new File("/Users/India Advocacy/Downloads/msme.html");
            try (FileWriter fw = new FileWriter(file)) {
                fw.write(printHtml);
            }
            System.out.println(printHtml);
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
