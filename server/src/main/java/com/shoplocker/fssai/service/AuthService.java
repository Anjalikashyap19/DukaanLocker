package com.shoplocker.fssai.service;

import com.shoplocker.fssai.dto.AuthResponse;
import com.shoplocker.fssai.dto.BiometricLoginRequest;
import com.shoplocker.fssai.dto.GoogleRegisterRequest;
import com.shoplocker.fssai.dto.LoginRequest;
import com.shoplocker.fssai.dto.ManagerCodeLoginRequest;
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
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
    private final GoogleOAuthService googleOAuthService;
    private final LoginAttemptService loginAttemptService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       ShopRepository shopRepository,
                       DocumentRepository documentRepository,
                       RequiredDocumentService requiredDocumentService,
                       GoogleOAuthService googleOAuthService,
                       LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.shopRepository = shopRepository;
        this.documentRepository = documentRepository;
        this.requiredDocumentService = requiredDocumentService;
        this.googleOAuthService = googleOAuthService;
        this.loginAttemptService = loginAttemptService;
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

        // MSME-registered users are not permitted to log in with email + password.
        // They must authenticate via their Udyam number + OTP flow instead.
        if (user.isMsmeUser()) {
            log.info("Login rejected: MSME user {} attempted email+password login", email);
            throw new FssaiException(
                    "MSME-registered accounts cannot log in with email and password. " +
                    "Please sign in with your Udyam (MSME) number and OTP.",
                    FailureCode.MSME_LOGIN_NOT_ALLOWED);
        }

        String token = jwtService.generateToken(user);
        return AuthResponse.from(user, token);
    }

    /**
     * Login a manager using their unique 6-character access code.
     * No password required - the code is the only credential.
     *
     * @param request contains the 6-character manager code
     * @return AuthResponse with JWT token
     */
    /**
     * Register a new user via Google account, or login if email already exists.
     * Uses Firebase UID as a unique identifier. No password required.
     *
     * <p>For a login-only flow that never creates accounts, see
     * {@link #loginWithGoogle(GoogleRegisterRequest)}.</p>
     *
     * @param request contains firebaseUid, userName, emailId from Google
     * @return AuthResponse with JWT token
     */
    @Transactional
    public AuthResponse registerWithGoogle(GoogleRegisterRequest request) {
        String name = request.getUserName() != null ? request.getUserName().trim() : "Google User";

        // Verify the Google ID token server-side. The token's verified email is
        // canonical — the emailId in the request is only trusted after it matches.
        VerifiedGoogleToken verified = verifyGoogleRequest(request);
        String email = normalizeEmail(verified.email());

        // Check if user already exists with this email
        java.util.Optional<User> existingUser = userRepository.findByEmailId(email);
        if (existingUser.isPresent()) {
            // User already exists — just log them in
            User user = existingUser.get();
            String token = jwtService.generateToken(user);
            log.info("Google login: existing user email={}", email);
            return AuthResponse.from(user, token);
        }

        // Create new user from Google account
        User user = new User();
        user.setUserName(name);
        user.setEmailId(email);
        user.setMobileNumber(generateUniqueMobile());
        // Google users don't have a password — use a random hash that can't be matched
        user.setPassword(passwordEncoder.encode("google-oauth-no-password-" + verified.subject()));
        user.setRole(Role.ADMIN);
        user.setEnabled(true);

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved);

        log.info("Google registration: userId={} email={}", saved.getId(), email);
        return AuthResponse.from(saved, token);
    }

    /**
     * Login an EXISTING user via Google account. Never creates an account: if
     * the email has no registered user, throws USER_NOT_FOUND (HTTP 404) so
     * the client can direct the user to register through another flow.
     *
     * @param request contains firebaseUid, userName, emailId from Google
     * @return AuthResponse with JWT token
     */
    @Transactional
    public AuthResponse loginWithGoogle(GoogleRegisterRequest request) {
        VerifiedGoogleToken verified = verifyGoogleRequest(request);
        String email = normalizeEmail(verified.email());

        User user = userRepository.findByEmailId(email)
                .orElseThrow(() -> new FssaiException(
                        "No account found with this Google email. Please register with email/password first.",
                        FailureCode.USER_NOT_FOUND));

        String token = jwtService.generateToken(user);
        log.info("Google login: existing user email={}", email);
        return AuthResponse.from(user, token);
    }

    /**
     * Verify the Google ID token server-side and enforce that the token's
     * verified email matches the emailId in the request. The token's verified
     * email is canonical — the request email is only trusted after it matches.
     *
     * @param request Google credentials from the client
     * @return the verified token claims (email, subject)
     * @throws FssaiException with INVALID_REQUEST if verification fails or the emails mismatch
     */
    private VerifiedGoogleToken verifyGoogleRequest(GoogleRegisterRequest request) {
        VerifiedGoogleToken verified = googleOAuthService.verify(request.getIdToken());
        String email = normalizeEmail(verified.email());
        if (email == null || email.isBlank()) {
            throw new FssaiException("Google authentication failed", FailureCode.INVALID_REQUEST);
        }
        if (!email.equals(normalizeEmail(request.getEmailId()))) {
            log.warn("Google token email mismatch: token={} request={}", email, request.getEmailId());
            throw new FssaiException("Google authentication failed", FailureCode.INVALID_REQUEST);
        }
        return verified;
    }

    /**
     * Generate a unique 10-digit mobile number for Google users.
     * Uses timestamp-based generation to avoid collisions.
     */
    private String generateUniqueMobile() {
        String mobile;
        int attempts = 0;
        do {
            long timestamp = System.currentTimeMillis() % 10000000000L;
            mobile = String.format("%010d", timestamp + attempts);
            attempts++;
        } while (userRepository.existsByMobileNumber(mobile) && attempts < 100);
        return mobile;
    }

    public AuthResponse loginByCode(ManagerCodeLoginRequest request, String clientIp) {
        String code = request.getManagerCode().trim().toUpperCase();

        String codeKey = "code:" + code;
        String ipKey = "ip:" + (clientIp == null ? "unknown" : clientIp);

        if (loginAttemptService.isLocked(codeKey) || loginAttemptService.isLocked(ipKey)) {
            log.info("Manager login rejected: too many failed attempts");
            throw new FssaiException(
                    "Too many failed attempts. Please try again in 15 minutes.",
                    FailureCode.TOO_MANY_ATTEMPTS);
        }

        User user = userRepository.findByManagerCode(code)
                .orElseThrow(() -> {
                    loginAttemptService.registerFailure(codeKey);
                    loginAttemptService.registerFailure(ipKey);
                    log.info("Manager login failed: invalid code");
                    return new FssaiException(
                            "Invalid access code",
                            FailureCode.INVALID_CREDENTIALS);
                });

        if (user.getRole() != Role.MANAGER) {
            loginAttemptService.registerFailure(codeKey);
            loginAttemptService.registerFailure(ipKey);
            log.info("Manager login failed: user is not a manager");
            throw new FssaiException(
                    "Invalid access code",
                    FailureCode.INVALID_CREDENTIALS);
        }

        if (!user.isEnabled()) {
            log.info("Manager login failed: disabled account");
            throw new FssaiException(
                    "This account has been disabled. Please contact support.",
                    FailureCode.DISABLED_USER);
        }

        loginAttemptService.reset(codeKey);
        loginAttemptService.reset(ipKey);

        String token = jwtService.generateToken(user);
        log.info("Manager logged in via code: userId={}", user.getId());
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
                log.info("Parsed Email = {}", parsedData.getEmailId());
                log.info("Parsed Owner = {}", parsedData.getEntrepreneurName());

                log.info("Parsed MSME data: enterprise={}, owner={}",
                        parsedData.getEnterpriseName(), parsedData.getEntrepreneurName());
            } catch (Exception e) {
                log.warn("Failed to parse MSME HTML, using fallback data: {}", e.getMessage());
            }
        }

        // ── Step 3: Create User with parsed data ──
        User savedUser = createMsmeUser(mobile, parsedData);
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
     * MSME-registered users authenticate via their Udyam number + OTP, so they
     * have NO email and NO password — {@code emailId} and {@code password} stay
     * null. The mobile number is the account's stable identifier.
     * Falls back to enterprise name or generic "MSME Owner" if entrepreneur name is missing.
     */
    private User createMsmeUser(String mobile, MsmeParsedData parsedData) {
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

        User user = new User();
        user.setUserName(userName);
        user.setMobileNumber(mobile);
        user.setEmailId(null);
        user.setPassword(null);
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        user.setMsmeUser(true);

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

    /**
     * Biometric login - issues a fresh JWT token after biometric authentication.
     * The client has already authenticated via biometric (CryptoObject) and decrypted
     * stored credentials. This endpoint validates the userId and emailId match,
     * then issues a new JWT token.
     *
     * @param request contains userId and emailId from decrypted biometric credentials
     * @return AuthResponse with fresh JWT token
     */
    @Transactional(readOnly = true)
    public AuthResponse biometricLogin(BiometricLoginRequest request) {
        Long userId = request.getUserId();
        String email = normalizeEmail(request.getEmailId());
        String proofToken = request.getToken();

        if (userId == null || email == null || email.isBlank()
                || proofToken == null || proofToken.isBlank()) {
            throw new FssaiException("Invalid biometric login request", FailureCode.INVALID_REQUEST);
        }

        // Verify the client possesses a server-issued JWT bound to this account.
        // The signature is always verified; expiration is tolerated so a stored
        // proof token stays usable between sessions.
        Claims claims;
        try {
            claims = jwtService.parseAllowExpired(proofToken);
        } catch (JwtException e) {
            log.info("Biometric login failed: invalid proof token for userId={}", userId);
            throw new FssaiException(
                    "Invalid biometric credentials",
                    FailureCode.INVALID_CREDENTIALS);
        }
        if (!email.equalsIgnoreCase(claims.getSubject())) {
            log.info("Biometric login failed: proof token subject mismatch for userId={}", userId);
            throw new FssaiException(
                    "Invalid biometric credentials",
                    FailureCode.INVALID_CREDENTIALS);
        }

        // Find user by ID and verify email matches
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.info("Biometric login failed: user not found for userId={}", userId);
                    return new FssaiException(
                            "User not found",
                            FailureCode.INVALID_CREDENTIALS);
                });

        if (!user.getEmailId().equals(email)) {
            log.info("Biometric login failed: email mismatch for userId={}", userId);
            throw new FssaiException(
                    "Invalid biometric credentials",
                    FailureCode.INVALID_CREDENTIALS);
        }

        if (!user.isEnabled()) {
            log.info("Biometric login failed: disabled account for userId={}", userId);
            throw new FssaiException(
                    "This account has been disabled. Please contact support.",
                    FailureCode.DISABLED_USER);
        }

        // MSME-registered users authenticate via Udyam number + OTP, not biometrics.
        if (user.isMsmeUser()) {
            log.info("Biometric login rejected: MSME user userId={}", userId);
            throw new FssaiException(
                    "MSME-registered accounts cannot use biometric login. " +
                    "Please sign in with your Udyam (MSME) number and OTP.",
                    FailureCode.MSME_LOGIN_NOT_ALLOWED);
        }

        // Issue fresh JWT token
        String token = jwtService.generateToken(user);
        log.info("Biometric login successful: userId={} email={}", userId, email);
        return AuthResponse.from(user, token);
    }

    private static String normalizeEmail(String raw) {
        return raw == null ? null : raw.trim().toLowerCase();
    }
}
