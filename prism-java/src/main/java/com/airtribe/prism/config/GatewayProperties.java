package com.airtribe.prism.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "prism")
public record GatewayProperties(
        String adminToken,
        long upstreamTimeoutMs,
        String storagePath,
        Retry retry,
        DataPaths dataPaths,
        OpenRouter openRouter
) {
    public record Retry(int maxAttempts, long initialBackoffMs, double backoffMultiplier) {}
    public record DataPaths(String pricing, String seedKeys, String gatewayConfig) {}
    public record OpenRouter(String apiKey, String baseUrl, String model, String smartModel) {}
}
