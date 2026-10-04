package com.airtribe.prism.repository;

import com.airtribe.prism.domain.RequestLogEntry;
import java.util.List;

public interface RequestLogRepository {
    void save(RequestLogEntry entry);
    List<RequestLogEntry> findRecentByKey(String virtualKey, int limit);
}
