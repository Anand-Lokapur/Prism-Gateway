package com.airtribe.prism.domain;

import jakarta.validation.constraints.NotBlank;

public record ChatMessage(
        @NotBlank String role,
        @NotBlank String content
) {}
