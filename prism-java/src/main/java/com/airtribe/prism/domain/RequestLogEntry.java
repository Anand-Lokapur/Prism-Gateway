package com.airtribe.prism.domain;

import java.time.Instant;

public record RequestLogEntry(
        String requestId,
        String virtualKey,
        String requestedModel,
        String resolvedProvider,
        String resolvedModel,
        String status,
        Integer promptTokens,
        Integer completionTokens,
        Double costUsd,
        String cache,
        boolean fallback,
        String routeReason,
        int retries,
        long latencyMs,
        Instant createdAt
) {}
