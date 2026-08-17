package com.shoplocker.fssai.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplocker.fssai.config.Fast2SmsConfig;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Sends SMS via the Fast2SMS gateway (https://www.fast2sms.com/dev/bulkV2).
 *
 * <p>Used for OTP delivery in the MSME login flow. The OTP route requires a
 * DLT-approved sender id + template — the template must contain a placeholder
 * that Fast2SMS substitutes via {@code variables_values}.</p>
 *
 * <p>If no API key is configured (local dev), the send is skipped (logged) so
 * the rest of the flow stays exercisable; it does NOT throw, to avoid hard
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

    /** Sends an OTP SMS to the given mobile. Throws {@link FailureCode#SMS_FAILURE} on gateway rejection. */
    public void sendOtp(String mobile, String otp) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.warn("Fast2SMS API key not configured — SKIPPING OTP SMS to {} (dev mode)", mask(mobile));
            return;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("route", config.getRoute());
        form.add("sender_id", config.getSenderId());
        form.add("template_id", config.getTemplateId());
        form.add("variables_values", otp);
        form.add("numbers", mobile);
        form.add("language", "english");
        form.add("flash", "0");

        try {
            String body = restClient.post()
                    .header("authorization", config.getApiKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            Fast2SmsResponse response = objectMapper.readValue(body, Fast2SmsResponse.class);
            if (!Boolean.TRUE.equals(response.returnValue)) {
                log.error("Fast2SMS OTP send rejected: {}", body);
                throw new FssaiException(
                        "We couldn't send the OTP right now. Please try again.",
                        FailureCode.SMS_FAILURE);
            }
            log.info("Fast2SMS OTP sent to {}", mask(mobile));
        } catch (FssaiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fast2SMS OTP send error: {}", e.getMessage());
            throw new FssaiException(
                    "We couldn't send the OTP right now. Please try again.",
                    FailureCode.SMS_FAILURE);
        }
    }

    private static String mask(String mobile) {
        if (mobile == null || mobile.length() < 4) return "****";
        return "*******" + mobile.substring(mobile.length() - 3);
    }

    /** Fast2SMS JSON envelope (only the fields we read). */
    public static class Fast2SmsResponse {
        @JsonProperty("return")
        public Boolean returnValue;

        @JsonProperty("request_id")
        public String requestId;

        @JsonProperty("message")
        public java.util.List<String> message;

        @JsonProperty("error")
        public java.util.List<String> error;
    }
}
