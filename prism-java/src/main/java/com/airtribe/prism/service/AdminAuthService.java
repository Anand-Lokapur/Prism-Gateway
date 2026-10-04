package com.airtribe.prism.service;

import com.airtribe.prism.config.GatewayProperties;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthService {

    private final GatewayProperties properties;

    public AdminAuthService(GatewayProperties properties) {
        this.properties = properties;
    }

    public boolean isAuthorized(String token) {
        return token != null && token.equals(properties.adminToken());
    }
}
