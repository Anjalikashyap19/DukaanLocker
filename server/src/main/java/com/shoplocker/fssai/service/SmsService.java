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
 * Sends OTP SMS via the Fast2SMS dedicated OTP endpoint
 * ({@code POST https://www.fast2sms.com/dev/otp/send}).
 *
 * <p>Used for OTP delivery in the MSME login flow. Unlike the legacy
 * {@code /dev/bulkV2?route=otp} form endpoint, this API takes a JSON body with
 * the DLT-approved OTP template id ({@code otp_id}), lets us pass our own OTP
 * value, and returns a {@code status_code} that pinpoints the failure reason
 * (KYC not done, wallet not topped up, template not approved, spam gate,
 * etc.) so the cause is never swallowed.</p>
 *
 * <p>If the API key or template id is not configured (local dev), the send is
 * skipped (logged) so the rest of the flow stays exercisable; it does NOT
 * throw, to avoid hard failures when SMS is not provisioned.</p>
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

    /** Sends an OTP SMS to the given mobile. Throws {@link FailureCode#SMS_FAILURE} on gateway rejection. */
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
        if (config.getSenderId() != null && !config.getSenderId().isBlank()) {
            body.put("sender_id", config.getSenderId());
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(body);

            // Use exchange() instead of retrieve() so 4xx/5xx bodies (which carry
            // the real Fast2SMS status_code) are read instead of thrown away.
            String rawResponse = restClient.post()
                    .uri("/dev/otp/send")
                    .header("authorization", config.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .exchange((request, response) -> {
                        byte[] bytes = response.getBody().readAllBytes();
                        return new String(bytes, StandardCharsets.UTF_8);
                    });

            log.info("Fast2SMS /dev/otp/send response for {}: {}", mask(mobile), rawResponse);

            JsonNode node = (rawResponse == null || rawResponse.isBlank())
                    ? null : objectMapper.readTree(rawResponse);

            if (node == null || !node.path("return").asBoolean(false)) {
                int statusCode = node != null ? node.path("status_code").asInt(0) : 0;
                String f2sMessage = node != null ? node.path("message").asText("") : "";
                log.error("Fast2SMS OTP send rejected: status_code={} message={}", statusCode, f2sMessage);
                throw toSendException(statusCode, f2sMessage);
            }

            log.info("Fast2SMS OTP sent to {} (request_id={})",
                    mask(mobile), node.path("request_id").asText(""));
        } catch (FssaiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fast2SMS OTP send error: {}", e.getMessage(), e);
            throw new FssaiException(
                    "We couldn't send the OTP right now. Please try again.",
                    FailureCode.SMS_FAILURE);
        }
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