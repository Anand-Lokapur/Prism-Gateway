package com.airtribe.prism.service;

import com.airtribe.prism.domain.CacheEntry;
import com.airtribe.prism.domain.CacheStatsDto;
import com.airtribe.prism.domain.RequestLogEntry;
import com.airtribe.prism.domain.ProviderHealthDto;
import com.airtribe.prism.domain.ProviderHealthReport;
import com.airtribe.prism.domain.UsageAggregate;
import com.airtribe.prism.domain.UsageSummaryDto;
import com.airtribe.prism.config.GatewayRegistry;
import com.airtribe.prism.repository.InMemoryCacheRepository;
import com.airtribe.prism.repository.InMemoryRequestLogRepository;
import com.airtribe.prism.repository.InMemoryUsageRepository;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    private final InMemoryRequestLogRepository logRepository;
    private final InMemoryUsageRepository usageRepository;
    private final InMemoryCacheRepository cacheRepository;
    private final GatewayRegistry registry;

    public AdminService(InMemoryRequestLogRepository logRepository,
                        InMemoryUsageRepository usageRepository,
                        InMemoryCacheRepository cacheRepository,
                        GatewayRegistry registry) {
        this.logRepository = logRepository;
        this.usageRepository = usageRepository;
        this.cacheRepository = cacheRepository;
        this.registry = registry;
    }

    public UsageSummaryDto usageSummary(String key, YearMonth month) {
        UsageAggregate aggregate = usageRepository.find(key, month)
                .orElse(new UsageAggregate(key, month, 0, 0, 0, 0.0, 0));
        return new UsageSummaryDto(key, month, aggregate.requests(), aggregate.promptTokens(), aggregate.completionTokens(), aggregate.costUsd(), aggregate.cacheHits());
    }

    public List<UsageSummaryDto> usageSummaries(YearMonth month) {
        return registry.virtualKeysByKey().keySet().stream()
                .sorted()
                .map(key -> usageSummary(key, month))
                .toList();
    }

    public List<RequestLogEntry> recentLogs(String key, int limit) {
        return logRepository.findRecentByKey(key, limit);
    }

    public CacheStatsDto cacheStats() {
        List<RequestLogEntry> all = logRepository.all().stream().sorted(Comparator.comparing(RequestLogEntry::createdAt).reversed()).toList();
        long hits = all.stream().filter(entry -> "hit".equalsIgnoreCase(entry.cache())).count();
        long misses = all.stream().filter(entry -> "miss".equalsIgnoreCase(entry.cache())).count();
        double total = hits + misses;
        Map<String, Object> byKey = all.stream().collect(Collectors.groupingBy(RequestLogEntry::virtualKey, Collectors.collectingAndThen(Collectors.toList(), list -> {
            long keyHits = list.stream().filter(entry -> "hit".equalsIgnoreCase(entry.cache())).count();
            long keyMisses = list.stream().filter(entry -> "miss".equalsIgnoreCase(entry.cache())).count();
            return Map.of("hits", keyHits, "misses", keyMisses, "hit_rate", (list.isEmpty() ? 0.0 : (double) keyHits / list.size()));
        })));
        return new CacheStatsDto(all.size(), hits, misses, total == 0 ? 0.0 : hits / total, byKey);
    }

    public List<CacheEntry> cacheEntries(String key) {
        return cacheRepository.findByKey(key);
    }

    public ProviderHealthReport providerHealth() {
        List<ProviderHealthDto> providers = registry.providersByName().keySet().stream().map(provider -> {
            long requests = logRepository.all().stream().filter(entry -> provider.equalsIgnoreCase(entry.resolvedProvider())).count();
            long errors = logRepository.all().stream().filter(entry -> provider.equalsIgnoreCase(entry.resolvedProvider()) && !"ok".equalsIgnoreCase(entry.status()) && !"cache_hit".equalsIgnoreCase(entry.status())).count();
            double errorRate = requests == 0 ? 0.0 : (double) errors / requests;
            String status = errorRate > 0.5 ? "degraded" : "healthy";
            return new ProviderHealthDto(provider, status, requests, errors, errorRate);
        }).toList();
        return new ProviderHealthReport(providers);
    }
}
