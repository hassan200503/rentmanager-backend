package com.rentmanager.modules.auth.application.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthPasswordService {

    private final PasswordEncoder passwordEncoder;

    public AuthPasswordService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matches(String raw, String encoded) {
        return passwordEncoder.matches(raw, encoded);
    }
}