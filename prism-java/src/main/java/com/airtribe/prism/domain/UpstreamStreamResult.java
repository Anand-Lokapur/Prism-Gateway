package com.airtribe.prism.domain;

import reactor.core.publisher.Flux;

public record UpstreamStreamResult(String providerName, String model, int statusCode, Flux<String> payloads) {}
