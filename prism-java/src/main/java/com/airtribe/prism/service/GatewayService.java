package com.airtribe.prism.service;

import com.airtribe.prism.config.GatewayRegistry;
import com.airtribe.prism.config.GatewayProperties;
import com.airtribe.prism.domain.ChatCompletionRequest;
import com.airtribe.prism.domain.GatewayException;
import com.airtribe.prism.domain.ModelAliasConfig;
import com.airtribe.prism.domain.ModelPricing;
import com.airtribe.prism.domain.RequestLogEntry;
import com.airtribe.prism.domain.UpstreamCompletionResult;
import com.airtribe.prism.domain.UpstreamStreamResult;
import com.airtribe.prism.domain.UsageDto;
import com.airtribe.prism.domain.VirtualKey;
import com.airtribe.prism.util.PromptUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class GatewayService {

    private final AuthService authService;
    private final VirtualKeyService virtualKeyService;
    private final RateLimiterService rateLimiterService;
    private final BudgetService budgetService;
    private final SemanticCacheService semanticCacheService;
    private final ProviderRouterService providerRouterService;
    private final ProviderClient providerClient;
    private final RequestLogService requestLogService;
    private final CostMeterService costMeterService;
    private final GatewayRegistry registry;
    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;

    public GatewayService(AuthService authService,
                          VirtualKeyService virtualKeyService,
                          RateLimiterService rateLimiterService,
                          BudgetService budgetService,
                          SemanticCacheService semanticCacheService,
                          ProviderRouterService providerRouterService,
                          ProviderClient providerClient,
                          RequestLogService requestLogService,
                          CostMeterService costMeterService,
                          GatewayRegistry registry,
                          GatewayProperties properties,
                          ObjectMapper objectMapper) {
        this.authService = authService;
        this.virtualKeyService = virtualKeyService;
        this.rateLimiterService = rateLimiterService;
        this.budgetService = budgetService;
        this.semanticCacheService = semanticCacheService;
        this.providerRouterService = providerRouterService;
        this.providerClient = providerClient;
        this.requestLogService = requestLogService;
        this.costMeterService = costMeterService;
        this.registry = registry;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Mono<ResponseEntity<?>> chatCompletions(ChatCompletionRequest request, String authorizationHeader) {
        return Mono.<ResponseEntity<?>>fromCallable(() -> handleNonStreaming(request, authorizationHeader))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(GatewayException.class, error -> logRejectedRequest(request, authorizationHeader, error));
    }

    public Mono<Void> chatCompletionsStream(ChatCompletionRequest request, String authorizationHeader, ServerHttpResponse response) {
        return Mono.fromRunnable(() -> {
                    response.setStatusCode(HttpStatus.OK);
                    response.getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);
                })
                .then(Mono.defer(() -> handleStreaming(request, authorizationHeader, response)))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(GatewayException.class, error -> logRejectedRequest(request, authorizationHeader, error));
    }

    private ResponseEntity<Object> handleNonStreaming(ChatCompletionRequest request, String authorizationHeader) throws IOException {
        String virtualKey = authenticate(authorizationHeader);
        VirtualKey tenant = requireTenant(virtualKey);
        validateRequest(request);
        requireKnownModel(request.model());
        enforceAllowlist(tenant, request.model());
        enforcePolicyBeforeCall(virtualKey, request.model());

        String prompt = PromptUtils.joinMessages(request.messages());
        var routePlan = providerRouterService.resolve(request.model(), prompt);
        String resolvedModel = routePlan.resolvedModel();
        String providerName = registry.providerForModel(resolvedModel);

        Optional<String> cached = semanticCacheService.lookup(virtualKey, resolvedModel, prompt, false);
        if (cached.isPresent()) {
            budgetService.recordUsage(virtualKey, 0, 0, 0.0, true);
            requestLogService.append(logEntry(virtualKey, request.model(), providerName, resolvedModel, "cache_hit", true, false, routePlan.routeReason(), 0, 0, 0.0, 0, 0));
            return ResponseEntity.ok()
                    .header("x-prism-provider", providerName + "/" + resolvedModel)
                    .header("x-prism-cache", "hit")
                    .header("x-prism-fallback", "false")
                    .header("x-prism-cost-usd", "0.000000")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cached.get());
        }

        CompletionResult completionResult = completeWithFallback(request, routePlan);
        if (completionResult.response() == null) {
            budgetService.cancelReservation(virtualKey);
            throw new GatewayException(HttpStatus.BAD_GATEWAY, "upstream_error", "upstream_error", "All providers failed");
        }

        JsonNode node = objectMapper.readTree(completionResult.response().body());
        UsageDto usage = extractUsage(node);
        double cost = calculateCost(completionResult.resolvedModel(), usage);

        budgetService.recordUsage(virtualKey, usage.promptTokens(), usage.completionTokens(), cost, false);
        // Cache by the resolved alias's primary route, not the transient winning
        // fallback, so later requests to the same alias can reuse the response.
        semanticCacheService.store(virtualKey, resolvedModel, prompt, completionResult.response().body(), false);
        requestLogService.append(logEntry(virtualKey, request.model(), completionResult.providerName(), completionResult.resolvedModel(), "ok", false, completionResult.fallback(), completionResult.routeReason(), completionResult.retries(), usage.promptTokens(), cost, usage.completionTokens(), completionResult.latencyMs()));

        return ResponseEntity.ok()
                .header("x-prism-provider", completionResult.providerName() + "/" + completionResult.resolvedModel())
                .header("x-prism-cache", "miss")
                .header("x-prism-fallback", Boolean.toString(completionResult.fallback()))
                .header("x-prism-cost-usd", String.format(java.util.Locale.ROOT, "%.6f", cost))
                .contentType(MediaType.APPLICATION_JSON)
                .body(completionResult.response().body());
    }

    private Mono<Void> handleStreaming(ChatCompletionRequest request, String authorizationHeader, ServerHttpResponse response) {
        String virtualKey = authenticate(authorizationHeader);
        VirtualKey tenant = requireTenant(virtualKey);
        validateRequest(request);
        requireKnownModel(request.model());
        enforceAllowlist(tenant, request.model());
        enforcePolicyBeforeCall(virtualKey, request.model());

        String prompt = PromptUtils.joinMessages(request.messages());
        var routePlan = providerRouterService.resolve(request.model(), prompt);
        String resolvedModel = routePlan.resolvedModel();
        String providerName = registry.providerForModel(resolvedModel);

        Optional<String> cached = semanticCacheService.lookup(virtualKey, resolvedModel, prompt, true);
        if (cached.isPresent()) {
            budgetService.recordUsage(virtualKey, 0, 0, 0.0, true);
            requestLogService.append(logEntry(virtualKey, request.model(), providerName, resolvedModel, "cache_hit", true, false, routePlan.routeReason(), 0, 0, 0.0, 0, 0));
            response.getHeaders().add("x-prism-provider", providerName + "/" + resolvedModel);
            response.getHeaders().add("x-prism-cache", "hit");
            response.getHeaders().add("x-prism-fallback", "false");
            return response.writeWith(Flux.just(response.bufferFactory().wrap(cached.get().getBytes(StandardCharsets.UTF_8))));
        }

        StreamingAttempt streamingAttempt = openStreamWithFallback(request, routePlan);
        if (streamingAttempt == null) {
            budgetService.cancelReservation(virtualKey);
            throw new GatewayException(HttpStatus.BAD_GATEWAY, "upstream_error", "upstream_error", "All providers failed");
        }
        String servingProvider = streamingAttempt.result().providerName();
        String servingModel = streamingAttempt.result().model();
        AtomicReference<UsageDto> usageRef = new AtomicReference<>();
        StringBuilder replayBuffer = new StringBuilder();

        Flux<String> payloads = streamingAttempt.result().payloads();
        Flux<String> body = payloads.map(payload -> {
            if (!"[DONE]".equals(payload)) {
                extractUsageFromPayload(payload).ifPresent(usageRef::set);
            }
            String frame = "data: " + payload + "\n\n";
            replayBuffer.append(frame);
            return frame;
        }).doOnComplete(() -> {
            UsageDto usage = usageRef.get();
            if (usage != null) {
                double cost = calculateCost(servingModel, usage);
                budgetService.recordUsage(virtualKey, usage.promptTokens(), usage.completionTokens(), cost, false);
                requestLogService.append(logEntry(virtualKey, request.model(), servingProvider, servingModel, "ok", false, streamingAttempt.fallback(), routePlan.routeReason(), streamingAttempt.retries(), usage.promptTokens(), cost, usage.completionTokens(), 0));
                semanticCacheService.store(virtualKey, resolvedModel, prompt, replayBuffer.toString(), true);
            } else {
                budgetService.cancelReservation(virtualKey);
                requestLogService.append(logEntry(virtualKey, request.model(), servingProvider, servingModel, "upstream_malformed", false, streamingAttempt.fallback(), routePlan.routeReason(), streamingAttempt.retries(), 0, 0.0, 0, 0));
            }
        }).doOnError(error -> {
            budgetService.cancelReservation(virtualKey);
            requestLogService.append(logEntry(virtualKey, request.model(), servingProvider, servingModel, "upstream_error", false, streamingAttempt.fallback(), routePlan.routeReason(), streamingAttempt.retries(), 0, 0.0, 0, 0));
        });

            response.getHeaders().add("x-prism-provider", servingProvider + "/" + servingModel);
            response.getHeaders().add("x-prism-cache", "miss");
            response.getHeaders().add("x-prism-fallback", Boolean.toString(streamingAttempt.fallback()));
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE);

            Flux<org.springframework.core.io.buffer.DataBuffer> dataBufferFlux = body.map(s ->
                response.bufferFactory().wrap(s.getBytes(StandardCharsets.UTF_8)));
            return response.writeWith(dataBufferFlux);
    }

    private StreamingAttempt openStreamWithFallback(ChatCompletionRequest request, ProviderRouterService.RoutePlan routePlan) {
        int retries = 0;
        for (int providerIndex = 0; providerIndex < routePlan.providerOrder().size(); providerIndex++) {
            String model = routePlan.providerOrder().get(providerIndex);
            String provider = registry.providerForModel(model);
            for (int attempt = 0; attempt < properties.retry().maxAttempts(); attempt++) {
                UpstreamStreamResult result = providerClient.openChatCompletionsStream(provider, model, request);
                if (result.statusCode() == 200) {
                    return new StreamingAttempt(result, providerIndex > 0, retries);
                }
                retries++;
                if (!isTransient(result.statusCode()) || attempt == properties.retry().maxAttempts() - 1) {
                    break;
                }
                sleepBackoff(attempt);
            }
        }
        return null;
    }

    private CompletionResult completeWithFallback(ChatCompletionRequest request, ProviderRouterService.RoutePlan routePlan) throws IOException {
        long start = System.currentTimeMillis();
        int retries = 0;
        boolean fallback = false;
        for (int i = 0; i < routePlan.providerOrder().size(); i++) {
            String model = routePlan.providerOrder().get(i);
            String provider = registry.providerForModel(model);
            for (int attempt = 0; attempt < properties.retry().maxAttempts(); attempt++) {
                UpstreamCompletionResult result = providerClient.chatCompletions(provider, model, request);
                if (result.statusCode() == 200) {
                    return new CompletionResult(result, provider, model, fallback || i > 0, retries, System.currentTimeMillis() - start, routePlan.routeReason());
                }
                System.err.println("Provider " + provider + " model " + model + " returned " + result.statusCode() + ": " + result.body());
                retries++;
                if (!isTransient(result.statusCode()) || attempt == properties.retry().maxAttempts() - 1) {
                    break;
                }
                sleepBackoff(attempt);
            }
            fallback = true;
        }
        return new CompletionResult(null, null, null, fallback, retries, System.currentTimeMillis() - start, routePlan.routeReason());
    }

    private String authenticate(String authorizationHeader) {
        return authService.resolveVirtualKey(authorizationHeader)
                .orElseThrow(() -> new GatewayException(HttpStatus.UNAUTHORIZED, "authentication_error", "authentication_error", "Missing or invalid virtual key"));
    }

    private VirtualKey requireTenant(String virtualKey) {
        VirtualKey tenant = virtualKeyService.findByKey(virtualKey)
                .orElseThrow(() -> new GatewayException(HttpStatus.UNAUTHORIZED, "authentication_error", "authentication_error", "Unknown virtual key"));
        if (!"active".equalsIgnoreCase(tenant.status())) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "authentication_error", "authentication_error", "Virtual key is disabled");
        }
        return tenant;
    }

    private void validateRequest(ChatCompletionRequest request) {
        if (request == null || request.messages() == null || request.messages().isEmpty()) {
            throw new GatewayException(HttpStatus.BAD_REQUEST, "invalid_request_error", "invalid_request_error", "messages must be a non-empty list");
        }
        if (request.model() == null || request.model().isBlank()) {
            throw new GatewayException(HttpStatus.BAD_REQUEST, "invalid_request_error", "invalid_request_error", "model is required");
        }
    }

    private void enforceAllowlist(VirtualKey tenant, String requestedModel) {
        if (tenant.modelAllowlist().contains(requestedModel)) {
            return;
        }
        for (String allowed : tenant.modelAllowlist()) {
            ModelAliasConfig alias = registry.aliasesByName().get(allowed);
            if (alias != null && (alias.primary().equalsIgnoreCase(requestedModel) || alias.fallbacks().contains(requestedModel))) {
                return;
            }
        }
        throw new GatewayException(HttpStatus.FORBIDDEN, "model_not_allowed", "model_not_allowed", "Model not allowed for this key");
    }

    private void requireKnownModel(String requestedModel) {
        if (!providerRouterService.isKnownModelOrAlias(requestedModel)) {
            throw new GatewayException(HttpStatus.NOT_FOUND, "not_found_error", "not_found_error", "Unknown model or alias: " + requestedModel);
        }
    }

    private void enforcePolicyBeforeCall(String virtualKey, String requestedModel) {
        if (!rateLimiterService.tryAcquire(virtualKey)) {
            throw new GatewayException(HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded", "rate_limit_exceeded", "Rate limit exceeded for this key");
        }
        if (!budgetService.reserve(virtualKey, 0.0)) {
            throw new GatewayException(HttpStatus.TOO_MANY_REQUESTS, "budget_exceeded", "budget_exceeded", "Monthly budget exhausted for this key");
        }
    }

    private void logRejectedRequest(ChatCompletionRequest request, String authorizationHeader, GatewayException error) {
        String virtualKey = authService.resolveVirtualKey(authorizationHeader).orElse("<unknown>");
        String requestedModel = request == null ? null : request.model();
        requestLogService.appendGatewayRejection(virtualKey, requestedModel, error.code());
    }

    private double calculateCost(String model, UsageDto usage) {
        ModelPricing pricing = registry.pricingByModel().get(model);
        return pricing == null ? 0.0 : costMeterService.calculate(pricing, usage.promptTokens(), usage.completionTokens());
    }

    private UsageDto extractUsage(JsonNode node) {
        JsonNode usage = node.path("usage");
        return new UsageDto(usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt(), usage.path("total_tokens").asInt());
    }

    private Optional<UsageDto> extractUsageFromPayload(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node.has("usage")) {
                return Optional.of(extractUsage(node));
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private boolean isTransient(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep((long) (properties.retry().initialBackoffMs()
                    * Math.pow(properties.retry().backoffMultiplier(), attempt)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private RequestLogEntry logEntry(String virtualKey,
                                     String requestedModel,
                                     String providerName,
                                     String resolvedModel,
                                     String status,
                                     boolean cacheHit,
                                     boolean fallback,
                                     String routeReason,
                                     int retries,
                                     int promptTokens,
                                     double cost,
                                     int completionTokens,
                                     long latencyMs) {
        return new RequestLogEntry(
                UUID.randomUUID().toString(),
                virtualKey,
                requestedModel,
                providerName,
                resolvedModel,
                status,
                promptTokens,
                completionTokens,
                cost,
                cacheHit ? "hit" : "miss",
                fallback,
                routeReason,
                retries,
                latencyMs,
                Instant.now());
    }

    private record CompletionResult(UpstreamCompletionResult response, String providerName, String resolvedModel, boolean fallback, int retries, long latencyMs, String routeReason) {}
    private record StreamingAttempt(UpstreamStreamResult result, boolean fallback, int retries) {}
}
