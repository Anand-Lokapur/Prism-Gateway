package com.airtribe.prism.service;

import com.airtribe.prism.domain.UsageAggregate;
import com.airtribe.prism.repository.UsageRepository;
import java.time.YearMonth;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class UsageService {

    private final UsageRepository repository;

    public UsageService(UsageRepository repository) {
        this.repository = repository;
    }

    public Optional<UsageAggregate> findMonthlyUsage(String virtualKey, YearMonth month) {
        return repository.find(virtualKey, month);
    }
}
