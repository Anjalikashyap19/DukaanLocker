package com.shoplocker.fssai.security;

import com.shoplocker.fssai.entity.Role;
import com.shoplocker.fssai.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Issues and validates HS256-signed JWTs.
 *
 * <p>Token shape:</p>
 * <ul>
 *   <li>{@code sub} = user's {@code emailId} (used as principal name)</li>
 *   <li>custom claims: {@code userId}, {@code userName}, {@code role}</li>
 *   <li>{@code iat} / {@code exp} = issued-at / expiry (epoch millis)</li>
 *   <li>signed with HS256 using a secret loaded from {@code jwt.secret}.</li>
 * </ul>
 *
 * <p>The secret is sourced from the {@code JWT_SECRET} environment variable.
 * HS256 requires ≥32 bytes (256 bits) of key material — we fail the Spring
 * context at startup if the configured secret is shorter so this surfaces
 * as a configuration error, not as a runtime 401.</p>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** HS256 requires ≥256-bit (32-byte) secret. */
    private static final int MIN_SECRET_LENGTH_BYTES = 32;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        // Level-gated diagnostic print BEFORE any throw so the message lands
        // in the JVM log even if Spring aborts context initialization
        // immediately after. Crucially this logs resolved length but never
        // the secret value. INFO level so it ships in every startup log.
        log.info("JwtService init: jwt.secret present={}, length={}, expirationMs={}",
                secret != null && !secret.isBlank(),
                secret == null ? 0 : secret.length(),
                expirationMs);

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret is not configured. Set the JWT_SECRET env var " +
                    "(>= 32 chars for HS256) before starting the application.");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret must be at least " + MIN_SECRET_LENGTH_BYTES +
                    " bytes (HS256 / 256-bit). Configured length: " + keyBytes.length + " bytes.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /** Builds and signs a JWT for the given user. */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("userName", user.getUserName());
        claims.put("role", user.getRole() == null ? null : user.getRole().name());

        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claims(claims)
                .subject(user.getEmailId())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Parses the token and returns the {@link Claims} body.
     *
     * @throws JwtException if the signature is invalid, the token is
     *         malformed, or the token is expired.
     */
    public Claims parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Parses a token's claims tolerating an expired {@code exp}.
     *
     * <p>The signature is ALWAYS verified — jjwt only throws
     * {@code ExpiredJwtException} after signature validation succeeds — so the
     * returned claims are genuine even when expired. Used by the biometric login
     * flow where the stored proof JWT may have expired between app sessions but
     * still proves possession of a server-issued token.</p>
     *
     * @throws JwtException if the signature is invalid or the token is malformed.
     */
    public Claims parseAllowExpired(String token) throws JwtException {
        try {
            return parse(token);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    /** Convenience for callers that want a header-friendly role string. */
    public static String roleAuthority(Role role) {
        return role == null ? "ROLE_USER" : "ROLE_" + role.name();
    }
}
