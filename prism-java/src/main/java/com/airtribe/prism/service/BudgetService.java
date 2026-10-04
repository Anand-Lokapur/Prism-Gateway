package com.airtribe.prism.service;

import com.airtribe.prism.config.GatewayRegistry;
import com.airtribe.prism.domain.UsageAggregate;
import com.airtribe.prism.domain.VirtualKey;
import com.airtribe.prism.repository.UsageRepository;
import java.time.YearMonth;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class BudgetService {

    private final GatewayRegistry registry;
    private final UsageRepository usageRepository;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> inFlightReservations = new ConcurrentHashMap<>();

    public BudgetService(GatewayRegistry registry, UsageRepository usageRepository) {
        this.registry = registry;
        this.usageRepository = usageRepository;
    }

    public boolean reserve(String virtualKey, double estimatedCostUsd) {
        VirtualKey key = registry.virtualKeysByKey().get(virtualKey);
        if (key == null) {
            return false;
        }
        YearMonth month = YearMonth.now();
        synchronized (lockFor(virtualKey, month)) {
            double current = usageRepository.find(virtualKey, month).map(UsageAggregate::costUsd).orElse(0.0);
            if (current + estimatedCostUsd > key.monthlyBudgetUsd()) {
                return false;
            }
            // Provider usage arrives only after the response. Reserve the key while
            // it is in flight so concurrent requests cannot all pass a $0 estimate.
            return inFlightReservations.putIfAbsent(reservationKey(virtualKey, month), Boolean.TRUE) == null;
        }
    }

    public void commit(String virtualKey, double finalCostUsd) {
        VirtualKey key = registry.virtualKeysByKey().get(virtualKey);
        if (key == null) {
            return;
        }
        YearMonth month = YearMonth.now();
        synchronized (lockFor(virtualKey, month)) {
            UsageAggregate existing = usageRepository.find(virtualKey, month).orElse(new UsageAggregate(virtualKey, month, 0, 0, 0, 0.0, 0));
            usageRepository.upsert(new UsageAggregate(
                    virtualKey,
                    month,
                    existing.requests() + 1,
                    existing.promptTokens(),
                    existing.completionTokens(),
                    round6(existing.costUsd() + finalCostUsd),
                    existing.cacheHits()));
            inFlightReservations.remove(reservationKey(virtualKey, month));
        }
    }

    public void recordUsage(String virtualKey, int promptTokens, int completionTokens, double finalCostUsd, boolean cacheHit) {
        VirtualKey key = registry.virtualKeysByKey().get(virtualKey);
        if (key == null) {
            return;
        }
        YearMonth month = YearMonth.now();
        synchronized (lockFor(virtualKey, month)) {
            UsageAggregate existing = usageRepository.find(virtualKey, month).orElse(new UsageAggregate(virtualKey, month, 0, 0, 0, 0.0, 0));
            usageRepository.upsert(new UsageAggregate(
                    virtualKey,
                    month,
                    existing.requests() + 1,
                    existing.promptTokens() + promptTokens,
                    existing.completionTokens() + completionTokens,
                    round6(existing.costUsd() + finalCostUsd),
                    existing.cacheHits() + (cacheHit ? 1 : 0)));
            inFlightReservations.remove(reservationKey(virtualKey, month));
        }
    }

    public void cancelReservation(String virtualKey) {
        inFlightReservations.remove(reservationKey(virtualKey, YearMonth.now()));
    }

    private Object lockFor(String virtualKey, YearMonth month) {
        return locks.computeIfAbsent(virtualKey + "|" + month, unused -> new Object());
    }

    private String reservationKey(String virtualKey, YearMonth month) {
        return virtualKey + "|" + month;
    }

    private double round6(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
