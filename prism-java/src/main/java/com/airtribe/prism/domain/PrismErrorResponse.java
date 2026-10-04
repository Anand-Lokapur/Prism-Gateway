package com.airtribe.prism.domain;

public record PrismErrorResponse(
        String message,
        String type,
        String code
) {
    public static record ErrorEnvelope(PrismErrorResponse error) {}
}
