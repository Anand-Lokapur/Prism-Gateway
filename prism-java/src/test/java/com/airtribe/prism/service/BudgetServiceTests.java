package com.airtribe.prism.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.airtribe.prism.config.GatewayRegistry;
import com.airtribe.prism.domain.AutoRouteConfig;
import com.airtribe.prism.domain.UsageAggregate;
import com.airtribe.prism.domain.VirtualKey;
import com.airtribe.prism.repository.UsageRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BudgetServiceTests {

    @Test
    void admitsOnlyOneUnresolvedRequestForAKey() {
        UsageRepository repository = new UsageRepository() {
            private UsageAggregate value;
            public Optional<UsageAggregate> find(String key, YearMonth month) { return Optional.ofNullable(value); }
            public void upsert(UsageAggregate aggregate) { value = aggregate; }
        };
        BudgetService budget = new BudgetService(registryWithKey(), repository);

        assertThat(budget.reserve("key", 0.0)).isTrue();
        assertThat(budget.reserve("key", 0.0)).isFalse();
        budget.cancelReservation("key");
        assertThat(budget.reserve("key", 0.0)).isTrue();
    }

    private GatewayRegistry registryWithKey() {
        VirtualKey key = new VirtualKey("key", "test", 1.0,
                new VirtualKey.RateLimit(10, null), List.of("fast"),
                new VirtualKey.SemanticCache(false, null), "active", Instant.now());
        GatewayRegistry registry = new GatewayRegistry();
        registry.replaceAll(Map.of(), Map.of("key", key), Map.of(), Map.of(), new AutoRouteConfig("fast", "smart"));
        return registry;
    }
}
