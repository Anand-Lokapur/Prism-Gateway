package com.airtribe.prism.domain;

import java.time.YearMonth;

public record UsageAggregate(
        String virtualKey,
        YearMonth month,
        long requests,
        long promptTokens,
        long completionTokens,
        double costUsd,
        long cacheHits
) {}
