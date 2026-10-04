package com.airtribe.prism.domain;

import java.time.Instant;

public record CacheEntry(
        String virtualKey,
        String modelKey,
        String promptSignature,
        String responsePayload,
        boolean streaming,
        Instant createdAt,
        Instant lastAccessedAt,
        long hitCount
) {}
