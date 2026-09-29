package com.jewellery.erp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Returns a JSON 401 for unauthenticated calls to a protected endpoint.
 *
 * <p>Without this, Spring Security would answer with an HTML login redirect,
 * which a SPA cannot act on.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiErrorResponse.of(
                        HttpStatus.UNAUTHORIZED.value(),
                        "Unauthorized",
                        ErrorCode.UNAUTHORIZED.name(),
                        "Authentication is required to access this resource.",
                        request.getRequestURI(),
                        null));
    }
}
