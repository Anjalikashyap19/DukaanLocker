package com.shoplocker.fssai.security;

import com.shoplocker.fssai.dto.FssaiErrorResponse;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reads {@code Authorization: Bearer <jwt>}, validates it via
 * {@link JwtService}, and populates {@link SecurityContextHolder} with an
 * authenticated {@link UsernamePasswordAuthenticationToken} carrying the
 * user's role as {@code ROLE_<role>}.
 *
 * <p>Public endpoints (matched by {@code SecurityConfig}) never reach here
 * with a token since the caller is allowed to bypass auth, but if a token
 * IS present and is malformed/expired we still surface a clean
 * {@link FssaiErrorResponse} JSON shape via the entry point rather than
 * letting Spring's defaults return an empty 401.</p>
 *
 * <p>Also enforces a 15-day inactivity window: if the user's
 * {@code lastActiveAt} is older than 15 days, the token is rejected even
 * if it hasn't reached its absolute JWT expiry. Every successful
 * authenticated request resets the inactivity clock.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Duration INACTIVITY_TIMEOUT = Duration.ofDays(15);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserDetailsService userDetailsService,
                                   UserRepository userRepository,
                                   ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "unauthorized", "Authorization header is missing a token.");
            return;
        }

        try {
            Claims claims = jwtService.parse(token);
            String email = claims.getSubject();
            if (email == null || email.isBlank()) {
                chain.doFilter(request, response);
                return;
            }

            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(email);
            } catch (org.springframework.security.core.userdetails.UsernameNotFoundException unf) {
                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "unauthorized", "Token references an unknown user.");
                return;
            }

            // Defense-in-depth: prefer the role baked into the JWT claim over
            // any role that might have been changed in the DB after this token
            // was issued. If the claim is missing, fall back to the authorities
            // loaded from the {@code UserDetailsService}.
            List<SimpleGrantedAuthority> authorities;
            Object roleClaim = claims.get("role");
            if (roleClaim instanceof String roleStr && !roleStr.isBlank()) {
                String authority = roleStr.startsWith("ROLE_") ? roleStr : "ROLE_" + roleStr;
                authorities = List.of(new SimpleGrantedAuthority(authority));
            } else {
                authorities = userDetails.getAuthorities().stream()
                        .map(a -> new SimpleGrantedAuthority(a.getAuthority()))
                        .toList();
            }

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);

            // ── Sliding inactivity window ──────────────────────────────────
            // If the user has been inactive for more than 15 days, reject the
            // request even though the JWT itself hasn't expired. This gives
            // active users an indefinite session while forcing re-login after
            // prolonged inactivity.
            try {
                User user = userRepository.findByEmailId(email).orElse(null);
                if (user == null) {
                    // Fallback: for MSME users the subject is a mobile number
                    user = userRepository.findByMobileNumber(email).orElse(null);
                }
                if (user != null) {
                    LocalDateTime lastActive = user.getLastActiveAt();
                    if (lastActive != null && Duration.between(lastActive, LocalDateTime.now()).compareTo(INACTIVITY_TIMEOUT) > 0) {
                        log.info("User {} inactive for more than {} days, forcing re-login",
                                email, INACTIVITY_TIMEOUT.toDays());
                        writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                "session_expired",
                                "Your session has expired due to inactivity. Please login again.");
                        return;
                    }
                    user.setLastActiveAt(LocalDateTime.now());
                    userRepository.save(user);
                }
            } catch (Exception e) {
                log.warn("Failed to update lastActiveAt for user {}: {}", email, e.getMessage());
            }

            chain.doFilter(request, response);
        } catch (JwtException ex) {
            // Signature invalid, malformed, or expired. Surface a clean 401 with
            // our standard error envelope — never echo internal JJWT messages.
            log.info("JWT validation failed: {}", ex.getMessage());
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "unauthorized", "Invalid or expired token. Please login again.");
        }
    }

    private void writeJsonError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        FssaiErrorResponse body = FssaiErrorResponse.builder()
                .status(status)
                .code(code)
                .message(message)
                .build();
        objectMapper.writeValue(response.getWriter(), body);
    }
}
