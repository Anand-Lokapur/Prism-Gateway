package com.airtribe.prism.service;

import com.airtribe.prism.domain.ChatCompletionRequest;
import com.airtribe.prism.domain.UpstreamCompletionResult;
import com.airtribe.prism.domain.UpstreamStreamResult;
import reactor.core.publisher.Flux;

public interface ProviderClient {
    UpstreamCompletionResult chatCompletions(String providerName, String model, ChatCompletionRequest request);

    Flux<String> chatCompletionsStream(String providerName, String model, ChatCompletionRequest request);

    UpstreamStreamResult openChatCompletionsStream(String providerName, String model, ChatCompletionRequest request);
}
