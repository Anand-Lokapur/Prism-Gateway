package com.airtribe.prism.domain;

import java.util.List;

public record ChatCompletionResponseDto(String id, String object, String model, List<ChatCompletionChoiceDto> choices, UsageDto usage) {}
