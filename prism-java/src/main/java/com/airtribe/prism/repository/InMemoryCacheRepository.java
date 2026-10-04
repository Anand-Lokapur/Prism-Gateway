package com.airtribe.prism.repository;

import com.airtribe.prism.domain.CacheEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryCacheRepository implements CacheRepository {

    private final Map<String, List<CacheEntry>> entries = new ConcurrentHashMap<>();
    private final PersistentJsonStore store;

    public InMemoryCacheRepository(PersistentJsonStore store) {
        this.store = store;
        for (CacheEntry entry : store.readList("cache.json", new TypeReference<List<CacheEntry>>() {})) {
            entries.computeIfAbsent(key(entry.virtualKey(), entry.modelKey()), unused -> new CopyOnWriteArrayList<>()).add(entry);
        }
    }

    private String key(String virtualKey, String modelKey) {
        return virtualKey + "|" + modelKey;
    }

    @Override
    public Optional<CacheEntry> findMatch(String virtualKey, String modelKey, String prompt) {
        return entries.getOrDefault(key(virtualKey, modelKey), List.of()).stream().findFirst();
    }

    @Override
    public void save(CacheEntry cacheEntry) {
        entries.computeIfAbsent(key(cacheEntry.virtualKey(), cacheEntry.modelKey()), unused -> new CopyOnWriteArrayList<>()).add(cacheEntry);
        store.write("cache.json", entries.values().stream().flatMap(List::stream).toList());
    }

    @Override
    public List<CacheEntry> findByKey(String virtualKey) {
        return entries.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(virtualKey + "|"))
                .flatMap(entry -> entry.getValue().stream())
                .toList();
    }
}
