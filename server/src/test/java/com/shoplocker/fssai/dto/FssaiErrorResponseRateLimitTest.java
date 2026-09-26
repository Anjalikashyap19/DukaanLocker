package com.shoplocker.fssai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shoplocker.fssai.exception.OtpRateLimitedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the rate-limit metadata on the error body.
 *
 * <p>These fields are the only thing the client has to render a countdown, and they were
 * silently dropped in production because {@link FssaiErrorResponse.FssaiErrorResponseBuilder}
 * built the response through a constructor that never received them — every field came back
 * null and the JSON omitted them entirely.</p>
 */
class FssaiErrorResponseRateLimitTest {

    // Spring Boot auto-registers the JavaTimeModule on its managed ObjectMapper, which is
    // what serializes LocalDateTime in the real API; a bare mapper would reject the field.
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /** Mirrors what GlobalExceptionHandler does for a rate-limited request. */
    private FssaiErrorResponse buildFor(OtpRateLimitedException cause) {
        FssaiErrorResponse.FssaiErrorResponseBuilder builder = FssaiErrorResponse.builder()
                .status(429)
                .code(cause.getFailureCode().getCode())
                .message(cause.getMessage());
        cause.enrich(builder);
        return builder.build();
    }

    @Test
    @DisplayName("enrich() metadata survives the builder")
    void enrichSurvivesBuilder() {
        FssaiErrorResponse response = buildFor(new OtpRateLimitedException(
                "Please wait 119 seconds before requesting a new OTP.", 119, 4, false));

        assertThat(response.getRetryAfterSeconds()).isEqualTo(119);
        assertThat(response.getRemainingSends()).isEqualTo(4);
        assertThat(response.getOtpLocked()).isFalse();
    }

    @Test
    @DisplayName("the lockout state is carried explicitly rather than inferred")
    void lockoutMetadataSurvivesBuilder() {
        FssaiErrorResponse response = buildFor(new OtpRateLimitedException(
                "Too many OTP requests. Please try again in 10 minutes.", 600, 0, true));

        assertThat(response.getOtpLocked()).isTrue();
        assertThat(response.getRetryAfterSeconds()).isEqualTo(600);
        assertThat(response.getRemainingSends()).isZero();
    }

    @Test
    @DisplayName("the metadata is actually serialized into the response body")
    void metadataIsSerialized() throws Exception {
        String json = mapper.writeValueAsString(buildFor(new OtpRateLimitedException(
                "Please wait 42 seconds before requesting a new OTP.", 42, 2, false)));

        assertThat(json).contains("\"retryAfterSeconds\":42");
        assertThat(json).contains("\"remainingSends\":2");
        assertThat(json).contains("\"otpLocked\":false");
    }
}
