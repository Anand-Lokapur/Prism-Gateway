package com.airtribe.prism.repository;

import com.airtribe.prism.domain.RequestLogEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryRequestLogRepository implements RequestLogRepository {

    private final CopyOnWriteArrayList<RequestLogEntry> entries = new CopyOnWriteArrayList<>();
    private final PersistentJsonStore store;

    public InMemoryRequestLogRepository(PersistentJsonStore store) {
        this.store = store;
        entries.addAll(store.readList("request-logs.json", new TypeReference<List<RequestLogEntry>>() {}));
    }

    @Override
    public void save(RequestLogEntry entry) {
        entries.add(entry);
        store.write("request-logs.json", entries);
    }

    @Override
    public List<RequestLogEntry> findRecentByKey(String virtualKey, int limit) {
        return entries.stream()
                .filter(entry -> virtualKey == null || virtualKey.isBlank() || virtualKey.equals(entry.virtualKey()))
                .sorted(Comparator.comparing(RequestLogEntry::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    public List<RequestLogEntry> all() {
        return new ArrayList<>(entries);
    }
}
