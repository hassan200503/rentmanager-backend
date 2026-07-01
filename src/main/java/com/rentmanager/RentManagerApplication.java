package com.rentmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.rentmanager")
@EnableScheduling
public class RentManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(RentManagerApplication.class, args);
    }
}
