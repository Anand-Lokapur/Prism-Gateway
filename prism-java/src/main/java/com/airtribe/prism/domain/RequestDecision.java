package com.airtribe.prism.domain;

public record RequestDecision(String resolvedModel, String providerName, boolean fallback, String routeReason) {}
