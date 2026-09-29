package com.jewellery.erp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Returns a JSON 403 when an authenticated user lacks the required permission.
 *
 * <p>The denial is logged with the username and target so that "why can this
 * user not do X" is answerable from the logs, but the response itself stays
 * generic.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {

        log.warn("Access denied for user '{}' on {} {}",
                SecurityUtils.authenticatedName().orElse("anonymous"),
                request.getMethod(),
                request.getRequestURI());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiErrorResponse.of(
                        HttpStatus.FORBIDDEN.value(),
                        "Forbidden",
                        ErrorCode.FORBIDDEN.name(),
                        "You do not have permission to perform this action.",
                        request.getRequestURI(),
                        null));
    }
}
