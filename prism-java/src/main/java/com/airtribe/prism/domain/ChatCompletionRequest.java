package com.airtribe.prism.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
        @NotBlank String model,
        @NotEmpty @Valid List<ChatMessage> messages,
        Boolean stream
) {}
