package com.airtribe.prism.domain;

public record UsageDto(int promptTokens, int completionTokens, int totalTokens) {}
