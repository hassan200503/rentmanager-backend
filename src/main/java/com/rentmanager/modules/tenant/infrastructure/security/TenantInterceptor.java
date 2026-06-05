package com.rentmanager.modules.tenant.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;


@Component
public class TenantInterceptor implements HandlerInterceptor {

    private final TenantResolver tenantResolver = new TenantResolver();

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        UUID tenantId = tenantResolver.resolve(request);

        if (tenantId != null) {
            TenantContext.set(tenantId);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {

        TenantContext.clear();
    }
}