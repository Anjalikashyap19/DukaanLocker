package com.shoplocker.fssai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplocker.fssai.entity.Role;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.repository.DeviceTokenRepository;
import com.shoplocker.fssai.repository.DocumentRepository;
import com.shoplocker.fssai.repository.ManagerShopAssignmentRepository;
import com.shoplocker.fssai.repository.NotificationRepository;
import com.shoplocker.fssai.repository.OtpChallengeRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.repository.UserRepository;
import com.shoplocker.fssai.service.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Forgot-password flow: request OTP → verify OTP → new password.
 *
 * <p>Fast2SMS is not configured in tests (no API key), so the OTP is echoed back
 * in the response message — {@link com.shoplocker.fssai.service.OtpService}'s
 * dev mode — which is exactly what lets these tests drive the whole flow
 * end-to-end without an SMS gateway.</p>
 *
 * <p>The reset is bound to an email/mobile pair: the email typed on the sign-in
 * form must belong to the same owner account as the mobile that receives the OTP,
 * so an unregistered address cannot start (or finish) a reset.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Forgot password")
class ForgotPasswordIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private OtpChallengeRepository otpChallengeRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private DeviceTokenRepository deviceTokenRepository;
    @Autowired private ManagerShopAssignmentRepository assignmentRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptService loginAttemptService;

    private static final String GENERIC_MESSAGE =
            "If this mobile number is registered to an owner account, an OTP has been sent to it.";

    /** MockMvc reports every caller as coming from this address. */
    private static final String HINT_KEY = "hint:ip:127.0.0.1";

    /** Deletes in FK order — registration and login create notifications that
     *  reference the user. */
    @BeforeEach
    void clean() {
        deviceTokenRepository.deleteAll();
        notificationRepository.deleteAll();
        assignmentRepository.deleteAll();
        documentRepository.deleteAll();
        shopRepository.deleteAll();
        userRepository.deleteAll();
        otpChallengeRepository.deleteAll();
        // The hint throttle is per IP and MockMvc always looks like the same caller,
        // so a test that exhausts the budget would starve the ones after it.
        loginAttemptService.reset(HINT_KEY);
    }

    // ---------------------------------------------------------------- helpers

    /** The email {@link #registerOwner(String)} assigns to an account. */
    private static String ownerEmail(String mobile) {
        return "owner" + mobile + "@example.com";
    }

    /** Registers an ADMIN through the public endpoint so the account is realistic. */
    private void registerOwner(String mobile) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Owner %s",
                                  "mobileNumber": "%s",
                                  "emailId": "%s",
                                  "password": "Strong@123"
                                }""".formatted(mobile, mobile, ownerEmail(mobile))))
                .andExpect(status().isCreated());
    }

    private String requestOtp(String email, String mobile) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailId": "%s", "mobileNumber": "%s"}"""
                                .formatted(email, mobile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("your OTP is")))
                .andReturn();

        String message = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("message").asText();
        Matcher matcher = Pattern.compile("your OTP is (\\d+)").matcher(message);
        assertThat(matcher.find()).as("dev-mode message carries the OTP: %s", message).isTrue();
        return matcher.group(1);
    }

    private void resetPassword(String email, String mobile, String otp, String password) throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "emailId": "%s",
                                  "mobileNumber": "%s",
                                  "otp": "%s",
                                  "password": "%s"
                                }""".formatted(email, mobile, otp, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("owner can reset their password and log in with it")
    void ownerResetsPasswordThenLogsIn() throws Exception {
        String mobile = "9000000001";
        String email = ownerEmail(mobile);
        String newPassword = "Brand@New1";
        registerOwner(mobile);

        String otp = requestOtp(email, mobile);
        resetPassword(email, mobile, otp, newPassword);

        User user = userRepository.findByMobileNumber(mobile).orElseThrow();
        assertThat(passwordEncoder.matches(newPassword, user.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("Strong@123", user.getPassword())).isFalse();

        // The new password works, the old one does not.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailId": "%s", "password": "%s"}"""
                                .formatted(email, newPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailId": "%s", "password": "Strong@123"}"""
                                .formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the OTP is single-use")
    void otpCannotBeReused() throws Exception {
        String mobile = "9000000002";
        String email = ownerEmail(mobile);
        registerOwner(mobile);

        String otp = requestOtp(email, mobile);
        resetPassword(email, mobile, otp, "Brand@New1");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "emailId": "%s",
                                  "mobileNumber": "%s",
                                  "otp": "%s",
                                  "password": "Another@Pass1"
                                }""".formatted(email, mobile, otp)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_otp"));
    }

    @Test
    @DisplayName("a wrong OTP is rejected and the account is untouched")
    void wrongOtpIsRejected() throws Exception {
        String mobile = "9000000003";
        registerOwner(mobile);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "emailId": "%s",
                                  "mobileNumber": "%s",
                                  "otp": "000000",
                                  "password": "Brand@New1"
                                }""".formatted(ownerEmail(mobile), mobile)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_otp"));

        User user = userRepository.findByMobileNumber(mobile).orElseThrow();
        assertThat(passwordEncoder.matches("Strong@123", user.getPassword())).isTrue();
    }

    @Test
    @DisplayName("unknown email and mobile get the same generic response as a real pair")
    void unknownMobileIsNotEnumerated() throws Exception {
        // Not registered: no OTP may be echoed, and the wording must be identical
        // to the response a real owner account receives.
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailId": "nobody@example.com", "mobileNumber": "9000000004"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE))
                .andExpect(jsonPath("$.requestId").doesNotExist());

        assertThat(otpChallengeRepository.count()).isZero();
    }

    @Test
    @DisplayName("an unregistered email cannot start a reset even for a real mobile")
    void unregisteredEmailCannotStartReset() throws Exception {
        String mobile = "9000000010";
        registerOwner(mobile);

        // The mobile is real, the email is not: the pair is what must match.
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailId": "dummy@example.com", "mobileNumber": "%s"}"""
                                .formatted(mobile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE))
                .andExpect(jsonPath("$.requestId").doesNotExist());

        assertThat(otpChallengeRepository.count()).isZero();
    }

    @Test
    @DisplayName("an email cannot claim a reset for someone else's mobile")
    void resetIsScopedToTheAccountsOwnMobile() throws Exception {
        String ownerMobile = "9000000011";
        String otherMobile = "9000000012";
        registerOwner(ownerMobile);
        registerOwner(otherMobile);

        // A valid OTP for the first account, presented with the second account's
        // email: the OTP itself is fine, the identity claim is not.
        String otp = requestOtp(ownerEmail(ownerMobile), ownerMobile);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "emailId": "%s",
                                  "mobileNumber": "%s",
                                  "otp": "%s",
                                  "password": "Brand@New1"
                                }""".formatted(ownerEmail(otherMobile), ownerMobile, otp)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("password_reset_not_allowed"));

        User user = userRepository.findByMobileNumber(ownerMobile).orElseThrow();
        assertThat(passwordEncoder.matches("Strong@123", user.getPassword())).isTrue();
    }

    @Test
    @DisplayName("a registered manager cannot self-service reset (owner accounts only)")
    void managerCannotReset() throws Exception {
        User manager = new User();
        manager.setUserName("Ravi Manager");
        manager.setMobileNumber("9000000005");
        manager.setEmailId("ravi.manager@example.com");
        manager.setPassword(passwordEncoder.encode("Strong@123"));
        manager.setRole(Role.MANAGER);
        manager.setEnabled(true);
        userRepository.save(manager);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailId": "ravi.manager@example.com", "mobileNumber": "9000000005"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE))
                .andExpect(jsonPath("$.requestId").doesNotExist());

        assertThat(otpChallengeRepository.count()).isZero();
    }

    @Test
    @DisplayName("the new password is held to the registration rules")
    void weakPasswordIsRejected() throws Exception {
        String mobile = "9000000006";
        registerOwner(mobile);
        String otp = requestOtp(ownerEmail(mobile), mobile);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "emailId": "%s",
                                  "mobileNumber": "%s",
                                  "otp": "%s",
                                  "password": "password"
                                }""".formatted(ownerEmail(mobile), mobile, otp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    @DisplayName("a malformed mobile is rejected before anything is sent")
    void malformedMobileIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailId": "owner@example.com", "mobileNumber": "12345"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    @DisplayName("a missing email is rejected before anything is sent")
    void missingEmailIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobileNumber\": \"9000000013\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    // ------------------------------------------------------ masked mobile hint

    /** Runs one hint lookup and returns the whole body. */
    private JsonNode hintBody(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/mobile-hint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailId\": \"%s\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** @return the reported mobile ending, or {@code null} when the field is absent. */
    private String hint(String email) throws Exception {
        JsonNode ending = hintBody(email).get("mobileEnding");
        return (ending == null || ending.isNull()) ? null : ending.asText();
    }

    @Test
    @DisplayName("the hint shows the tail of an owner's registered mobile")
    void eligibleOwnerGetsMaskedHint() throws Exception {
        String mobile = "9000000007";
        registerOwner(mobile);

        assertThat(hint(ownerEmail(mobile))).isEqualTo("007");
        assertThat(hintBody(ownerEmail(mobile)).get("registered").asBoolean()).isTrue();
        // Whatever the user typed on the sign-in form must not have to match the
        // stored casing (clients trim before sending; @Email rejects stray spaces).
        assertThat(hint("Owner9000000007@Example.COM")).isEqualTo("007");
    }

    @Test
    @DisplayName("unknown emails and non-owner accounts get no hint")
    void ineligibleAccountsGetNoHint() throws Exception {
        assertThat(hint("nobody@example.com")).isNull();
        assertThat(hintBody("nobody@example.com").get("registered").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a registered manager gets no password-reset hint")
    void managerGetsNoHint() throws Exception {
        User manager = new User();
        manager.setUserName("Ravi Manager");
        manager.setMobileNumber("9000000008");
        manager.setEmailId("ravi.hint@example.com");
        manager.setPassword(passwordEncoder.encode("Strong@123"));
        manager.setRole(Role.MANAGER);
        manager.setEnabled(true);
        userRepository.save(manager);

        assertThat(hint("ravi.hint@example.com")).isNull();
        assertThat(hintBody("ravi.hint@example.com").get("registered").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a malformed email is rejected by validation")
    void malformedEmailIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/mobile-hint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailId\": \"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    @DisplayName("hint lookups are throttled per client IP and answer 429 when throttled")
    void hintIsThrottledPerIp() throws Exception {
        String mobile = "9000000009";
        registerOwner(mobile);

        for (int i = 1; i <= 30; i++) {
            assertThat(hint(ownerEmail(mobile)))
                    .as("lookup %d", i)
                    .isEqualTo("009");
        }

        // The 31st lookup inside the window is rejected with retry metadata rather
        // than a null hint, so the client can show "wait" instead of "not registered".
        mockMvc.perform(post("/api/auth/mobile-hint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailId\": \"%s\"}".formatted(ownerEmail(mobile))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("too_many_attempts"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }
}
