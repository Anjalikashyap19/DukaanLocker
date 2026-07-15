package com.shoplocker.fssai.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless Spring Security wiring.
 *
 * <ul>
 *   <li>CSRF disabled — JWT-only REST API has no surface for CSRF.</li>
 *   <li>{@link SessionCreationPolicy#STATELESS} — no HTTP session is ever
 *       created or used; each request carries its own JWT.</li>
 *   <li>{@link JwtAuthenticationFilter} runs before
 *       {@link UsernamePasswordAuthenticationFilter} so a valid Bearer token
 *       populates {@code SecurityContextHolder} ahead of any username/password
 *       flow.</li>
 *   <li>Public matchers: {@code /api/auth/**}, {@code /swagger-ui/**},
 *       {@code /swagger-ui.html}, {@code /v3/api-docs} (exact),
 *       {@code /v3/api-docs/**}, {@code /webjars/**}. The trailing-wildcard
 *       matcher does <strong>not</strong> match the base path under Spring
 *       Boot 3's {@code PathPatternParser} so both the exact and wildcard
 *       patterns must be registered — otherwise Swagger UI cannot fetch the
 *       OpenAPI JSON and the page hangs on a 403.</li>
 *   <li>Shop creation and user-management endpoints require
 *       {@code ROLE_ADMIN}.</li>
 *   <li>{@link JwtAuthenticationEntryPoint} + {@link JwtAccessDeniedHandler}
 *       shape the 401/403 responses into the standard {@code FssaiErrorResponse}
 *       JSON envelope so they line up with the rest of the API.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                          JwtAccessDeniedHandler jwtAccessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                  AuthenticationProvider authenticationProvider) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Spring Security 6's default HeadersConfigurer installs
            // `X-Frame-Options: DENY` on every response. That blocks Swagger
            // UI from rendering inside any iframe (admin UI embeds, browser
            // tooling, reverse proxies / ALB / CloudFront that re-frame), which
            // is a frequent cause of "Swagger is not opening". Allow same-origin
            // framing so the UI is at least usable in our own admin embeds while
            // still being denied from cross-origin clickjacking surfaces.
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                    .accessDeniedHandler(jwtAccessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/api/auth/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html",
                            // Spring Boot 3 uses PathPatternParser by default;
                            // the trailing-wildcard matcher does NOT match the
                            // base path, so register `/v3/api-docs` exact too.
                            // Without this the Swagger UI JS hangs on a 401/403
                            // while fetching the OpenAPI JSON.
                            "/v3/api-docs",
                            "/v3/api-docs/**",
                            "/webjars/**",
                            "/actuator/**",
                            "/error"
                    ).permitAll()
                    // Shop creation requires ADMIN; reading/updating/deleting a shop
                    // is any-authenticated-user (the existing flow is read-mostly,
                    // so we keep it simple — owners-only checks happen in the
                    // service layer once the linking relationship is in place).
                    .requestMatchers(HttpMethod.POST, "/shops").hasRole("ADMIN")
                    // Existing UserController routes are kept for future
                    // profile-management: lock down to ADMIN to avoid leaking
                    // user records via the unprotected CRUD.
                    .requestMatchers("/users/**").hasRole("ADMIN")
                    // Default: any other application endpoint requires a valid
                    // JWT. Public endpoints get bypassed above.
                    .anyRequest().authenticated())
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Wires DaoAuthenticationProvider against our
     * {@link com.shoplocker.fssai.service.CustomUserDetailsService} and the
     * BCrypt encoder so {@code AuthenticationManager#authenticate(...)} in
     * {@link com.shoplocker.fssai.service.AuthService} works without any
     * manual password comparison.
     *
     * <p>Spring will automatically fail disabled accounts BEFORE the password
     * check runs (so disabled-user detection is by-design via the
     * {@code UserDetails#isEnabled()} flag set in
     * {@code CustomUserDetailsService}).</p>
     *
     * <p>The factory method takes {@link UserDetailsService} and
     * {@link PasswordEncoder} as parameters so Spring injects the singleton
     * instances — we deliberately do NOT pull them into the
     * {@code SecurityConfig} constructor (that would create a circular
     * dependency since {@code PasswordEncoder} is itself defined by this
     * configuration class as a {@code @Bean}).</p>
     */
    @Bean
    public AuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * AuthenticationConfiguration-driven AuthenticationManager so
     * {@link com.shoplocker.fssai.service.AuthService} can authenticate
     * users against the {@link AuthenticationProvider} above.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
