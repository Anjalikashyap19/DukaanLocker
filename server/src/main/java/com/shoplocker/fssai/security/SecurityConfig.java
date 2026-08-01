package com.shoplocker.fssai.security;

import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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
 *   <li>Public endpoints: /api/auth/**, Swagger UI, OpenAPI spec, H2 console.</li>
 *   <li>ADMIN-only endpoints: shop creation (POST /api/shops), my-shops listing,
 *       manager CRUD, manager-shop assignments.</li>
 *   <li>MANAGER-only endpoint: GET /api/managers/me/shops (listed BEFORE the
 *       ADMIN catch-all so the manager path doesn't get intercepted).</li>
 *   <li>Authenticated (non-role-specific): document listing and upload/re-upload
 *       — access validation happens in the service layer via
 *       {@code ShopAccessService}.</li>
 *   <li>{@link JwtAuthenticationEntryPoint} + {@link JwtAccessDeniedHandler}
 *       shape the 401/403 responses into the standard {@code FssaiErrorResponse}
 *       JSON envelope.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Value("${app.external-url:http://localhost:8081}")
    private String externalUrl;

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
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                    .accessDeniedHandler(jwtAccessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                    // ── Public (no JWT required) ──────────────────────────────
                    .requestMatchers(
                            "/api/auth/**",
                            "/api/udyam/**",
                            "/api/location/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html",
                            "/v3/api-docs",
                            "/v3/api-docs/**",
                            "/webjars/**",
                            "/h2-console/**",
                            "/actuator/**",
                            "/error"
                    ).permitAll()
                    // ── MANAGER-only (must be BEFORE the ADMIN /api/managers/** catch-all) ──
                    .requestMatchers(HttpMethod.GET, "/api/managers/me/shops").hasRole("MANAGER")
                    // ── ADMIN-only endpoints ──────────────────────────────────
                    .requestMatchers(HttpMethod.POST, "/api/shops").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/shops/my-shops").hasRole("ADMIN")
                    .requestMatchers("/api/managers/**").hasRole("ADMIN")
                    .requestMatchers("/users/**").hasRole("ADMIN")
                    // ── Authenticated (any valid JWT) — access checked in service layer ──
                    .requestMatchers("/api/business-profile/**").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/shops/*").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/shops/*/documents").authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/shops/*/documents/**").authenticated()
                    // Default: any other application endpoint requires authentication
                    .anyRequest().authenticated())
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(externalUrl, "http://localhost:8081"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
