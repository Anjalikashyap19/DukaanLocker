package com.shoplocker.fssai.service;

import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Server-side verification of Google ID tokens.
 *
 * <p>Google's {@code tokeninfo} endpoint validates the token signature against
 * Google's public keys and returns the token claims. We then enforce:
 * <ul>
 *   <li>issuer is {@code accounts.google.com}</li>
 *   <li>{@code aud} matches the configured Firebase web client ID (when configured)</li>
 *   <li>{@code email_verified} is {@code true}</li>
 *   <li>token is not expired</li>
 * </ul>
 * The verified {@code email} claim is the canonical identity — callers must never
 * trust the {@code emailId} sent in the request body.</p>
 */
@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    private static final String TOKENINFO_HOST = "oauth2.googleapis.com";
    private static final String TOKENINFO_PATH = "/tokeninfo";
    private static final String ISSUER_ACCOUNTS_GOOGLE = "accounts.google.com";
    private static final String ISSUER_ACCOUNTS_GOOGLE_HTTPS = "https://accounts.google.com";

    private final RestClient restClient = RestClient.create();

    @Value("${app.google.web-client-id:}")
    private String expectedAudience;

    @PostConstruct
    void init() {
        if (expectedAudience == null || expectedAudience.isBlank()) {
            log.warn("app.google.web-client-id is not configured; Google ID token 'aud' claim will NOT be checked. "
                    + "Set GOOGLE_WEB_CLIENT_ID to your Firebase web client ID to fully harden Google sign-in.");
        }
    }

    /**
     * Verifies a Google ID token and returns the verified claims.
     *
     * @throws FssaiException with {@link FailureCode#INVALID_REQUEST} if the token
     *         is missing, malformed, expired, or fails any verification check.
     */
    public VerifiedGoogleToken verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw invalidToken();
        }

        Map<String, String> claims;
        try {
            claims = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host(TOKENINFO_HOST)
                            .path(TOKENINFO_PATH)
                            .queryParam("id_token", idToken)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Google tokeninfo request failed: {}", e.getMessage());
            throw invalidToken();
        }

        if (claims == null
                || (!ISSUER_ACCOUNTS_GOOGLE.equals(claims.get("iss"))
                    && !ISSUER_ACCOUNTS_GOOGLE_HTTPS.equals(claims.get("iss")))) {
            log.warn("Google ID token rejected: invalid issuer");
            throw invalidToken();
        }

        if (expectedAudience != null && !expectedAudience.isBlank()
                && !expectedAudience.equals(claims.get("aud"))) {
            log.warn("Google ID token rejected: audience mismatch");
            throw invalidToken();
        }

        if (!"true".equals(claims.get("email_verified"))) {
            log.warn("Google ID token rejected: email not verified");
            throw invalidToken();
        }

        long exp;
        try {
            exp = Long.parseLong(claims.get("exp"));
        } catch (Exception e) {
            log.warn("Google ID token rejected: missing/invalid exp");
            throw invalidToken();
        }
        if (System.currentTimeMillis() / 1000L >= exp) {
            log.warn("Google ID token rejected: expired");
            throw invalidToken();
        }

        return new VerifiedGoogleToken(
                claims.get("sub"),
                claims.get("email"),
                "true".equals(claims.get("email_verified")));
    }

    private static FssaiException invalidToken() {
        return new FssaiException("Google authentication failed", FailureCode.INVALID_REQUEST);
    }
}
