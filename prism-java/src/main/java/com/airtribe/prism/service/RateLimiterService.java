package com.airtribe.prism.service;

import com.airtribe.prism.config.GatewayRegistry;
import com.airtribe.prism.domain.VirtualKey;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {

    private final GatewayRegistry registry;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiterService(GatewayRegistry registry) {
        this.registry = registry;
    }

    public boolean tryAcquire(String virtualKey) {
        VirtualKey key = registry.virtualKeysByKey().get(virtualKey);
        if (key == null) {
            return false;
        }
        Bucket bucket = buckets.computeIfAbsent(virtualKey, unused -> new Bucket(key.rateLimit().requestsPerMinute()));
        return bucket.tryAcquire();
    }

    private static final class Bucket {
        private final double capacity;
        private final double refillPerSecond;
        private double tokens;
        private long lastRefillNanos;

        private Bucket(int requestsPerMinute) {
            this.capacity = Math.max(1, requestsPerMinute);
            this.refillPerSecond = this.capacity / 60.0;
            this.tokens = this.capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        private synchronized boolean tryAcquire() {
            refill();
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            if (elapsedSeconds <= 0) {
                return;
            }
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
            lastRefillNanos = now;
        }
    }
}
