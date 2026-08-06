package com.shoplocker.fssai.service;

import com.shoplocker.fssai.dto.AuthResponse;
import com.shoplocker.fssai.dto.LoginRequest;
import com.shoplocker.fssai.dto.MsmeAuthResponse;
import com.shoplocker.fssai.dto.MsmeParsedData;
import com.shoplocker.fssai.dto.RegisterRequest;
import com.shoplocker.fssai.dto.RegisterWithMsmeRequest;
import com.shoplocker.fssai.dto.UdyamVerifyRequest;
import com.shoplocker.fssai.dto.UdyamVerifyResponse;
import com.shoplocker.fssai.service.UdyamVerificationService;
import com.shoplocker.fssai.entity.*;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.*;
import com.shoplocker.fssai.security.JwtService;
import com.shoplocker.fssai.util.MsmeDataParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Authentication flows — registration and login.
 *
 * <h3>Registration</h3>
 * <ol>
 *   <li>Validate request ({@code @Valid} in controller).</li>
 *   <li>Normalize emailId to lowercase; trim name/mobile.</li>
 *   <li>Reject on duplicate email or mobile — both surface as 409 with
 *       stable {@code FailureCode}s.</li>
 *   <li>Create a new user, force {@code role = ADMIN} (never read from
 *       request), force {@code enabled = true}.</li>
 *   <li>BCrypt-encode the password before persisting.</li>
 *   <li>Issue a JWT and return {@link AuthResponse}.</li>
 * </ol>
 *
 * <h3>Login</h3>
 * <ol>
 *   <li>Normalize emailId to lowercase.</li>
 *   <li>Delegate to Spring Security's {@link AuthenticationManager} —
 *       never manually compare plaintext passwords.</li>
 *   <li>Translate {@code BadCredentialsException}/{@code DisabledException}
 *       to safe user-facing messages (no internal Spring text leaks).</li>
 *   <li>Issue a JWT and return {@link AuthResponse}.</li>
 * </ol>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final ShopRepository shopRepository;
    private final DocumentRepository documentRepository;
    private final RequiredDocumentService requiredDocumentService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       ShopRepository shopRepository,
                       DocumentRepository documentRepository,
                       RequiredDocumentService requiredDocumentService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.shopRepository = shopRepository;
        this.documentRepository = documentRepository;
        this.requiredDocumentService = requiredDocumentService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmailId());
        String mobile = request.getMobileNumber() == null ? null : request.getMobileNumber().trim();
        String name = request.getUserName() == null ? null : request.getUserName().trim();

        // Defensive normalization check — these are validated by @Valid in
        // AuthController and would normally fail bean validation first.
        // Kept as belt-and-suspenders in case AuthService is ever called from
        // another path (e.g., admin tooling).
        if (mobile == null || mobile.isBlank() || !mobile.matches("^[0-9]{10}$")) {
            throw new FssaiException(
                    "Invalid registration details",
                    FailureCode.INVALID_REQUEST);
        }
        if (email == null || email.isBlank()) {
            throw new FssaiException(
                    "Invalid registration details",
                    FailureCode.INVALID_REQUEST);
        }

        if (userRepository.existsByEmailId(email)) {
            log.info("Registration rejected: duplicate email {}", email);
            throw new FssaiException(
                    "An account already exists with this email address",
                    FailureCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByMobileNumber(mobile)) {
            log.info("Registration rejected: duplicate mobile {}", mobile);
            throw new FssaiException(
                    "An account already exists with this mobile number",
                    FailureCode.DUPLICATE_MOBILE);
        }

        User user = new User();
        user.setUserName(name);
        user.setMobileNumber(mobile);
        user.setEmailId(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.ADMIN);
        user.setEnabled(true);

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved);

        log.info("User registered: id={} email={} role={}", saved.getId(), saved.getEmailId(), saved.getRole());
        return AuthResponse.from(saved, token);
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmailId());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (BadCredentialsException bce) {
            log.info("Login failed (bad credentials) for {}", email);
            throw new FssaiException(
                    "Invalid email or password",
                    FailureCode.INVALID_CREDENTIALS);
        } catch (DisabledException de) {
            log.info("Login failed (disabled) for {}", email);
            throw new FssaiException(
                    "This account has been disabled. Please contact support.",
                    FailureCode.DISABLED_USER);
        } catch (AuthenticationException ae) {
            // Catch-all so we never leak internal Spring Security messages.
            log.info("Login failed ({} ) for {}", ae.getClass().getSimpleName(), email);
            throw new FssaiException(
                    "Invalid email or password",
                    FailureCode.INVALID_CREDENTIALS);
        }

        User user = userRepository.findByEmailId(email)
                .orElseThrow(() -> {
                    // Should be unreachable — authenticate() succeeded so the user must exist.
                    log.warn("Authenticated principal {} not found in DB post-authenticate", email);
                    return new FssaiException(
                            "Invalid email or password",
                            FailureCode.INVALID_CREDENTIALS);
                });

        String token = jwtService.generateToken(user);
        return AuthResponse.from(user, token);
    }

    /**
     * Register a new user by verifying their Udyam (MSME) number against the
     * government portal.  On success:
     * <ol>
     *   <li>Verifies Udyam number + CAPTCHA against government portal.</li>
     *   <li>Generates PDF from HTML and uploads to S3.</li>
     *   <li>Parses enterprise data from the HTML certificate.</li>
     *   <li>Creates a User account with the entrepreneur's name.</li>
     *   <li>Creates a Shop linked to the user with enterprise details.</li>
     *   <li>Creates all required document records (MSME = VERIFIED, others = NOT_UPLOADED).</li>
     *   <li>Attaches the MSME certificate as a verified document.</li>
     *   <li>Returns JWT token with user, shop, and certificate details.</li>
     * </ol>
     *
     * <p>NOTE: This method is intentionally NOT @Transactional because it makes
     * external HTTP calls (government portal verification, PDF generation, S3 upload)
     * that can take minutes. DB operations are grouped at the end after external calls
     * complete.</p>
     */
    public MsmeAuthResponse registerWithMsme(RegisterWithMsmeRequest request,
                                         UdyamVerificationService udyamService) {
        String mobile = request.getMobileNumber() == null ? null : request.getMobileNumber().trim();

        if (mobile == null || mobile.isBlank() || !mobile.matches("^[0-9]{10}$")) {
            throw new FssaiException("Invalid registration details", FailureCode.INVALID_REQUEST);
        }

        if (userRepository.existsByMobileNumber(mobile)) {
            log.info("MSME registration rejected: duplicate mobile {}", mobile);
            throw new FssaiException(
                    "An account already exists with this mobile number",
                    FailureCode.DUPLICATE_MOBILE);
        }

        // ── Step 0: Reject MSME numbers already registered once ──
        // An Udyam number can be used to create an account exactly once. After
        // that, the business owner must log in with the email from the MSME
        // certificate and the password set at registration.
        String udyamNumber = request.getMsmeNumber().trim().toUpperCase();
        if (documentRepository.existsByDocumentNumberAndDocumentType(
                udyamNumber, DocumentType.MSME_CERTIFICATE)) {
            log.info("MSME registration rejected: Udyam number already registered {}", udyamNumber);
            throw new FssaiException(
                    "This MSME number is already registered. Please log in with the email " +
                    "on your MSME certificate and the password you set during registration.",
                    FailureCode.DUPLICATE_MSME);
        }

        // ── Step 1: Verify Udyam against government portal + generate PDF ──
        UdyamVerifyRequest verifyReq = new UdyamVerifyRequest();
        verifyReq.setSessionId(request.getSessionId());
        verifyReq.setUdyamNumber(udyamNumber);
        verifyReq.setCaptchaText(request.getCaptchaText());

        UdyamVerifyResponse verifyResult = udyamService.verifyAndGeneratePdf(verifyReq);

        if (!verifyResult.isSuccess()) {
            throw new FssaiException(
                    verifyResult.getErrorMessage() != null
                            ? verifyResult.getErrorMessage()
                            : "Udyam verification failed",
                    FailureCode.INVALID_REQUEST);
        }

        // ── Step 2: Parse MSME data from HTML certificate ──
        MsmeParsedData parsedData = null;
        if (verifyResult.getCertificateHtml() != null && !verifyResult.getCertificateHtml().isBlank()) {
            try {
                parsedData = MsmeDataParser.parse(verifyResult.getCertificateHtml());
                log.info("Parsed MSME data: enterprise={}, owner={}",
                        parsedData.getEnterpriseName(), parsedData.getEntrepreneurName());
            } catch (Exception e) {
                log.warn("Failed to parse MSME HTML, using fallback data: {}", e.getMessage());
            }
        }

        // ── Step 3: Create User with parsed data ──
        User savedUser = createMsmeUser(mobile, request.getPassword(), parsedData);
        log.info("MSME user created: id={} mobile={} name={}",
                savedUser.getId(), mobile, savedUser.getUserName());

        // ── Step 4: Create Shop with parsed data ──
        Shop savedShop = createMsmeShop(savedUser, parsedData, udyamNumber);
        log.info("MSME shop created: id={} name={}", savedShop.getId(), savedShop.getShopName());

        // ── Step 5: Create required documents and attach MSME certificate ──
        createRequiredDocuments(savedShop, verifyResult.getPdfUrl(), udyamNumber);
        log.info("Required documents created for shop {}", savedShop.getId());

        // ── Step 6: Generate JWT token ──
        String token = jwtService.generateToken(savedUser);

        log.info("MSME auto-registration complete: userId={} shopId={} udyam={}",
                savedUser.getId(), savedShop.getId(), udyamNumber);

        // ── Step 7: Build response with all details ──
        AuthResponse auth = AuthResponse.from(savedUser, token);
        MsmeAuthResponse msmeAuth = new MsmeAuthResponse(
                auth, verifyResult.getPdfUrl(), udyamNumber);

        // Populate enterprise and shop details
        if (parsedData != null) {
            msmeAuth.setEnterpriseName(parsedData.getEnterpriseName());
            msmeAuth.setEntrepreneurName(parsedData.getEntrepreneurName());
        }
        msmeAuth.setEmailId(savedUser.getEmailId());
        msmeAuth.setShopId(savedShop.getId());
        msmeAuth.setShopName(savedShop.getShopName());
        msmeAuth.setShopCategory(savedShop.getCategory());
        msmeAuth.setShopState(savedShop.getState());
        msmeAuth.setShopCity(savedShop.getCity());
        msmeAuth.setShopAddress(savedShop.getAddress());

        return msmeAuth;
    }

    /**
     * Creates a new User account from MSME parsed data.
     * Uses entrepreneur name as userName, generates a local email.
     * Falls back to enterprise name or generic "MSME Owner" if entrepreneur name is missing.
     */
    private User createMsmeUser(String mobile, String password, MsmeParsedData parsedData) {
        if (userRepository.existsByMobileNumber(mobile)) {
            throw new FssaiException(
                    "An account already exists with this mobile number",
                    FailureCode.DUPLICATE_MOBILE);
        }

        // Use entrepreneur name if available, otherwise fallback to enterprise name or generic
        String userName = "MSME Owner";
        if (parsedData != null) {
            if (parsedData.getEntrepreneurName() != null && !parsedData.getEntrepreneurName().isBlank()) {
                userName = parsedData.getEntrepreneurName();
            } else if (parsedData.getEnterpriseName() != null && !parsedData.getEnterpriseName().isBlank()) {
                userName = parsedData.getEnterpriseName();
            }
        }

        // Use email from MSME HTML if available, otherwise generate a dummy email
        String email = "msme_" + mobile + "@dukaanlocker.local";
        if (parsedData != null && parsedData.getEmailId() != null && !parsedData.getEmailId().isBlank()) {
            email = parsedData.getEmailId().toLowerCase();
            // Check if email already exists, if so append mobile suffix
            if (userRepository.existsByEmailId(email)) {
                email = "msme_" + mobile + "@dukaanlocker.local";
            }
        }

        User user = new User();
        user.setUserName(userName);
        user.setMobileNumber(mobile);
        user.setEmailId(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.ADMIN);
        user.setEnabled(true);

        return userRepository.save(user);
    }

    /**
     * Creates a Shop linked to the user with enterprise details parsed from MSME data.
     * Falls back to sensible defaults for any missing fields.
     */
    private Shop createMsmeShop(User user, MsmeParsedData parsedData, String udyamNumber) {
        Shop shop = new Shop();

        // Shop name = Enterprise Name (fallback to Udyam number)
        String shopName = udyamNumber;
        if (parsedData != null && parsedData.getEnterpriseName() != null && !parsedData.getEnterpriseName().isBlank()) {
            shopName = parsedData.getEnterpriseName();
        }
        shop.setShopName(shopName);

        // Owner name = Entrepreneur Name (fallback to user name)
        shop.setOwnerName(user.getUserName());

        // Mobile = user's mobile
        shop.setMobile(user.getMobileNumber());

        // Category = Major Activity mapped to DukaanLocker category (fallback to GENERAL STORE)
        String category = "GENERAL STORE";
        if (parsedData != null && parsedData.getMajorActivity() != null && !parsedData.getMajorActivity().isBlank()) {
            category = MsmeDataParser.mapToDukaanLockerCategory(parsedData.getMajorActivity());
        }
        shop.setCategory(category.toUpperCase());

        // Scale = Enterprise Type (Micro/Small/Medium -> BUSINESS_SCALE enum)
        BusinessScale scale = BusinessScale.MICRO; // default
        if (parsedData != null && parsedData.getEnterpriseType() != null) {
            try {
                scale = BusinessScale.valueOf(parsedData.getEnterpriseType().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                // Try partial match
                String typeLower = parsedData.getEnterpriseType().toLowerCase();
                if (typeLower.contains("small")) {
                    scale = BusinessScale.SMALL;
                } else if (typeLower.contains("medium")) {
                    scale = BusinessScale.MEDIUM;
                } else if (typeLower.contains("large")) {
                    scale = BusinessScale.LARGE;
                } else {
                    scale = BusinessScale.MICRO;
                }
            }
        }
        shop.setScale(scale);

        // State, City, District, Pincode, Address with sensible defaults
        if (parsedData != null) {
            shop.setState(parsedData.getState() != null && !parsedData.getState().isBlank()
                         ? parsedData.getState() : "India");
            shop.setCity(parsedData.getCity() != null && !parsedData.getCity().isBlank() ? parsedData.getCity() :
                         (parsedData.getDistrict() != null && !parsedData.getDistrict().isBlank()
                          ? parsedData.getDistrict() : "Not Specified"));
            shop.setAddress(parsedData.getAddress() != null ? parsedData.getAddress() : "");
            shop.setPincode(parsedData.getPincode());
        } else {
            shop.setState("India");
            shop.setCity("Not Specified");
            shop.setAddress("");
        }

        shop.setOwner(user);

        return shopRepository.save(shop);
    }

    /**
     * Creates all required document records for the shop.
     * MSME document is marked as VERIFIED with the PDF URL.
     * All other required documents are marked as NOT_UPLOADED.
     */
    private void createRequiredDocuments(Shop shop, String pdfUrl, String udyamNumber) {
        // Get required document types for this shop's category and scale
        Set<DocumentType> requiredTypes = requiredDocumentService.getRequiredDocuments(
                shop.getCategory(), shop.getScale());

        LocalDateTime now = LocalDateTime.now();

        for (DocumentType type : requiredTypes) {
            Document doc = new Document(shop, type);

            if (type == DocumentType.MSME_CERTIFICATE) {
                // MSME certificate is uploaded with the PDF from S3
                doc.setStatus(DocumentStatus.UPLOADED);
                doc.setDocumentNumber(udyamNumber);
                doc.setFileUrl(pdfUrl);
                doc.setFileName("Udyam_Certificate_" + udyamNumber + ".pdf");
                doc.setIssueDate(now);
            } else {
                // Other documents are pending upload
                doc.setStatus(DocumentStatus.NOT_UPLOADED);
            }

            doc.setUploadedAt(now);
            doc.setUpdatedAt(now);
            documentRepository.save(doc);
        }
    }

    private static String normalizeEmail(String raw) {
        return raw == null ? null : raw.trim().toLowerCase();
    }
}
