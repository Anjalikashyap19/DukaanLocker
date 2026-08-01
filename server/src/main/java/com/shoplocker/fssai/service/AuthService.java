package com.shoplocker.fssai.service;

import com.shoplocker.fssai.dto.AuthResponse;
import com.shoplocker.fssai.dto.LoginRequest;
import com.shoplocker.fssai.dto.MsmeAuthResponse;
import com.shoplocker.fssai.dto.RegisterRequest;
import com.shoplocker.fssai.dto.RegisterWithMsmeRequest;
import com.shoplocker.fssai.dto.UdyamVerifyRequest;
import com.shoplocker.fssai.dto.UdyamVerifyResponse;
import com.shoplocker.fssai.service.UdyamVerificationService;
import com.shoplocker.fssai.entity.Role;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.UserRepository;
import com.shoplocker.fssai.security.JwtService;
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

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
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
     * government portal.  On success, the user account is created and a PDF
     * certificate of the MSME registration is stored in S3.
     */
    @Transactional
    @Transactional
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

        // ── Step 1: Verify Udyam against government portal + generate PDF ──
        UdyamVerifyRequest verifyReq = new UdyamVerifyRequest();
        verifyReq.setSessionId(request.getSessionId());
        verifyReq.setUdyamNumber(request.getMsmeNumber().trim().toUpperCase());
        verifyReq.setCaptchaText(request.getCaptchaText());

        UdyamVerifyResponse verifyResult = udyamService.verifyAndGeneratePdf(verifyReq);

        if (!verifyResult.isSuccess()) {
            throw new FssaiException(
                    verifyResult.getErrorMessage() != null
                            ? verifyResult.getErrorMessage()
                            : "Udyam verification failed",
                    FailureCode.INVALID_REQUEST);
        }

        // ── Step 2: Register user (role = ADMIN, name extracted from Udyam number) ──
        String email = "msme_" + mobile + "@dukaanlocker.local";
        User user = new User();
        user.setUserName("MSME Owner");  // placeholder — can be updated later
        user.setMobileNumber(mobile);
        user.setEmailId(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.ADMIN);
        user.setEnabled(true);

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved);

        log.info("MSME user registered: id={} mobile={} udyam={}",
                saved.getId(), mobile, request.getMsmeNumber());

        AuthResponse auth = AuthResponse.from(saved, token);
        MsmeAuthResponse msmeAuth = new MsmeAuthResponse(
                auth, verifyResult.getPdfUrl(), request.getMsmeNumber());
        return msmeAuth;
    }

    private static String normalizeEmail(String raw) {
        return raw == null ? null : raw.trim().toLowerCase();
    }
}
