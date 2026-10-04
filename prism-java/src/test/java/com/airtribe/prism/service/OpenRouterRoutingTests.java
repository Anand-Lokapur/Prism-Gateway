package com.airtribe.prism.service;

import com.airtribe.prism.config.GatewayRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OpenRouterRoutingTests {

    @Autowired
    private GatewayRegistry registry;

    @Autowired
    private ProviderRouterService routerService;

    @Test
    void openRouterRegisteredAndRoutesConfigured() {
        assertTrue(registry.providersByName().containsKey("openrouter"), "openrouter provider should be registered");
        assertEquals("openrouter", registry.providerForModel("openai/gpt-4o-mini"));
        assertEquals("openrouter", registry.providerForModel("anthropic/claude-sonnet-4"));

        var fastPlan = routerService.resolve("fast", "Hello world");
        assertEquals("openai/gpt-4o-mini", fastPlan.resolvedModel());
        assertEquals("openrouter", registry.providerForModel(fastPlan.resolvedModel()));

        var smartPlan = routerService.resolve("smart", "Design an algorithm");
        assertEquals("anthropic/claude-sonnet-4", smartPlan.resolvedModel());
        assertEquals("openrouter", registry.providerForModel(smartPlan.resolvedModel()));
    }
}
