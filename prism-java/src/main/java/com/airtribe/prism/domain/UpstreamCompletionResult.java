package com.airtribe.prism.domain;

public record UpstreamCompletionResult(String providerName, String model, int statusCode, String body) {}
