package com.rentmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.rentmanager")
@EnableJpaRepositories(basePackages = "com.rentmanager")
@EntityScan(basePackages = "com.rentmanager")
@EnableScheduling
public class RentManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(RentManagerApplication.class, args);
    }
}