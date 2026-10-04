package com.airtribe.prism.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.airtribe.prism.config.GatewayRegistry;
import com.airtribe.prism.domain.AutoRouteConfig;
import com.airtribe.prism.domain.VirtualKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RateLimiterServiceTests {

    @Test
    void enforcesBurstCapacityAtomically() {
        GatewayRegistry registry = registryWithKey(2);
        RateLimiterService limiter = new RateLimiterService(registry);

        assertThat(limiter.tryAcquire("key")).isTrue();
        assertThat(limiter.tryAcquire("key")).isTrue();
        assertThat(limiter.tryAcquire("key")).isFalse();
    }

    private GatewayRegistry registryWithKey(int rpm) {
        VirtualKey key = new VirtualKey("key", "test", 1.0,
                new VirtualKey.RateLimit(rpm, null), List.of("fast"),
                new VirtualKey.SemanticCache(false, null), "active", Instant.now());
        GatewayRegistry registry = new GatewayRegistry();
        registry.replaceAll(Map.of(), Map.of("key", key), Map.of(), Map.of(), new AutoRouteConfig("fast", "smart"));
        return registry;
    }
}
