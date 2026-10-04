package com.airtribe.prism.service;

import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    public Optional<String> resolveVirtualKey(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = bearerToken.substring("Bearer ".length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
