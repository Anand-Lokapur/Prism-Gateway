package com.airtribe.prism.adapter;

import com.airtribe.prism.config.GatewayRegistry;
import com.airtribe.prism.config.GatewayProperties;
import com.airtribe.prism.domain.ChatCompletionRequest;
import com.airtribe.prism.domain.UpstreamCompletionResult;
import com.airtribe.prism.domain.UpstreamStreamResult;
import com.airtribe.prism.service.ProviderClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Component
public class OpenAiProviderClient implements ProviderClient {

    private final GatewayRegistry registry;
    private final ObjectMapper objectMapper;
    private final GatewayProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public OpenAiProviderClient(GatewayRegistry registry, ObjectMapper objectMapper, GatewayProperties properties) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public UpstreamCompletionResult chatCompletions(String providerName, String model, ChatCompletionRequest request) {
        try {
            var provider = Objects.requireNonNull(registry.providersByName().get(providerName), "provider");
            HttpRequest httpRequest = baseRequest(providerName, provider.baseUrl(), provider.apiKey(), model, request, false);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new UpstreamCompletionResult(providerName, model, response.statusCode(), response.body());
        } catch (Exception e) {
            System.err.println("chatCompletions exception for provider " + providerName + ": " + e.getMessage());
            e.printStackTrace();
            // Preserve failures for the router so retries and fallback can run.
            return new UpstreamCompletionResult(providerName, model, 503, "Upstream call failed: " + e.getMessage());
        }
    }

    @Override
    public Flux<String> chatCompletionsStream(String providerName, String model, ChatCompletionRequest request) {
        UpstreamStreamResult opened = openChatCompletionsStream(providerName, model, request);
        return opened.statusCode() == 200 ? opened.payloads()
                : Flux.error(new IllegalStateException("Provider returned status " + opened.statusCode()));
    }

    @Override
    public UpstreamStreamResult openChatCompletionsStream(String providerName, String model, ChatCompletionRequest request) {
        try {
            var provider = Objects.requireNonNull(registry.providersByName().get(providerName), "provider");
            HttpRequest httpRequest = baseRequest(providerName, provider.baseUrl(), provider.apiKey(), model, request, true);
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                return new UpstreamStreamResult(providerName, model, response.statusCode(), Flux.empty());
            }
            Flux<String> payloads = Flux.<String>create(sink -> Schedulers.boundedElastic().schedule(() -> {
                try (InputStream stream = response.body();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data: ")) {
                            continue;
                        }
                        String payload = line.substring("data: ".length());
                        sink.next(payload);
                        if ("[DONE]".equals(payload.trim())) {
                            sink.complete();
                            return;
                        }
                    }
                    sink.complete();
                } catch (Exception e) {
                    sink.error(e);
                }
            }));
            return new UpstreamStreamResult(providerName, model, 200, payloads);
        } catch (Exception e) {
            return new UpstreamStreamResult(providerName, model, 503, Flux.empty());
        }
    }

    private HttpRequest baseRequest(String providerName, String baseUrl, String apiKey, String model, ChatCompletionRequest request, boolean stream) throws IOException {
        JsonNode body = objectMapper.valueToTree(request);
        var objectNode = (com.fasterxml.jackson.databind.node.ObjectNode) body;
        objectNode.put("model", model);
        if (stream) {
            objectNode.put("stream", true);
            var streamOptions = objectNode.putObject("stream_options");
            streamOptions.put("include_usage", true);
        } else {
            objectNode.remove("stream");
            objectNode.remove("stream_options");
        }
        long timeoutMs = "openrouter".equalsIgnoreCase(providerName)
                ? Math.max(properties.upstreamTimeoutMs(), 30000)
                : properties.upstreamTimeoutMs();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(normalizedBase + "/chat/completions"))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json");
        if ("openrouter".equalsIgnoreCase(providerName)) {
            builder.header("HTTP-Referer", "http://localhost:8080")
                    .header("X-Title", "Prism");
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8)).build();
    }
}
