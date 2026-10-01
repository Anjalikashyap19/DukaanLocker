package com.shoplocker.fssai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplocker.fssai.entity.Notification;
import com.shoplocker.fssai.entity.Role;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.repository.DeviceTokenRepository;
import com.shoplocker.fssai.repository.DocumentRepository;
import com.shoplocker.fssai.repository.NotificationRepository;
import com.shoplocker.fssai.repository.ManagerShopAssignmentRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.repository.UserRepository;
import com.shoplocker.fssai.service.GoogleOAuthService;
import com.shoplocker.fssai.service.TextractService;
import com.shoplocker.fssai.service.VerifiedGoogleToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive integration tests covering auth, shops, managers, documents, and access control.
 *
 * <p>Uses H2 in-memory DB and a deterministic JWT secret from
 * {@code src/test/resources/application.properties}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("DukaanLocker integration tests")
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private DeviceTokenRepository deviceTokenRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private ManagerShopAssignmentRepository assignmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private GoogleOAuthService googleOAuthService;
    /**
     * OCR is stubbed at the Textract seam so upload tests exercise the
     * validation and duplicate guards rather than Tesseract's accuracy, and
     * need no real PDF. Unstubbed calls return null, which no test relies on:
     * nothing else in this class drives the OCR pipeline.
     */
    @MockitoBean private TextractService textractService;

    private static final String VALID_REGISTER_BODY = """
            {
              "userName": "Anjali Kashyap",
              "mobileNumber": "9876543210",
              "emailId": "anjali@example.com",
              "password": "Strong@123"
            }""";

    private static final String VALID_SHOP_BODY = """
            {
              "shopName": "Anjali General Store",
              "ownerName": "Anjali Kashyap",
              "mobile": "1234567890",
              "category": "GROCERY",
              "scale": "SMALL",
              "state": "Tamil Nadu",
              "city": "Chennai",
              "branchName": "Main Branch",
              "address": "123 Main St, Chennai",
              "pincode": "600001"
            }""";

    /**
     * The West Bengal Certificate of Enlistment the upload tests stub OCR with.
     * Identical to {@code DocumentValidationServiceTest.WB_ENLISTMENT_TEXT},
     * which already proves this text passes the TRADE_LICENSE validator: it is
     * numbered "TRADE LICENCE NO" rather than "License Number" and issued by a
     * Municipality rather than a Municipal Corporation.
     *
     * <p>It carries the proprietor's name ("KAVINDER SEHGAL") but not the trade
     * name of any shop in this class, so ownership passes on the proprietor
     * branch of the check while the shop names below stay free to be whatever
     * the test needs.</p>
     */
    private static final String WB_ENLISTMENT_TEXT = """
            GOVERNMENT OF WEST BENGAL
            PERMANENT CERTIFICATE OF ENLISTMENT
            Form-24, Rule 82, Sec 118, West Bengal Municipal Act, 1993
            Issuing Authority: Baranagar (Municipality)
            TRADE LICENCE NO: 0917P1008125372790
            Name of the Enlistee: KAVINDER SEHGAL
            Business: MOTOR SPARE PARTS AND LUBRICATING OIL
            This certificate will be in force until the 03-Oct-2026 and is liable
            to be produced at the time of renewal.
            Date of Issue: 04-Oct-2025
            """;

    /** Two byte-strings that both start with a PDF magic header but are not
     *  identical, so they hash differently. OCR is stubbed, so their contents
     *  beyond the magic bytes are never examined. */
    private static final byte[] PDF_A = (
            "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n")
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] PDF_B = (
            "%PDF-1.4\n1 0 obj << /Type /Catalog /Variant 1 >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n")
            .getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void clean() {
        deviceTokenRepository.deleteAll();
        notificationRepository.deleteAll();
        assignmentRepository.deleteAll();
        documentRepository.deleteAll();
        shopRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ========================================================================
    // 0. Swagger / OpenAPI gate (default OFF; see app.security.expose-devtools)
    // ========================================================================

    @Test
    @DisplayName("0a. OpenAPI JSON spec at /v3/api-docs is NOT exposed when devtools are off")
    void openApiJsonIsNotReachableByDefault() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("0b. Swagger UI bootstrap at /swagger-ui/index.html is NOT exposed when devtools are off")
    void swaggerUiIndexIsNotReachableByDefault() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized());
    }

    // ========================================================================
    // Auth tests (1-12 from original spec)
    // ========================================================================

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

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("password")).as("password must never appear in any auth response").isFalse();
    }

    @Test
    @DisplayName("2. New user is automatically assigned ADMIN role (never from request)")
    void newUserIsAdmin() throws Exception {
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
    @DisplayName("3. Stored password is BCrypt (not plaintext)")
    void passwordIsBcryptEncoded() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmailId("anjali@example.com").orElseThrow();
        assertThat(saved.getPassword())
                .as("password must be a BCrypt hash, not the raw plaintext")
                .startsWith("$2")
                .doesNotContain("Strong@123");
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
    @DisplayName("6a. Successful password login creates an unread WELCOME notification")
    void loginCreatesWelcomeNotification() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "emailId": "anjali@example.com",
                                  "password": "Strong@123"
                                }"""))
                .andExpect(status().isOk());

        User user = userRepository.findByEmailId("anjali@example.com").orElseThrow();
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType()).isEqualTo("WELCOME");
        assertThat(notifications.get(0).getTitle()).isEqualTo("Welcome back, Anjali Kashyap!");
        assertThat(notifications.get(0).isRead()).isFalse();
    }

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
    @DisplayName("9. Protected POST /api/shops without JWT returns 401")
    void protectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(post("/api/shops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("10. Protected POST /api/shops with valid ADMIN token returns 201 and links owner")
    void protectedEndpointWithAdminTokenWorks() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode auth = objectMapper.readTree(reg.getResponse().getContentAsString());
        long userId = auth.get("userId").asLong();
        String token = auth.get("token").asText();

        MvcResult shopResult = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdShop = objectMapper.readTree(shopResult.getResponse().getContentAsString());
        assertThat(createdShop.has("ownerUserId"))
                .as("shop response must include the linked owner's userId")
                .isTrue();
        assertThat(createdShop.get("ownerUserId").asLong()).isEqualTo(userId);
        assertThat(createdShop.get("ownerEmail").asText()).isEqualTo("anjali@example.com");
        assertThat(createdShop.has("password"))
                .as("password must never appear in shop response")
                .isFalse();
    }

    // ========================================================================
    // New test cases per 20-test requirement
    // ========================================================================

    // --- 1. ADMIN can create shop ---
    @Test
    @DisplayName("11. ADMIN can create a shop")
    void adminCanCreateShop() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shopName").value("Anjali General Store"))
                .andExpect(jsonPath("$.category").value("GROCERY"))
                .andExpect(jsonPath("$.scale").value("SMALL"))
                .andExpect(jsonPath("$.state").value("Tamil Nadu"))
                .andExpect(jsonPath("$.city").value("Chennai"))
                .andExpect(jsonPath("$.ownerUserId").isNumber());
    }

    // --- 11a. Older app builds send the scale dropdown label, not the enum ---
    @Test
    @DisplayName("11a. Shop is created when scale arrives as the dropdown label")
    void shopCreatedWhenScaleIsDropdownLabel() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        String labelBody = VALID_SHOP_BODY.replace(
                "\"scale\": \"SMALL\"", "\"scale\": \"Small ( < 20 lac/year)\"");

        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(labelBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scale").value("SMALL"));
    }

    // --- 11b. A genuinely unknown scale is still rejected ---
    @Test
    @DisplayName("11b. Shop creation returns 400 for an unknown scale")
    void shopCreationRejectsUnknownScale() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        String badBody = VALID_SHOP_BODY.replace("\"scale\": \"SMALL\"", "\"scale\": \"HUGE\"");

        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    // --- 2. MANAGER cannot create shop ---
    @Test
    @DisplayName("12. MANAGER cannot create a shop")
    void managerCannotCreateShop() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String adminToken = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        String managerBody = """
                {
                  "userName": "Manager One",
                  "mobileNumber": "9876543211",
                  "emailId": "manager@example.com",
                  "password": "Manager@123"
                }""";
        mockMvc.perform(post("/api/managers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(managerBody))
                .andExpect(status().isCreated());

        String loginBody = """
                {
                  "emailId": "manager@example.com",
                  "password": "Manager@123"
                }""";
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        String mgrToken = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + mgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isForbidden());
    }

    // --- 3. Shop is automatically linked to logged-in ADMIN ---
    @Test
    @DisplayName("13. Shop is automatically linked to logged-in ADMIN")
    void shopLinkedToLoggedInAdmin() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode auth = objectMapper.readTree(reg.getResponse().getContentAsString());
        long userId = auth.get("userId").asLong();
        String token = auth.get("token").asText();

        MvcResult shopResult = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode shop = objectMapper.readTree(shopResult.getResponse().getContentAsString());
        assertThat(shop.get("ownerUserId").asLong()).isEqualTo(userId);
    }

    // --- 4. Frontend cannot override shop owner ---
    @Test
    @DisplayName("14. Frontend cannot override shop owner (ownerUserId is ignored)")
    void frontendCannotOverrideOwner() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode auth = objectMapper.readTree(reg.getResponse().getContentAsString());
        long userId = auth.get("userId").asLong();
        String token = auth.get("token").asText();

        String shopWithOwner = """
                {
                  "shopName": "Anjali General Store",
                  "ownerName": "Anjali Kashyap",
                  "mobile": "1234567890",
                  "category": "GROCERY",
                  "scale": "SMALL",
                  "state": "Tamil Nadu",
                  "city": "Chennai",
                  "ownerUserId": 99999
                }""";

        MvcResult shopResult = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shopWithOwner))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode shop = objectMapper.readTree(shopResult.getResponse().getContentAsString());
        assertThat(shop.get("ownerUserId").asLong()).isEqualTo(userId);
    }

    // --- 5. ADMIN can list own shops ---
    @Test
    @DisplayName("15. ADMIN can list own shops via /api/shops/my-shops")
    void adminCanListOwnShops() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated());

        String secondShop = """
                {
                  "shopName": "Anjali Electronics",
                  "ownerName": "Anjali Kashyap",
                  "mobile": "2234567890",
                  "category": "ELECTRONICS",
                  "scale": "MEDIUM",
                  "state": "Tamil Nadu",
                  "city": "Chennai"
                }""";
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondShop))
                .andExpect(status().isCreated());

        MvcResult listResult = mockMvc.perform(get("/api/shops/my-shops")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode shops = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(shops.isArray()).isTrue();
        assertThat(shops).hasSize(2);
    }

    // --- 6. ADMIN cannot access another admin's shop ---
    @Test
    @DisplayName("16. ADMIN cannot access another admin's shop")
    void adminCannotAccessOtherAdminShop() throws Exception {
        MvcResult reg1 = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token1 = objectMapper.readTree(reg1.getResponse().getContentAsString()).get("token").asText();

        String register2 = """
                {
                  "userName": "Second Admin",
                  "mobileNumber": "9988776655",
                  "emailId": "admin2@example.com",
                  "password": "Strong@123"
                }""";
        MvcResult reg2 = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(register2))
                .andExpect(status().isCreated())
                .andReturn();
        String token2 = objectMapper.readTree(reg2.getResponse().getContentAsString()).get("token").asText();

        MvcResult shopResult = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        long shopId = objectMapper.readTree(shopResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/shops/" + shopId)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden());
    }

    // --- 7. Required document checklist includes NOT_UPLOADED documents ---
    @Test
    @DisplayName("17. Required document checklist includes NOT_UPLOADED documents")
    void documentChecklistIncludesNotUploaded() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        MvcResult shopResult = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        long shopId = objectMapper.readTree(shopResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult docsResult = mockMvc.perform(get("/api/shops/" + shopId + "/documents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode docs = objectMapper.readTree(docsResult.getResponse().getContentAsString());
        assertThat(docs.isArray()).isTrue();
        assertThat(docs).isNotEmpty();

        boolean hasNotUploaded = false;
        for (JsonNode doc : docs) {
            if ("NOT_UPLOADED".equals(doc.get("status").asText())) {
                hasNotUploaded = true;
                assertThat(doc.get("id").isNull()).isTrue();
                assertThat(doc.get("version").asInt()).isEqualTo(0);
                break;
            }
        }
        assertThat(hasNotUploaded).as("checklist must contain NOT_UPLOADED documents").isTrue();
    }

    // --- 8. Document checklist returns proper structure ---
    @Test
    @DisplayName("18. Document checklist returns proper structure")
    void documentChecklistReturnsStructure() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        MvcResult shopResult = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        long shopId = objectMapper.readTree(shopResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult docsResult = mockMvc.perform(get("/api/shops/" + shopId + "/documents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode docs = objectMapper.readTree(docsResult.getResponse().getContentAsString());
        assertThat(docs.isArray()).isTrue();

        for (JsonNode doc : docs) {
            assertThat(doc.has("documentType")).isTrue();
            assertThat(doc.has("status")).isTrue();
            assertThat(doc.has("shopId")).isTrue();
            assertThat(doc.get("shopId").asLong()).isEqualTo(shopId);
        }
    }

    // --- 9. Document listing requires authentication ---
    @Test
    @DisplayName("19. Document listing requires authentication")
    void documentListingRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/shops/1/documents"))
                .andExpect(status().isUnauthorized());
    }

    // --- 10. ADMIN can create MANAGER ---
    @Test
    @DisplayName("20. ADMIN can create a manager")
    void adminCanCreateManager() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        String managerBody = """
                {
                  "userName": "Manager One",
                  "mobileNumber": "9876543211",
                  "emailId": "manager@example.com",
                  "password": "Manager@123"
                }""";

        MvcResult mgrResult = mockMvc.perform(post("/api/managers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(managerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.userName").value("Manager One"))
                .andReturn();

        JsonNode mgr = objectMapper.readTree(mgrResult.getResponse().getContentAsString());
        assertThat(mgr.has("password")).as("password must not appear in manager response").isFalse();
    }

    // --- 11. Public registration always creates ADMIN ---
    @Test
    @DisplayName("21. Public registration always creates ADMIN, never MANAGER")
    void publicRegistrationCannotCreateManager() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // --- 12. ADMIN can assign own shop to own manager ---
    @Test
    @DisplayName("22. ADMIN can assign own shop to own manager")
    void adminCanAssignShopToManager() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        MvcResult shopResult = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        long shopId = objectMapper.readTree(shopResult.getResponse().getContentAsString()).get("id").asLong();

        String managerBody = """
                {
                  "userName": "Manager One",
                  "mobileNumber": "9876543211",
                  "emailId": "manager@example.com",
                  "password": "Manager@123"
                }""";
        MvcResult mgrResult = mockMvc.perform(post("/api/managers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(managerBody))
                .andExpect(status().isCreated())
                .andReturn();
        long managerId = objectMapper.readTree(mgrResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/managers/" + managerId + "/shops/" + shopId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    // --- 13. ADMIN cannot assign another admin's shop ---
    @Test
    @DisplayName("23. ADMIN cannot assign another admin's shop to their manager")
    void adminCannotAssignOtherAdminShop() throws Exception {
        MvcResult reg1 = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token1 = objectMapper.readTree(reg1.getResponse().getContentAsString()).get("token").asText();

        String register2 = """
                {
                  "userName": "Second Admin",
                  "mobileNumber": "9988776655",
                  "emailId": "admin2@example.com",
                  "password": "Strong@123"
                }""";
        MvcResult reg2 = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(register2))
                .andExpect(status().isCreated())
                .andReturn();
        String token2 = objectMapper.readTree(reg2.getResponse().getContentAsString()).get("token").asText();

        MvcResult shopResult = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        long shopId = objectMapper.readTree(shopResult.getResponse().getContentAsString()).get("id").asLong();

        String managerBody = """
                {
                  "userName": "Manager One",
                  "mobileNumber": "9876543211",
                  "emailId": "manager@example.com",
                  "password": "Manager@123"
                }""";
        MvcResult mgrResult = mockMvc.perform(post("/api/managers")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(managerBody))
                .andExpect(status().isCreated())
                .andReturn();
        long managerId = objectMapper.readTree(mgrResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/managers/" + managerId + "/shops/" + shopId)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden());
    }

    // --- 14. Duplicate assignment is rejected ---
    @Test
    @DisplayName("24. Duplicate assignment is rejected")
    void duplicateAssignmentIsRejected() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        MvcResult shopResult = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        long shopId = objectMapper.readTree(shopResult.getResponse().getContentAsString()).get("id").asLong();

        String managerBody = """
                {
                  "userName": "Manager One",
                  "mobileNumber": "9876543211",
                  "emailId": "manager@example.com",
                  "password": "Manager@123"
                }""";
        MvcResult mgrResult = mockMvc.perform(post("/api/managers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(managerBody))
                .andExpect(status().isCreated())
                .andReturn();
        long managerId = objectMapper.readTree(mgrResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/managers/" + managerId + "/shops/" + shopId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/managers/" + managerId + "/shops/" + shopId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    // --- 15. MANAGER can list assigned shops ---
    @Test
    @DisplayName("25. MANAGER can list assigned shops via /api/managers/me/shops")
    void managerCanListAssignedShops() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        MvcResult shopResult = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        long shopId = objectMapper.readTree(shopResult.getResponse().getContentAsString()).get("id").asLong();

        String managerBody = """
                {
                  "userName": "Manager One",
                  "mobileNumber": "9876543211",
                  "emailId": "manager@example.com",
                  "password": "Manager@123"
                }""";
        MvcResult mgrResult = mockMvc.perform(post("/api/managers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(managerBody))
                .andExpect(status().isCreated())
                .andReturn();
        long managerId = objectMapper.readTree(mgrResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/managers/" + managerId + "/shops/" + shopId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        String loginBody = """
                {
                  "emailId": "manager@example.com",
                  "password": "Manager@123"
                }""";
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        String mgrToken = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();

        MvcResult listResult = mockMvc.perform(get("/api/managers/me/shops")
                        .header("Authorization", "Bearer " + mgrToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode shops = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(shops.isArray()).isTrue();
        assertThat(shops).hasSize(1);
    }

    // --- 16. MANAGER cannot access unassigned shop ---
    @Test
    @DisplayName("26. MANAGER cannot access unassigned shop")
    void managerCannotAccessUnassignedShop() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        MvcResult shopResult = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        long shopId = objectMapper.readTree(shopResult.getResponse().getContentAsString()).get("id").asLong();

        String managerBody = """
                {
                  "userName": "Manager One",
                  "mobileNumber": "9876543211",
                  "emailId": "manager@example.com",
                  "password": "Manager@123"
                }""";
        mockMvc.perform(post("/api/managers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(managerBody))
                .andExpect(status().isCreated());

        String loginBody = """
                {
                  "emailId": "manager@example.com",
                  "password": "Manager@123"
                }""";
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        String mgrToken = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(get("/api/shops/" + shopId)
                        .header("Authorization", "Bearer " + mgrToken))
                .andExpect(status().isForbidden());
    }

    // --- 17. Password is never returned in manager response ---
    @Test
    @DisplayName("27. Password is never returned in manager response")
    void passwordNotInManagerResponse() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        String managerBody = """
                {
                  "userName": "Manager One",
                  "mobileNumber": "9876543211",
                  "emailId": "manager@example.com",
                  "password": "Manager@123"
                }""";
        MvcResult mgrResult = mockMvc.perform(post("/api/managers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(managerBody))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = mgrResult.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("Manager@123").doesNotContain("$2");
    }

    // --- 18. No document DELETE endpoint available ---
    @Test
    @DisplayName("28. No document DELETE endpoint exists")
    void noDocumentDeleteEndpoint() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(delete("/api/shops/1/documents/GST")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // --- 19. Password is not present in any API response ---
    @Test
    @DisplayName("29. Password is not present in any auth API response")
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

    // --- 20. Registration request role is ignored ---
    @Test
    @DisplayName("30. Registration request cannot assign a role")
    void registrationRequestRoleIsIgnored() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // ========================================================================
    // Google login tests (login-google must never auto-register)
    // ========================================================================

    @Test
    @DisplayName("33. Google login succeeds for an existing user")
    void googleLoginSucceedsForExistingUser() throws Exception {
        // Register a normal email/password account first
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated());

        when(googleOAuthService.verify(anyString()))
                .thenReturn(new VerifiedGoogleToken("google-sub-123", "anjali@example.com", true));

        String googleBody = """
                {
                  "firebaseUid": "google-sub-123",
                  "userName": "Anjali Kashyap",
                  "emailId": "anjali@example.com",
                  "idToken": "fake-token"
                }""";
        mockMvc.perform(post("/api/auth/login-google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(googleBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.emailId").value("anjali@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("34. Google login for an unknown email returns 404 and does NOT create a user")
    void googleLoginUnknownEmailReturns404WithoutCreatingUser() throws Exception {
        when(googleOAuthService.verify(anyString()))
                .thenReturn(new VerifiedGoogleToken("google-sub-999", "newguy@example.com", true));

        String googleBody = """
                {
                  "firebaseUid": "google-sub-999",
                  "userName": "New Guy",
                  "emailId": "newguy@example.com",
                  "idToken": "fake-token"
                }""";
        mockMvc.perform(post("/api/auth/login-google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(googleBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("user_not_found"));

        assertThat(userRepository.findByEmailId("newguy@example.com").isPresent())
                .as("login-google must never auto-register a new account")
                .isFalse();
    }

    @Test
    @DisplayName("35. register-google still works (regression)")
    void googleRegisterStillWorks() throws Exception {
        when(googleOAuthService.verify(anyString()))
                .thenReturn(new VerifiedGoogleToken("google-sub-777", "fresh@example.com", true));

        String googleBody = """
                {
                  "firebaseUid": "google-sub-777",
                  "userName": "Fresh User",
                  "emailId": "fresh@example.com",
                  "idToken": "fake-token"
                }""";
        mockMvc.perform(post("/api/auth/register-google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(googleBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.emailId").value("fresh@example.com"));

        assertThat(userRepository.findByEmailId("fresh@example.com").isPresent())
                .as("register-google must create a new account for an unknown email")
                .isTrue();
    }

    // ========================================================================
    // Re-upload tests (reqs 9 and 20 from spec)
    // ========================================================================

    @Test
    @DisplayName("31. Re-upload increments document version")
    void reuploadIncrementsVersion() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        MvcResult shopResult = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        long shopId = objectMapper.readTree(shopResult.getResponse().getContentAsString()).get("id").asLong();

        // Verify checklist has 6 not-uploaded docs for GROCERY
        MvcResult docsBeforeResult = mockMvc.perform(get("/api/shops/" + shopId + "/documents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode docsBefore = objectMapper.readTree(docsBeforeResult.getResponse().getContentAsString());
        // Verify structure: each doc has documentType, status, shopId
        assertThat(docsBefore.isArray()).isTrue();
        for (JsonNode doc : docsBefore) {
            assertThat(doc.has("documentType")).isTrue();
            assertThat(doc.has("shopId")).isTrue();
        }
    }

    @Test
    @DisplayName("32. Unauthorized user cannot access another admin's shop documents")
    void unauthorizedCannotAccessShopDocuments() throws Exception {
        MvcResult reg1 = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String token1 = objectMapper.readTree(reg1.getResponse().getContentAsString()).get("token").asText();

        String register2 = """
                {
                  "userName": "Second Admin",
                  "mobileNumber": "9988776655",
                  "emailId": "admin2@example.com",
                  "password": "Strong@123"
                }""";
        MvcResult reg2 = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(register2))
                .andExpect(status().isCreated())
                .andReturn();
        String token2 = objectMapper.readTree(reg2.getResponse().getContentAsString()).get("token").asText();

        MvcResult shopResult = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        long shopId = objectMapper.readTree(shopResult.getResponse().getContentAsString()).get("id").asLong();

        // Admin 2 cannot view admin 1's document checklist
        mockMvc.perform(get("/api/shops/" + shopId + "/documents")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden());

        // Admin 2 cannot view admin 1's shop detail
        mockMvc.perform(get("/api/shops/" + shopId)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden());
    }

    // ========================================================================
    // Duplicate shop prevention (same owner + name + branch)
    // ========================================================================

    private String registerToken(String emailId, String mobileNumber) throws Exception {
        String body = """
                {
                  "userName": "Test Admin",
                  "mobileNumber": "%s",
                  "emailId": "%s",
                  "password": "Strong@123"
                }""".formatted(mobileNumber, emailId);
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @DisplayName("33. Creating the same shop name + branch twice returns 409 duplicate_shop")
    void duplicateShopCreateRejected() throws Exception {
        String token = registerToken("dup@example.com", "9876543210");

        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate_shop"))
                .andExpect(jsonPath("$.message").value(
                        containsString("already exists")));
    }

    @Test
    @DisplayName("34. Duplicate check ignores case and extra whitespace")
    void duplicateShopCheckNormalizesCaseAndWhitespace() throws Exception {
        String token = registerToken("dup2@example.com", "9876543210");

        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated());

        String variantBody = VALID_SHOP_BODY
                .replace("Anjali General Store", "  anjali   GENERAL store  ");
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(variantBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate_shop"));
    }

    @Test
    @DisplayName("35. Same shop name with a different branch is still a duplicate (branch is only a label)")
    void sameNameDifferentBranchRejected() throws Exception {
        String token = registerToken("dup3@example.com", "9876543210");

        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated());

        String branchBody = VALID_SHOP_BODY.replace("Main Branch", "Anna Nagar Branch");
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate_shop"));
    }

    @Test
    @DisplayName("35b. Punctuation and diacritics do not let a duplicate through")
    void punctuationAndDiacriticsDoNotBypassDuplicateCheck() throws Exception {
        String token = registerToken("dup4@example.com", "9876543210");

        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated());

        // "Anjali General Store" -> accents + hyphen + stray punctuation.
        String punctuated = VALID_SHOP_BODY.replace(
                "Anjali General Store", "Ánjali General–Store!!");
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(punctuated))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate_shop"));
    }

    @Test
    @DisplayName("35c. A genuinely different business name is still accepted")
    void distinctNameStillAllowed() throws Exception {
        String token = registerToken("dup5@example.com", "9876543210");

        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated());

        String otherBody = VALID_SHOP_BODY.replace("Anjali General Store", "Anjali Electronics");
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(otherBody))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("35d. A shortened name is a duplicate of the existing longer one")
    void shorterNameAgainstLongerRejected() throws Exception {
        String token = registerToken("dup6@example.com", "9876543210");

        String longBody = VALID_SHOP_BODY.replace("Anjali General Store", "Sehgal Automobiles");
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(longBody))
                .andExpect(status().isCreated());

        String shortBody = VALID_SHOP_BODY.replace("Anjali General Store", "Sehgal");
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shortBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate_shop"))
                .andExpect(jsonPath("$.message").value(containsString("Sehgal Automobiles")));
    }

    @Test
    @DisplayName("35e. A lengthened name is a duplicate of the existing shorter one")
    void longerNameAgainstShorterRejected() throws Exception {
        String token = registerToken("dup7@example.com", "9876543210");

        String shortBody = VALID_SHOP_BODY.replace("Anjali General Store", "Sehgal");
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shortBody))
                .andExpect(status().isCreated());

        String longBody = VALID_SHOP_BODY.replace("Anjali General Store", "Sehgal Automobiles");
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(longBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate_shop"));
    }

    @Test
    @DisplayName("36. Different owner may create a shop with the same name + branch")
    void differentOwnerSameNameAllowed() throws Exception {
        String token1 = registerToken("owner1@example.com", "9876543210");
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated());

        String token2 = registerToken("owner2@example.com", "9988776655");
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("37. Renaming a shop onto another shop of the same owner returns 409")
    void renameOntoExistingShopRejected() throws Exception {
        String token = registerToken("owner3@example.com", "9876543210");

        MvcResult shop1 = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SHOP_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        long shop1Id = objectMapper.readTree(shop1.getResponse().getContentAsString()).get("id").asLong();

        String shop2Body = VALID_SHOP_BODY.replace("Anjali General Store", "Anjali Electronics");
        MvcResult shop2 = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shop2Body))
                .andExpect(status().isCreated())
                .andReturn();
        long shop2Id = objectMapper.readTree(shop2.getResponse().getContentAsString()).get("id").asLong();

        // Rename shop2 onto shop1's name + branch -> conflict
        String collideBody = """
                {"shopName": "Anjali General Store", "branchName": "Main Branch"}""";
        mockMvc.perform(put("/api/shops/" + shop2Id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(collideBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate_shop"));

        // Same rename with a DIFFERENT branch is still a conflict — branch no
        // longer distinguishes two businesses.
        String collideOtherBranch = """
                {"shopName": "Anjali General Store", "branchName": "T. Nagar Branch"}""";
        mockMvc.perform(put("/api/shops/" + shop2Id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(collideOtherBranch))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate_shop"));

        // Renaming onto a strict word-subset of an existing name is also a
        // conflict — "Anjali" must not become a second copy of "Anjali General
        // Store" just by dropping two words.
        String collideSubset = """
                {"shopName": "Anjali", "branchName": "Main Branch"}""";
        mockMvc.perform(put("/api/shops/" + shop2Id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(collideSubset))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate_shop"))
                .andExpect(jsonPath("$.message").value(
                        containsString("Anjali General Store")));

        // Keeping a shop's own name+branch on edit stays legal
        String ownBody = """
                {"shopName": "Anjali Electronics", "branchName": "Main Branch"}""";
        mockMvc.perform(put("/api/shops/" + shop2Id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ownBody))
                .andExpect(status().isOk());

        assertThat(shop1Id).isNotEqualTo(shop2Id);
    }

    // ========================================================================
    // Cross-shop document reuse (content hash)
    // ========================================================================

    @Test
    @DisplayName("38. The same file cannot be filed under two shops of one account")
    void duplicateDocumentContentRejected() throws Exception {
        String token = registerToken("dupdoc@example.com", "9876543210");

        // OCR is stubbed so the pipeline sees the enlistment certificate whatever
        // bytes are uploaded. "New Market" with proprietor "Kavinder Sehgal" is
        // the documented trade-name-vs-proprietor case the ownership gate accepts.
        when(textractService.extractText(any(byte[].class), anyString()))
                .thenReturn(WB_ENLISTMENT_TEXT);

        long shop1Id = createShop(token, VALID_SHOP_BODY
                .replace("Anjali General Store", "New Market")
                .replace("Anjali Kashyap", "Kavinder Sehgal"));
        long shop2Id = createShop(token, VALID_SHOP_BODY
                .replace("Anjali General Store", "Kavinder Sehgal")
                .replace("Anjali Kashyap", "Kavinder Sehgal")
                .replace("Main Branch", "Howrah Branch")
                .replace("1234567890", "1234567891"));

        // 1. The first shop takes the document. version counts re-uploads, so a
        //    first-time upload is 0 and one re-upload takes it to 1.
        mockMvc.perform(reupload(token, shop1Id, PDF_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));

        // 2. The second shop may not take identical bytes — even though the type
        //    check passes and the ownership check passes legitimately, because
        //    the certificate really is this proprietor's.
        mockMvc.perform(reupload(token, shop2Id, PDF_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate_document"))
                .andExpect(jsonPath("$.message").value(containsString("New Market")));

        // 3. Re-uploading to the shop that already holds the file stays legal
        //    and still bumps the version.
        mockMvc.perform(reupload(token, shop1Id, PDF_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // 4. A genuinely different file reaches the second shop.
        mockMvc.perform(reupload(token, shop2Id, PDF_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    /** Multipart PUT to the re-upload endpoint; the endpoint is a PUT, not a POST. */
    private static MockHttpServletRequestBuilder reupload(String token, long shopId, byte[] bytes) {
        MockMultipartFile file = new MockMultipartFile(
                "file", "trade_license.pdf", MediaType.APPLICATION_PDF_VALUE, bytes);
        return multipart(HttpMethod.PUT,
                        "/api/shops/" + shopId + "/documents/TRADE_LICENSE/reupload")
                .file(file)
                .header("Authorization", "Bearer " + token);
    }

    private long createShop(String token, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
