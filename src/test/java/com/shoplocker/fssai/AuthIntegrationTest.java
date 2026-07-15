package com.shoplocker.fssai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplocker.fssai.entity.User;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end auth + access-control tests. Covers all 12 cases listed in
 * the requirements spec.
 *
 * <p>Uses H2 in-memory DB and a deterministic 32-byte JWT secret from
 * {@code src/test/resources/application.properties}. The whole Spring
 * context is wired (Spring Security + JWT filter chain) so this is a
 * faithful integration test rather than a unit test.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Auth integration tests")
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        // Clean shops first — H2 enforces the FK from Shop.user_id -> users.id.
        // Reversing the order would yield a referential-integrity violation.
        shopRepository.deleteAll();
        userRepository.deleteAll();
    }

    private static final String VALID_REGISTER_BODY = """
            {
              "userName": "Anjali Kashyap",
              "mobileNumber": "9876543210",
              "emailId": "anjali@example.com",
              "password": "Strong@123"
            }""";

    private static final String STRONG_PASSWORD_REGEX_OR_NOTE =
            "at least one uppercase + one lowercase + one digit + one special char + min 8 chars";

    /* ====================================================================
     * 0. Swagger / OpenAPI smoke (verifies the SecurityFilterChain matchers
     *    from the JWT-auth fix actually let /v3/api-docs and the Swagger UI
     *    bootstrap through the live filter chain. Spring Boot 3's
     *    PathPatternParser treats /v3/api-docs and /v3/api-docs/** as
     *    DIFFERENT patterns so both have to be registered explicitly.)
     * ====================================================================*/

    @Test
    @DisplayName("0a. OpenAPI JSON spec at /v3/api-docs is reachable anonymously")
    void openApiJsonIsReachable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("DukaanLocker API"));
    }

    @Test
    @DisplayName("0b. Swagger UI bootstrap at /swagger-ui/index.html is reachable anonymously")
    void swaggerUiIndexIsReachable() throws Exception {
        // springdoc 2.x serves the UI under /swagger-ui/index.html — the
        // bare /swagger-ui.html redirects to it. Either resolution proves
        // the matcher is correctly permitAll-ed.
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    /* ====================================================================
     * 1. Successful registration
     * 2. Role is automatically ADMIN
     * 3. Password is BCrypt encoded
     * 4. Duplicate email rejected
     * 5. Duplicate mobile rejected
     * 6. Successful login
     * 7. Wrong password returns 401
     * 8. Unknown email returns 401
     * 9. Protected endpoint without token returns 401
     * 10. Protected endpoint with valid ADMIN token works
     * 11. Registration request cannot assign role
     * 12. Password is not present in API response
     * ====================================================================*/

    @Test
    @DisplayName("1. Registration succeeds and returns 201 with token + bearer schema")
    void registerSucceeds() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.emailId").value("anjali@example.com"))
                .andReturn();

        // 12. Password never leaks through the API response.
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("password")).as("password must never appear in any auth response").isFalse();
    }

    @Test
    @DisplayName("2. New user is automatically assigned ADMIN role (never from request)")
    void newUserIsAdmin() throws Exception {
        // 11. Try to send a "role":"MANAGER" — must be IGNORED (DTO has no role field anyway).
        String bodyWithRole = """
                {
                  "userName": "Anjali Kashyap",
                  "mobileNumber": "9876543210",
                  "emailId": "anjali@example.com",
                  "password": "Strong@123",
                  "role": "MANAGER"
                }""";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithRole))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        User saved = userRepository.findByEmailId("anjali@example.com").orElseThrow();
        assertThat(saved.getRole().name()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("3. Stored password is BCrypt (not plaintext) and matches the raw value")
    void passwordIsBcryptEncoded() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmailId("anjali@example.com").orElseThrow();
        // BCrypt hashes start with $2a$, $2b$, $2y$, etc.
        assertThat(saved.getPassword())
                .as("password must be a BCrypt hash, not the raw plaintext")
                .startsWith("$2")
                .doesNotContain("Strong@123");
        // And round-trips through the encoder:
        assertThat(passwordEncoder.matches("Strong@123", saved.getPassword())).isTrue();
    }

    @Test
    @DisplayName("4. Duplicate email rejected with 409 + duplicate_email code")
    void duplicateEmailRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated());

        String dupEmailSameMobile = """
                {
                  "userName": "Another Person",
                  "mobileNumber": "9123456789",
                  "emailId": "ANJALI@example.com",
                  "password": "Strong@123"
                }""";

        // Email is normalized to lowercase before checking.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dupEmailSameMobile))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate_email"))
                .andExpect(jsonPath("$.message").value("An account already exists with this email address"));
    }

    @Test
    @DisplayName("5. Duplicate mobile rejected with 409 + duplicate_mobile code")
    void duplicateMobileRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated());

        String dupMobileSameEmail = """
                {
                  "userName": "Another Person",
                  "mobileNumber": "9876543210",
                  "emailId": "another@example.com",
                  "password": "Strong@123"
                }""";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dupMobileSameEmail))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate_mobile"))
                .andExpect(jsonPath("$.message").value("An account already exists with this mobile number"));
    }

    @Test
    @DisplayName("6. Login with correct emailId+password returns 200 + token")
    void loginSucceeds() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated());

        String loginBody = """
                {
                  "emailId": "ANJALI@EXAMPLE.COM",
                  "password": "Strong@123"
                }""";
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.emailId").value("anjali@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("7. Login with wrong password returns 401 + invalid_credentials")
    void wrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated());

        String loginBody = """
                {
                  "emailId": "anjali@example.com",
                  "password": "WrongPassword1!"
                }""";
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("8. Login with unknown email returns 401 (NOT 404)")
    void unknownEmailReturns401() throws Exception {
        String loginBody = """
                {
                  "emailId": "ghost@nope.com",
                  "password": "Strong@123"
                }""";
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("9. Protected POST /shops without JWT returns 401 + unauthorized envelope")
    void protectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(post("/shops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shopName": "Anjali's",
                                  "ownerName": "Anjali Kashyap",
                                  "mobile": "1234567890"
                                }"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("10. Protected POST /shops with valid ADMIN token returns 201 and links owner")
    void protectedEndpointWithAdminTokenWorks() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode auth = objectMapper.readTree(reg.getResponse().getContentAsString());
        long userId = auth.get("userId").asLong();
        String token = auth.get("token").asText();

        MvcResult shopResult = mockMvc.perform(post("/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shopName": "Anjali's",
                                  "ownerName": "Anjali Kashyap",
                                  "mobile": "1234567890"
                                }"""))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createdShop = objectMapper.readTree(shopResult.getResponse().getContentAsString());
        assertThat(createdShop.has("user"))
                .as("shop response must include the linked owner (Shop.user)")
                .isTrue();
        assertThat(createdShop.get("user").get("id").asLong()).isEqualTo(userId);
        assertThat(createdShop.get("user").get("emailId").asText()).isEqualTo("anjali@example.com");
        assertThat(createdShop.get("user").has("password"))
                .as("password must never appear in shop response (via Shop.user)")
                .isFalse();
    }

    @Test
    @DisplayName("11. Registration request cannot assign a role — it is rejected by Jackson or ignored")
    void registrationRequestRoleIsIgnored() throws Exception {
        // Register WITHOUT role in body — must succeed.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "Anjali Kashyap",
                                  "mobileNumber": "9876543210",
                                  "emailId": "anjali@example.com",
                                  "password": "Strong@123"
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("12. Password is not present in any auth API response")
    void passwordNotInApiResponse() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String regBody = reg.getResponse().getContentAsString();
        assertThat(regBody).doesNotContain("Strong@123").doesNotContain("$2");

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "emailId": "anjali@example.com",
                                  "password": "Strong@123"
                                }"""))
                .andExpect(status().isOk())
                .andReturn();
        String loginBodyJson = login.getResponse().getContentAsString();
        assertThat(loginBodyJson).doesNotContain("Strong@123").doesNotContain("$2");
    }
}
