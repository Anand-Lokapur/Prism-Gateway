package com.airtribe.prism.domain;

public record ChatCompletionChoiceDto(int index, ChatCompletionMessageDto message, String finishReason) {}
