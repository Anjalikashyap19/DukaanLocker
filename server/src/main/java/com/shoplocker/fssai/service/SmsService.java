package com.shoplocker.fssai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplocker.fssai.config.Fast2SmsConfig;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.exception.OtpRateLimitedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends OTP SMS through Fast2SMS.
 *
 * <p>Two distinct routes are used, because the two flows are approved as two
 * different DLT content templates:</p>
 * <ul>
 *   <li>{@link #sendOtp} — MSME login, via the dedicated Smart-OTP endpoint
 *       {@code POST /dev/otp/send} with the Smart-OTP {@code otp_id}.</li>
 *   <li>{@link #sendPasswordResetOtp} — forgot password, via the DLT route
 *       {@code POST /dev/bulkV2} ({@code route=dlt}) addressed by DLT message id +
 *       principal entity id + sender id.</li>
 * </ul>
 *
 * <p>Unlike the legacy {@code /dev/bulkV2?route=otp} form endpoint, both take a
 * JSON body and an {@code authorization} header, and return a {@code status_code}
 * that pinpoints the failure reason (KYC not done, wallet not topped up, template
 * not approved, spam gate, etc.) so the cause is never swallowed.</p>
 *
 * <p>If the API key is not configured (local dev), the send is skipped (logged)
 * so the rest of the flow stays exercisable; it does NOT throw, to avoid hard
 * failures when SMS is not provisioned.</p>
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final RestClient restClient;
    private final Fast2SmsConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SmsService(@Qualifier("fast2SmsRestClient") RestClient fast2SmsRestClient, Fast2SmsConfig config) {
        this.restClient = fast2SmsRestClient;
        this.config = config;
    }

    /** Sends the MSME-login OTP SMS to the given mobile. Throws {@link FailureCode#SMS_FAILURE} on gateway rejection. */
    public void sendOtp(String mobile, String otp) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.warn("Fast2SMS API key not configured — DEV MODE: OTP for {} is {}", mask(mobile), otp);
            return;
        }
        if (config.getTemplateId() == null || config.getTemplateId().isBlank()) {
            log.warn("Fast2SMS OTP template id not configured — DEV MODE: OTP for {} is {}", mask(mobile), otp);
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mobile", mobile);
        body.put("otp_id", config.getTemplateId());
        body.put("otp_expiry", config.getOtpExpiryMinutes());
        body.put("otp_length", config.getOtpLength());
        body.put("otp", otp);
        putIfPresent(body, "sender_id", config.getSenderId());

        send("/dev/otp/send", body, "login");
    }

    /**
     * Sends the forgot-password OTP SMS over Fast2SMS's <b>DLT route</b>
     * ({@code POST /dev/bulkV2}, {@code route=dlt}) rather than the Smart-OTP
     * endpoint used by {@link #sendOtp}.
     *
     * <p>The DLT route addresses a message by its approved
     * {@code message} (content-template) id together with the {@code entity_id}
     * (principal entity) and {@code sender_id} (header), and fills the template's
     * {@code {#var#}} placeholders from {@code variables_values}. The OTP is that
     * first (and only) variable.</p>
     *
     * <p>Unlike {@link #sendOtp}, a missing DLT credential is a hard failure rather
     * than a silent dev-mode skip: the API key being present means this is a real
     * deployment, and swallowing the send there would tell the user "OTP sent"
     * while nothing was delivered.</p>
     */
    public void sendPasswordResetOtp(String mobile, String otp) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.warn("Fast2SMS API key not configured — DEV MODE: password-reset OTP for {} is {}", mask(mobile), otp);
            return;
        }
        if (config.getResetMessageId() == null || config.getResetMessageId().isBlank()
                || config.getResetEntityId() == null || config.getResetEntityId().isBlank()) {
            log.error("Fast2SMS password-reset DLT credentials not configured "
                    + "(FAST2SMS_RESET_MESSAGE_ID / FAST2SMS_RESET_ENTITY_ID)");
            throw new FssaiException(
                    "Password reset SMS is not configured on the server. Please contact support.",
                    FailureCode.SMS_FAILURE);
        }
        if (config.getSenderId() == null || config.getSenderId().isBlank()) {
            log.error("Fast2SMS sender id not configured (FAST2SMS_SENDER_ID) — required by the DLT route");
            throw new FssaiException(
                    "Password reset SMS is not configured on the server. Please contact support.",
                    FailureCode.SMS_FAILURE);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("route", "dlt");
        body.put("sender_id", config.getSenderId());
        body.put("message", config.getResetMessageId());
        body.put("entity_id", config.getResetEntityId());
        // The DLT template's variables, in order, pipe-separated. This template
        // carries exactly one: the OTP.
        body.put("variables_values", otp);
        body.put("numbers", mobile);

        send("/dev/bulkV2", body, "password-reset");
    }

    private void send(String uri, Map<String, Object> body, String purpose) {
        try {
            String jsonBody = objectMapper.writeValueAsString(body);

            // Use exchange() instead of retrieve() so 4xx/5xx bodies (which carry
            // the real Fast2SMS status_code) are read instead of thrown away.
            String rawResponse = restClient.post()
                    .uri(uri)
                    .header("authorization", config.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .exchange((request, response) -> {
                        byte[] bytes = response.getBody().readAllBytes();
                        return new String(bytes, StandardCharsets.UTF_8);
                    });

            log.info("Fast2SMS {} ({}) response for {}: {}", uri, purpose, mask(
                    (String) body.getOrDefault("numbers", body.getOrDefault("mobile", ""))), rawResponse);

            JsonNode node = (rawResponse == null || rawResponse.isBlank())
                    ? null : objectMapper.readTree(rawResponse);

            if (node == null || !node.path("return").asBoolean(false)) {
                int statusCode = node != null ? node.path("status_code").asInt(0) : 0;
                String f2sMessage = node != null ? messageOf(node) : "";
                log.error("Fast2SMS OTP send rejected ({}): status_code={} message={}", purpose, statusCode, f2sMessage);
                throw toSendException(statusCode, f2sMessage);
            }

            log.info("Fast2SMS OTP ({}) accepted (request_id={})",
                    purpose, node.path("request_id").asText(""));
        } catch (FssaiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fast2SMS OTP send error ({}): {}", purpose, e.getMessage(), e);
            throw new FssaiException(
                    "We couldn't send the OTP right now. Please try again.",
                    FailureCode.SMS_FAILURE);
        }
    }

    /**
     * Reads the gateway's {@code message} field. It is an array on the DLT route
     * (e.g. {@code ["Message sent successfully"]}) and a plain string on the
     * Smart-OTP route and on every error body.
     */
    private static String messageOf(JsonNode node) {
        JsonNode message = node.get("message");
        if (message == null || message.isNull()) return "";
        if (message.isTextual()) return message.asText();
        if (message.isArray()) {
            StringBuilder joined = new StringBuilder();
            for (JsonNode item : message) {
                if (joined.length() > 0) joined.append(" ");
                joined.append(item.asText());
            }
            return joined.toString();
        }
        return message.asText("");
    }

    private static void putIfPresent(Map<String, Object> body, String key, String value) {
        if (value != null && !value.isBlank()) body.put(key, value);
    }

    private static String mask(String mobile) {
        if (mobile == null || mobile.length() < 4) return "****";
        return "*******" + mobile.substring(mobile.length() - 3);
    }

    /**
     * Matches the gateway's own resend-throttle wording, e.g.
     * "Please wait 8 seconds before requesting a new OTP."
     */
    private static final Pattern RESEND_WAIT_PATTERN =
            Pattern.compile("please wait (\\d+) second", Pattern.CASE_INSENSITIVE);

    /**
     * Converts a gateway rejection into the most accurate exception we can express.
     *
     * <p>Fast2SMS enforces its own per-mobile resend cooldown and rejects an overlapping
     * send with a 400. Our own cooldown window opens a moment before the gateway sees the
     * request, so a user tapping near the end of the window can legitimately clear our
     * check and still be rejected by the gateway. Reporting that as a 502
     * {@code sms_failure} would tell the user "something went wrong, try again" with no
     * countdown, which is both wrong and unactionable — so it is translated into the same
     * 429 the rate limiter emits, letting the client start a timer.</p>
     */
    private static RuntimeException toSendException(int statusCode, String f2sMessage) {
        Matcher matcher = RESEND_WAIT_PATTERN.matcher(f2sMessage == null ? "" : f2sMessage);
        if (statusCode == 400 && matcher.find()) {
            int wait = Integer.parseInt(matcher.group(1));
            return new OtpRateLimitedException(
                    "Please wait " + wait + " seconds before requesting a new OTP.", wait, 0, false);
        }
        return new FssaiException(
                "We couldn't send the OTP right now. Fast2SMS error " + statusCode
                        + (f2sMessage == null || f2sMessage.isBlank() ? "." : ": " + f2sMessage + "."),
                FailureCode.SMS_FAILURE);
    }
}