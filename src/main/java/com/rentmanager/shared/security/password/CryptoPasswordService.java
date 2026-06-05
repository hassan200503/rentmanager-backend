package com.rentmanager.shared.security.password;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class CryptoPasswordService {

    private final PasswordEncoder passwordEncoder;

    public CryptoPasswordService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String hash(String raw) {
        return passwordEncoder.encode(raw);
    }

    public boolean matches(String raw, String encoded) {
        return passwordEncoder.matches(raw, encoded);
    }
}