package com.airtribe.prism.domain;

public record ProviderHealthDto(String provider, String status, long requests, long errors, double errorRate) {}
