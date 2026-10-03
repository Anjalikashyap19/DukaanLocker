package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.AuthResponse;
import com.shoplocker.fssai.dto.BiometricLoginRequest;
import com.shoplocker.fssai.dto.ForgotPasswordRequest;
import com.shoplocker.fssai.dto.GoogleRegisterRequest;
import com.shoplocker.fssai.dto.LoginRequest;
import com.shoplocker.fssai.dto.ManagerCodeLoginRequest;
import com.shoplocker.fssai.dto.MessageResponse;
import com.shoplocker.fssai.dto.MobileHintRequest;
import com.shoplocker.fssai.dto.MobileHintResponse;
import com.shoplocker.fssai.dto.MsmeAuthResponse;
import com.shoplocker.fssai.dto.MsmeOtpRequest;
import com.shoplocker.fssai.dto.MsmeOtpVerifyRequest;
import com.shoplocker.fssai.dto.MsmeOtpResponse;
import com.shoplocker.fssai.dto.OtpSendResponse;
import com.shoplocker.fssai.dto.RegisterRequest;
import com.shoplocker.fssai.dto.RegisterWithMsmeRequest;
import com.shoplocker.fssai.dto.ResetPasswordRequest;
import com.shoplocker.fssai.service.AuthService;
import com.shoplocker.fssai.service.UdyamVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints. Both endpoints intentionally bypass JWT
 * authentication — {@code /api/auth/**} is whitelisted in
 * {@code SecurityConfig}.
 *
 * <p>Per the security plan, registration ALWAYS issues an ADMIN role.
 * There is no public path to create a MANAGER user — that role is reserved
 * for admin-side flows (out of scope for this implementation).</p>
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Public authentication — register and login.")
public class AuthController {

    private final AuthService authService;
    private final UdyamVerificationService udyamService;

    public AuthController(AuthService authService, UdyamVerificationService udyamService) {
        this.authService = authService;
        this.udyamService = udyamService;
    }

    @Operation(
            summary = "Register a new user",
            description = "Creates a new account with role=ADMIN. The password is BCrypt-encoded " +
                          "server-side. The role cannot be set by the client.")
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Login",
            description = "Authenticates by emailId + password and returns a JWT Bearer token. " +
                          "Use the returned token in subsequent calls via the Authorize button " +
                          "in Swagger UI (Bearer <token>) or directly in the Authorization header.")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Register with MSME (Udyam) verification",
            description = "Verifies the Udyam number against the government portal, " +
                          "generates a PDF certificate, and creates a new user account. " +
                          "Call /api/udyam/init first to obtain a sessionId and CAPTCHA."
    )
    @SecurityRequirements
    @PostMapping("/register-msme")
    public ResponseEntity<MsmeAuthResponse> registerWithMsme(
            @Valid @RequestBody RegisterWithMsmeRequest request) {
        MsmeAuthResponse response = authService.registerWithMsme(request, udyamService);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Register with Google account",
            description = "Creates a new account using Google authentication. " +
                          "If the email already exists, logs in the user instead. " +
                          "No password required — Firebase handles authentication."
    )
    @SecurityRequirements
    @PostMapping("/register-google")
    public ResponseEntity<AuthResponse> registerWithGoogle(
            @Valid @RequestBody GoogleRegisterRequest request) {
        AuthResponse response = authService.registerWithGoogle(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Login with Google account",
            description = "Logs in an EXISTING user using Google authentication. " +
                          "Does NOT create new accounts — returns 404 if the email is not " +
                          "registered, so clients can direct new users to register instead."
    )
    @SecurityRequirements
    @PostMapping("/login-google")
    public ResponseEntity<AuthResponse> loginWithGoogle(
            @Valid @RequestBody GoogleRegisterRequest request) {
        AuthResponse response = authService.loginWithGoogle(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Manager login by access code",
            description = "Authenticates a manager using their unique 6-character access code. " +
                          "No password required. The code is assigned by the business owner when " +
                          "creating the manager."
    )
    @SecurityRequirements
    @PostMapping("/login-by-code")
    public ResponseEntity<AuthResponse> loginByCode(
            @Valid @RequestBody ManagerCodeLoginRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.loginByCode(request, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Biometric login",
            description = "Issues a fresh JWT token after successful biometric authentication. " +
                          "The client must first authenticate via biometric (CryptoObject) and " +
                          "decrypt stored credentials. Then calls this endpoint with userId and emailId " +
                          "to get a new token."
    )
    @SecurityRequirements
    @PostMapping("/biometric-login")
    public ResponseEntity<AuthResponse> biometricLogin(
            @Valid @RequestBody BiometricLoginRequest request) {
        AuthResponse response = authService.biometricLogin(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "MSME login — request OTP",
            description = "MSME-registered users log in with their Udyam number + OTP (no email/password). " +
                          "Supplies the Udyam number; the server sends an OTP to the mobile registered at signup."
    )
    @SecurityRequirements
    @PostMapping("/msme-login-request")
    public ResponseEntity<MsmeOtpResponse> msmeLoginRequest(
            @Valid @RequestBody MsmeOtpRequest request) {
        MsmeOtpResponse response = authService.msmeLoginRequest(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "MSME login — verify OTP",
            description = "Verifies the OTP for the given Udyam number and returns a JWT Bearer token on success."
    )
    @SecurityRequirements
    @PostMapping("/msme-login-verify")
    public ResponseEntity<AuthResponse> msmeLoginVerify(
            @Valid @RequestBody MsmeOtpVerifyRequest request) {
        AuthResponse response = authService.msmeLoginVerify(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Forgot password — masked mobile hint",
            description = "Looks up the email typed on the sign-in form and returns the last three digits of the " +
                          "registered mobile for an account that may reset its password, so the user can recognise " +
                          "their own number when asked for it. Unknown emails, managers and MSME (Udyam) accounts " +
                          "all get an empty hint. Throttled per client IP."
    )
    @SecurityRequirements
    @PostMapping("/mobile-hint")
    public ResponseEntity<MobileHintResponse> mobileHint(
            @Valid @RequestBody MobileHintRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.mobileHint(request, clientIp(httpRequest)));
    }

    @Operation(
            summary = "Forgot password — request OTP",
            description = "Sends a one-time code to the registered mobile of an owner (ADMIN) account so its " +
                          "password can be reset. The response is identical whether or not the mobile is registered, " +
                          "so the endpoint cannot be used to enumerate accounts. Rate-limited like any other OTP send."
    )
    @SecurityRequirements
    @PostMapping("/forgot-password")
    public ResponseEntity<OtpSendResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        OtpSendResponse response = authService.forgotPassword(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Forgot password — reset with OTP",
            description = "Verifies the OTP sent to the registered mobile and stores the new BCrypt-encoded " +
                          "password. The OTP is checked before any account detail is revealed, so a caller must " +
                          "first prove control of the mobile. Password rules match registration."
    )
    @SecurityRequirements
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        MessageResponse response = authService.resetPassword(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Real client IP for throttling, as sent by the fronting nginx
     * ({@code X-Real-IP}), falling back to the socket address.
     *
     * <p>Safe to trust: the app's port is bound to localhost so only the local proxy
     * can reach it, and nginx overwrites the header from {@code $remote_addr} rather
     * than passing a client-supplied value through.</p>
     */
    private static String clientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        return (realIp == null || realIp.isBlank()) ? request.getRemoteAddr() : realIp.trim();
    }
}
