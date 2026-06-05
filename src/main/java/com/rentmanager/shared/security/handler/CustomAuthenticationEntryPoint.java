package com.rentmanager.shared.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException ex) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");

        ApiResponse<Void> body = ApiResponse.fail(
                "Authentication required",
                ErrorCode.UNAUTHORIZED.name()
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}