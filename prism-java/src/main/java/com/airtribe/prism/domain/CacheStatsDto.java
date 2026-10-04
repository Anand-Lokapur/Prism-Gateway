package com.airtribe.prism.domain;

import java.util.Map;

public record CacheStatsDto(long totalRequests, long cacheHits, long cacheMisses, double hitRate, Map<String, Object> byKey) {}
