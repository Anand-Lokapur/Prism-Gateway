package com.airtribe.prism.service;

import com.airtribe.prism.config.GatewayRegistry;
import com.airtribe.prism.domain.VirtualKey;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class VirtualKeyService {

    private final GatewayRegistry registry;

    public VirtualKeyService(GatewayRegistry registry) {
        this.registry = registry;
    }

    public Optional<VirtualKey> findByKey(String virtualKey) {
        return Optional.ofNullable(registry.virtualKeysByKey().get(virtualKey));
    }
}
