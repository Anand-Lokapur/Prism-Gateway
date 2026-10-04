package com.airtribe.prism.repository;

import com.airtribe.prism.domain.CacheEntry;
import java.util.List;
import java.util.Optional;

public interface CacheRepository {
    Optional<CacheEntry> findMatch(String virtualKey, String modelKey, String prompt);
    void save(CacheEntry cacheEntry);
    List<CacheEntry> findByKey(String virtualKey);
}
