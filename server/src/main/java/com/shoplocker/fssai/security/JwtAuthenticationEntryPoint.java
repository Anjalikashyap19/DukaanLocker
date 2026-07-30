package com.shoplocker.fssai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplocker.fssai.dto.FssaiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Renders Spring Security's "unauthenticated request reached a protected
 * resource" rejection as our standard {@link FssaiErrorResponse} JSON shape
 * (the same envelope {@link com.shoplocker.fssai.exception.GlobalExceptionHandler}
 * uses for the rest of the API). Without this, requests with no/invalid
 * tokens would get Spring's default empty 401 — not consistent with the
 * rest of the API.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        FssaiErrorResponse body = FssaiErrorResponse.builder()
                .status(HttpServletResponse.SC_UNAUTHORIZED)
                .code("unauthorized")
                .message("Authentication is required to access this resource. " +
                         "Provide a valid Bearer token via the Authorization header.")
                .build();
        objectMapper.writeValue(response.getWriter(), body);
    }
}
