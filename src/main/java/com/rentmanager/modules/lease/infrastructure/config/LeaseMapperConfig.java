package com.rentmanager.modules.lease.infrastructure.config;

import com.rentmanager.modules.lease.infrastructure.persistence.mapper.LeaseMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit bean configuration for Lease mappers.
 */
@Configuration
public class LeaseMapperConfig {

    @Bean
    public LeaseMapper leaseMapper() {
        return new LeaseMapper();
    }



}