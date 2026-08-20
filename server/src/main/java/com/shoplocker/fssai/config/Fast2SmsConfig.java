package com.shoplocker.fssai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuration for the Fast2SMS SMS gateway used to deliver OTPs for the
 * MSME (Udyam) number + OTP login flow.
 *
 * <p>Credentials are read from environment variables ({@code FAST2SMS_API_KEY},
 * {@code FAST2SMS_SENDER_ID}, {@code FAST2SMS_TEMPLATE_ID}) so they are never
 * hardcoded. The DLT-approved sender id and OTP template id are required for
 * OTP SMS to reach arbitrary Indian mobile numbers (trial accounts only
 * deliver to the account owner's own number).</p>
 *
 * <p>Messages are sent through the dedicated OTP endpoint
 * {@code POST /dev/otp/send} rather than the legacy bulkV2 OTP route.</p>
 */
@Configuration
public class Fast2SmsConfig {

    @Value("${fast2sms.api-key:${FAST2SMS_API_KEY:}}")
    private String apiKey;

    @Value("${fast2sms.sender-id:${FAST2SMS_SENDER_ID:}}")
    private String senderId;

    @Value("${fast2sms.template-id:${FAST2SMS_TEMPLATE_ID:}}")
    private String templateId;

    @Value("${fast2sms.otp-expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${fast2sms.otp-length:6}")
    private int otpLength;

    @Value("${fast2sms.base-url:https://www.fast2sms.com}")
    private String baseUrl;

    @Bean
    public RestClient fast2SmsRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public String getApiKey() { return apiKey; }
    public String getSenderId() { return senderId; }
    public String getTemplateId() { return templateId; }
    public int getOtpExpiryMinutes() { return otpExpiryMinutes; }
    public int getOtpLength() { return otpLength; }
    public String getBaseUrl() { return baseUrl; }
}