package com.airtribe.prism.service;

import com.airtribe.prism.config.GatewayRegistry;
import com.airtribe.prism.domain.CacheEntry;
import com.airtribe.prism.domain.VirtualKey;
import com.airtribe.prism.repository.CacheRepository;
import com.airtribe.prism.util.SimilarityUtils;
import com.airtribe.prism.util.PromptUtils;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SemanticCacheService {

    private final GatewayRegistry registry;
    private final CacheRepository repository;

    public SemanticCacheService(GatewayRegistry registry, CacheRepository repository) {
        this.registry = registry;
        this.repository = repository;
    }

    public Optional<String> lookup(String virtualKey, String modelKey, String prompt, boolean streaming) {
        VirtualKey key = registry.virtualKeysByKey().get(virtualKey);
        if (key == null || !key.semanticCache().enabled()) {
            return Optional.empty();
        }
        if (isTimeSensitive(prompt)) {
            return Optional.empty();
        }
        String normalizedPrompt = PromptUtils.normalize(prompt);
        double threshold = key.semanticCache().similarityThreshold() == null ? 0.9 : key.semanticCache().similarityThreshold();
        return repository.findByKey(virtualKey).stream()
                .filter(entry -> modelKey.equals(entry.modelKey()))
                .filter(entry -> entry.streaming() == streaming)
                .filter(entry -> SimilarityUtils.semanticSimilarity(normalizedPrompt, PromptUtils.normalize(entry.promptSignature())) >= threshold)
                .map(CacheEntry::responsePayload)
                .findFirst();
    }

    public void store(String virtualKey, String modelKey, String prompt, String responsePayload, boolean streaming) {
        VirtualKey key = registry.virtualKeysByKey().get(virtualKey);
        if (key == null || !key.semanticCache().enabled()) {
            return;
        }
        if (isTimeSensitive(prompt)) {
            return;
        }
        repository.save(new CacheEntry(virtualKey, modelKey, prompt, responsePayload, streaming, Instant.now(), Instant.now(), 0));
    }

    private boolean isTimeSensitive(String prompt) {
        String normalized = PromptUtils.normalize(prompt);
        return normalized.contains("current status")
                || normalized.contains("right now")
                || normalized.contains("today")
                || normalized.contains("latest ")
                || normalized.contains("live ");
    }
}
