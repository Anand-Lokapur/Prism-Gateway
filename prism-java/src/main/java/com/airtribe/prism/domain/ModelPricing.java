package com.airtribe.prism.domain;

public record ModelPricing(
        String model,
        double inputPer1m,
        double outputPer1m
) {}
