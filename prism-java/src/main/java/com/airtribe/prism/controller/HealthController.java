package com.airtribe.prism.controller;

import com.airtribe.prism.service.AdminService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final AdminService adminService;
    private final Instant startedAt = Instant.now();

    public HealthController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "uptime_seconds", Duration.between(startedAt, Instant.now()).toSeconds(),
                "providers", adminService.providerHealth().providers());
    }
}
