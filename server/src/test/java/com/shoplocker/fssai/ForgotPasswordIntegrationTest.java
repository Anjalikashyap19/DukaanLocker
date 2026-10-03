package com.shoplocker.fssai;

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

    private static final String GENERIC_MESSAGE =
            "If this mobile number is registered to an owner account, an OTP has been sent to it.";

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
    }

    // ---------------------------------------------------------------- helpers

    /** Registers an ADMIN through the public endpoint so the account is realistic. */
    private void registerOwner(String mobile) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Owner %s",
                                  "mobileNumber": "%s",
                                  "emailId": "owner%s@example.com",
                                  "password": "Strong@123"
                                }""".formatted(mobile, mobile, mobile)))
                .andExpect(status().isCreated());
    }

    private String requestOtp(String mobile) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobileNumber\": \"%s\"}".formatted(mobile)))
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

    private void resetPassword(String mobile, String otp, String password) throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobileNumber": "%s",
                                  "otp": "%s",
                                  "password": "%s"
                                }""".formatted(mobile, otp, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("owner can reset their password and log in with it")
    void ownerResetsPasswordThenLogsIn() throws Exception {
        String mobile = "9000000001";
        String newPassword = "Brand@New1";
        registerOwner(mobile);

        String otp = requestOtp(mobile);
        resetPassword(mobile, otp, newPassword);

        User user = userRepository.findByMobileNumber(mobile).orElseThrow();
        assertThat(passwordEncoder.matches(newPassword, user.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("Strong@123", user.getPassword())).isFalse();

        // The new password works, the old one does not.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailId": "owner9000000001@example.com", "password": "%s"}"""
                                .formatted(newPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailId": "owner9000000001@example.com", "password": "Strong@123"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the OTP is single-use")
    void otpCannotBeReused() throws Exception {
        String mobile = "9000000002";
        registerOwner(mobile);

        String otp = requestOtp(mobile);
        resetPassword(mobile, otp, "Brand@New1");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobileNumber": "%s",
                                  "otp": "%s",
                                  "password": "Another@Pass1"
                                }""".formatted(mobile, otp)))
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
                                  "mobileNumber": "%s",
                                  "otp": "000000",
                                  "password": "Brand@New1"
                                }""".formatted(mobile)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_otp"));

        User user = userRepository.findByMobileNumber(mobile).orElseThrow();
        assertThat(passwordEncoder.matches("Strong@123", user.getPassword())).isTrue();
    }

    @Test
    @DisplayName("unknown mobile gets the same generic response as a real one")
    void unknownMobileIsNotEnumerated() throws Exception {
        // Not registered: no OTP may be echoed, and the wording must be identical
        // to the response a real owner account receives.
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobileNumber\": \"9000000004\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE))
                .andExpect(jsonPath("$.requestId").doesNotExist());

        assertThat(otpChallengeRepository.count()).isZero();
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
                        .content("{\"mobileNumber\": \"9000000005\"}"))
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
        String otp = requestOtp(mobile);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobileNumber": "%s",
                                  "otp": "%s",
                                  "password": "password"
                                }""".formatted(mobile, otp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    @DisplayName("a malformed mobile is rejected before anything is sent")
    void malformedMobileIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobileNumber\": \"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }
}
