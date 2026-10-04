package com.airtribe.prism.domain;

public record ProviderConfig(
        String name,
        String baseUrl,
        String apiKey
) {}
