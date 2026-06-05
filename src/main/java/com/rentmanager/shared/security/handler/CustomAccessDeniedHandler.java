package com.rentmanager.shared.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;


@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {

        if (response.isCommitted()) {
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> body = ApiResponse.fail(
                ex.getMessage(),
                ErrorCode.ACCESS_DENIED.name()
        );

        var writer = response.getWriter();
        writer.write(objectMapper.writeValueAsString(body));
        writer.flush();
    }
}