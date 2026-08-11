package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.AuthResponse;
import com.shoplocker.fssai.dto.GoogleRegisterRequest;
import com.shoplocker.fssai.dto.LoginRequest;
import com.shoplocker.fssai.dto.ManagerCodeLoginRequest;
import com.shoplocker.fssai.dto.MsmeAuthResponse;
import com.shoplocker.fssai.dto.RegisterRequest;
import com.shoplocker.fssai.dto.RegisterWithMsmeRequest;
import com.shoplocker.fssai.service.AuthService;
import com.shoplocker.fssai.service.UdyamVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
            summary = "Manager login by access code",
            description = "Authenticates a manager using their unique 6-character access code. " +
                          "No password required. The code is assigned by the business owner when " +
                          "creating the manager."
    )
    @SecurityRequirements
    @PostMapping("/login-by-code")
    public ResponseEntity<AuthResponse> loginByCode(
            @Valid @RequestBody ManagerCodeLoginRequest request) {
        AuthResponse response = authService.loginByCode(request);
        return ResponseEntity.ok(response);
    }
}
