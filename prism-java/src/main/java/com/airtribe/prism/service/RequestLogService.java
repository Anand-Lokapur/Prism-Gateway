package com.airtribe.prism.service;

import com.airtribe.prism.domain.RequestLogEntry;
import com.airtribe.prism.repository.RequestLogRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RequestLogService {

    private final RequestLogRepository repository;

    public RequestLogService(RequestLogRepository repository) {
        this.repository = repository;
    }

    public void append(RequestLogEntry entry) {
        repository.save(entry);
    }

    public void appendGatewayRejection(String virtualKey, String requestedModel, String status) {
        append(new RequestLogEntry(UUID.randomUUID().toString(), virtualKey, requestedModel,
                "gateway", null, status, 0, 0, 0.0, "miss", false,
                null, 0, 0, Instant.now()));
    }
}
