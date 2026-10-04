package com.airtribe.prism.controller;

import com.airtribe.prism.domain.ChatCompletionRequest;
import com.airtribe.prism.service.GatewayService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class OpenAiController {

    private final GatewayService gatewayService;

    public OpenAiController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @PostMapping(value = "/v1/chat/completions", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Mono<ResponseEntity<?>> chatCompletions(@Valid @RequestBody ChatCompletionRequest request,
                                                   @RequestHeader(name = "Authorization", required = false) String authorization,
                                                   ServerHttpResponse response) {
        if (Boolean.TRUE.equals(request.stream())) {
            return gatewayService.chatCompletionsStream(request, authorization, response)
                    .thenReturn(ResponseEntity.ok().build());
        }
        return gatewayService.chatCompletions(request, authorization);
    }
}
