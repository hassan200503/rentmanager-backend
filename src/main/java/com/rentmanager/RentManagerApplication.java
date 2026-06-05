package com.rentmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

import org.springframework.data.jpa.repository.config.EnableJpaRepositories;



@SpringBootApplication(scanBasePackages = "com.rentmanager")
@EnableJpaRepositories(basePackages = "com.rentmanager")
@EntityScan(basePackages = "com.rentmanager")
public class RentManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(RentManagerApplication.class, args);
    }
}

