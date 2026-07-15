package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.AuthResponse;
import com.shoplocker.fssai.dto.LoginRequest;
import com.shoplocker.fssai.dto.RegisterRequest;
import com.shoplocker.fssai.service.AuthService;
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

    public AuthController(AuthService authService) {
        this.authService = authService;
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
}
