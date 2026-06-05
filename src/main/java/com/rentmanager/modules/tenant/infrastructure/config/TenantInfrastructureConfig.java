package com.rentmanager.modules.tenant.infrastructure.config;

import com.rentmanager.modules.tenant.infrastructure.security.TenantInterceptor;
import org.springframework.context.annotation.Configuration;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TenantInfrastructureConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;

    public TenantInfrastructureConfig(TenantInterceptor tenantInterceptor) {
        this.tenantInterceptor = tenantInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**");
    }

    /**
     * Optional future tenant-level beans can be declared here.
     * KEEP THIS CLEAN — wiring only.
     */
}