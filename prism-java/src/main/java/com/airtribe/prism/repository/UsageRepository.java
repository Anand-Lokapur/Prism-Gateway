package com.airtribe.prism.repository;

import com.airtribe.prism.domain.UsageAggregate;
import java.time.YearMonth;
import java.util.Optional;

public interface UsageRepository {
    Optional<UsageAggregate> find(String virtualKey, YearMonth month);
    void upsert(UsageAggregate usageAggregate);
}
