package com.airtribe.prism.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoutingEvalCase(String id, @JsonProperty("expected_tier") String expectedTier, String note, String prompt) {}
