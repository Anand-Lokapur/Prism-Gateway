package com.airtribe.prism.repository;

import com.airtribe.prism.domain.UsageAggregate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryUsageRepository implements UsageRepository {

    private final Map<String, UsageAggregate> aggregates = new ConcurrentHashMap<>();
    private final PersistentJsonStore store;

    public InMemoryUsageRepository(PersistentJsonStore store) {
        this.store = store;
        for (UsageAggregate aggregate : store.readList("usage.json", new TypeReference<List<UsageAggregate>>() {})) {
            aggregates.put(key(aggregate.virtualKey(), aggregate.month()), aggregate);
        }
    }

    private String key(String virtualKey, YearMonth month) {
        return virtualKey + "|" + month;
    }

    @Override
    public Optional<UsageAggregate> find(String virtualKey, YearMonth month) {
        return Optional.ofNullable(aggregates.get(key(virtualKey, month)));
    }

    @Override
    public void upsert(UsageAggregate usageAggregate) {
        aggregates.put(key(usageAggregate.virtualKey(), usageAggregate.month()), usageAggregate);
        store.write("usage.json", aggregates.values());
    }

    public Map<String, UsageAggregate> all() {
        return Map.copyOf(aggregates);
    }
}
