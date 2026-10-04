package com.airtribe.prism.config;

import com.airtribe.prism.domain.AutoRouteConfig;
import com.airtribe.prism.domain.ModelAliasConfig;
import com.airtribe.prism.domain.ModelPricing;
import com.airtribe.prism.domain.ProviderConfig;
import com.airtribe.prism.domain.VirtualKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SeedDataLoader {

    private final GatewayProperties properties;
    private final GatewayRegistry registry;
    private final ObjectMapper objectMapper;

    public SeedDataLoader(GatewayProperties properties, GatewayRegistry registry, ObjectMapper objectMapper) {
        this.properties = properties;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() throws IOException {
        load();
    }

    public void load() throws IOException {
        DotenvLoader.load();
        Map<String, ModelPricing> pricing = loadPricing(resolvePath(properties.dataPaths().pricing()));
        Map<String, VirtualKey> keys = loadKeys(resolvePath(properties.dataPaths().seedKeys()));
        LoadedGatewayConfig gatewayConfig = loadGatewayConfig(resolvePath(properties.dataPaths().gatewayConfig()));
        registry.replaceAll(pricing, keys, gatewayConfig.providers(), gatewayConfig.modelProviders(), gatewayConfig.aliases(), gatewayConfig.autoRouteConfig());
    }

    private Path resolvePath(String configuredPath) {
        Path path = Path.of(configuredPath);
        if (Files.exists(path)) {
            return path;
        }
        Path alt = Path.of("..", configuredPath).normalize();
        if (Files.exists(alt)) {
            return alt;
        }
        return path;
    }

    private Map<String, ModelPricing> loadPricing(Path path) throws IOException {
        JsonNode root = readJson(path);
        Map<String, ModelPricing> pricing = new HashMap<>();
        root.fields().forEachRemaining(entry -> {
            JsonNode node = entry.getValue();
            pricing.put(entry.getKey(), new ModelPricing(
                    entry.getKey(),
                    node.path("input_per_1m").asDouble(),
                    node.path("output_per_1m").asDouble()));
        });
        return pricing;
    }

    private Map<String, VirtualKey> loadKeys(Path path) throws IOException {
        JsonNode root = readJson(path).path("tenants");
        Map<String, VirtualKey> keys = new HashMap<>();
        for (JsonNode node : root) {
            VirtualKey.RateLimit rateLimit = new VirtualKey.RateLimit(
                    node.path("rate_limit").path("requests_per_minute").asInt(),
                    node.path("rate_limit").has("tokens_per_minute") ? node.path("rate_limit").path("tokens_per_minute").asInt() : null);
            JsonNode cache = node.path("semantic_cache");
            VirtualKey.SemanticCache semanticCache = new VirtualKey.SemanticCache(
                    cache.path("enabled").asBoolean(),
                    cache.has("similarity_threshold") ? cache.path("similarity_threshold").asDouble() : null);
            VirtualKey key = new VirtualKey(
                    node.path("virtual_key").asText(),
                    node.path("team").asText(),
                    node.path("monthly_budget_usd").asDouble(),
                    rateLimit,
                    objectMapper.convertValue(node.path("model_allowlist"), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)),
                    semanticCache,
                    node.path("status").asText("active"),
                    Instant.now());
            keys.put(key.virtualKey(), key);
        }
        return keys;
    }

    private LoadedGatewayConfig loadGatewayConfig(Path path) throws IOException {
        JsonNode root = readJson(path);
        String openRouterApiKey = resolveOpenRouterApiKey(root);
        boolean hasOpenRouter = openRouterApiKey != null && !openRouterApiKey.isBlank();

        Map<String, ProviderConfig> providers = new HashMap<>();
        Map<String, String> modelProviders = new HashMap<>();
        for (JsonNode provider : root.path("providers")) {
            String name = provider.path("name").asText();
            String baseUrl = resolveEnvPlaceholders(provider.path("base_url").asText());
            String apiKey = resolveEnvPlaceholders(provider.path("api_key").asText());
            if ("openrouter".equalsIgnoreCase(name) && hasOpenRouter && apiKey.isBlank()) {
                apiKey = openRouterApiKey;
            }
            ProviderConfig cfg = new ProviderConfig(name, baseUrl, apiKey);
            providers.put(cfg.name(), cfg);
        }

        Map<String, ModelAliasConfig> aliases = new HashMap<>();
        JsonNode aliasesNode = root.path("model_aliases");
        aliasesNode.fields().forEachRemaining(entry -> {
            JsonNode aliasNode = entry.getValue();
            if (aliasNode.has("primary")) {
                String primary = aliasNode.path("primary").asText();
                List<String> fallbacks = objectMapper.convertValue(aliasNode.path("fallbacks"), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                aliases.put(entry.getKey(), new ModelAliasConfig(
                        entry.getKey(),
                        primary,
                        fallbacks));
                registerModelProvider(modelProviders, providers, primary);
                fallbacks.forEach(model -> registerModelProvider(modelProviders, providers, model));
            }
        });

        if (hasOpenRouter) {
            String openRouterBaseUrl = properties.openRouter() != null && properties.openRouter().baseUrl() != null && !properties.openRouter().baseUrl().isBlank()
                    ? properties.openRouter().baseUrl().trim()
                    : "https://openrouter.ai/api/v1";
            ProviderConfig openRouterProvider = new ProviderConfig(
                    "openrouter",
                    openRouterBaseUrl,
                    openRouterApiKey);
            providers.put(openRouterProvider.name(), openRouterProvider);

            String fastModel = "openai/gpt-4o-mini";
            String smartModel = "anthropic/claude-sonnet-4";
            if (root.has("openrouter_aliases")) {
                JsonNode orFast = root.path("openrouter_aliases").path("fast");
                if (orFast.has("primary")) {
                    fastModel = orFast.path("primary").asText(fastModel);
                }
                JsonNode orSmart = root.path("openrouter_aliases").path("smart");
                if (orSmart.has("primary")) {
                    smartModel = orSmart.path("primary").asText(smartModel);
                }
            }
            if (properties.openRouter() != null && properties.openRouter().model() != null && !properties.openRouter().model().isBlank()) {
                fastModel = properties.openRouter().model().trim();
            } else {
                String envModel = DotenvLoader.get("OPENROUTER_MODEL");
                if (envModel != null && !envModel.isBlank()) {
                    fastModel = envModel.trim();
                }
            }
            if (properties.openRouter() != null && properties.openRouter().smartModel() != null && !properties.openRouter().smartModel().isBlank()) {
                smartModel = properties.openRouter().smartModel().trim();
            } else {
                String envSmartModel = DotenvLoader.get("OPENROUTER_SMART_MODEL");
                if (envSmartModel != null && !envSmartModel.isBlank()) {
                    smartModel = envSmartModel.trim();
                }
            }

            aliases.put("fast", new ModelAliasConfig("fast", fastModel, List.of(smartModel)));
            aliases.put("smart", new ModelAliasConfig("smart", smartModel, List.of(fastModel)));
            aliases.put(fastModel, new ModelAliasConfig(fastModel, fastModel, List.of(smartModel)));
            aliases.put(smartModel, new ModelAliasConfig(smartModel, smartModel, List.of(fastModel)));

            registerModelProvider(modelProviders, providers, fastModel, openRouterProvider.name());
            registerModelProvider(modelProviders, providers, smartModel, openRouterProvider.name());
        }

        JsonNode autoNode = aliasesNode.path("auto").path("route_by_difficulty");
        AutoRouteConfig autoRouteConfig = new AutoRouteConfig(
                autoNode.path("simple").asText("fast"),
                autoNode.path("complex").asText("smart"));
        return new LoadedGatewayConfig(providers, modelProviders, aliases, autoRouteConfig);
    }

    private String resolveOpenRouterApiKey(JsonNode root) {
        if (properties.openRouter() != null && properties.openRouter().apiKey() != null && !properties.openRouter().apiKey().isBlank()) {
            return properties.openRouter().apiKey().trim();
        }
        String fromDotenv = DotenvLoader.get("OPENROUTER_API_KEY");
        if (fromDotenv != null && !fromDotenv.isBlank()) {
            return fromDotenv.trim();
        }
        if (root != null && root.has("providers")) {
            for (JsonNode provider : root.path("providers")) {
                if ("openrouter".equalsIgnoreCase(provider.path("name").asText())) {
                    String raw = provider.path("api_key").asText("");
                    String resolved = resolveEnvPlaceholders(raw);
                    if (!resolved.isBlank()) {
                        return resolved.trim();
                    }
                }
            }
        }
        return null;
    }

    private String resolveEnvPlaceholders(String text) {
        if (text == null) {
            return "";
        }
        if (text.startsWith("${") && text.endsWith("}")) {
            String inner = text.substring(2, text.length() - 1);
            String defaultValue = "";
            int colon = inner.indexOf(':');
            if (colon >= 0) {
                defaultValue = inner.substring(colon + 1);
                inner = inner.substring(0, colon);
            }
            String val = DotenvLoader.get(inner);
            if (val != null && !val.isBlank()) {
                return val;
            }
            return defaultValue;
        }
        return text;
    }

    private void registerModelProvider(Map<String, String> modelProviders, Map<String, ProviderConfig> providers, String model) {
        registerModelProvider(modelProviders, providers, model, null);
    }

    private void registerModelProvider(Map<String, String> modelProviders, Map<String, ProviderConfig> providers, String model, String explicitProvider) {
        if (model == null || model.isBlank()) {
            return;
        }
        String providerName = explicitProvider;
        if (providerName == null) {
            int slash = model.indexOf('/');
            if (slash > 0) {
                providerName = model.substring(0, slash);
            } else {
                int dash = model.indexOf('-');
                if (dash > 0) {
                    providerName = model.substring(0, dash);
                }
            }
        }
        if (providerName != null && providers.containsKey(providerName)) {
            modelProviders.put(model, providerName);
        }
    }

    private JsonNode readJson(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Missing seed data file: " + path);
        }
        return objectMapper.readTree(Files.readString(path));
    }

    private record LoadedGatewayConfig(Map<String, ProviderConfig> providers,
                                       Map<String, String> modelProviders,
                                       Map<String, ModelAliasConfig> aliases,
                                       AutoRouteConfig autoRouteConfig) {}
}
