package com.shoplocker.fssai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplocker.fssai.dto.FssaiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Renders Spring Security's "authenticated but lacks role" rejection as
 * our standard {@link FssaiErrorResponse} JSON shape. The default
 * AccessDeniedHandler either returns an empty 403 or an error page, which
 * is inconsistent with the {@code FssaiErrorResponse} envelope the rest of
 * the API uses.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        FssaiErrorResponse body = FssaiErrorResponse.builder()
                .status(HttpServletResponse.SC_FORBIDDEN)
                .code("forbidden")
                .message("You do not have permission to access this resource.")
                .build();
        objectMapper.writeValue(response.getWriter(), body);
    }
}
