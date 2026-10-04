package com.airtribe.prism.domain;

import java.time.Instant;
import java.util.List;

public record VirtualKey(
        String virtualKey,
        String team,
        double monthlyBudgetUsd,
        RateLimit rateLimit,
        List<String> modelAllowlist,
        SemanticCache semanticCache,
        String status,
        Instant createdAt
) {
    public record RateLimit(int requestsPerMinute, Integer tokensPerMinute) {}
    public record SemanticCache(boolean enabled, Double similarityThreshold) {}
}
