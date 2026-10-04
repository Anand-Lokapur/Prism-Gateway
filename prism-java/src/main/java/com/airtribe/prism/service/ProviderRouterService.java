package com.airtribe.prism.service;

import com.airtribe.prism.config.GatewayRegistry;
import com.airtribe.prism.domain.AutoRoutingDecision;
import com.airtribe.prism.domain.GatewayException;
import com.airtribe.prism.domain.ModelAliasConfig;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProviderRouterService {

    private final GatewayRegistry registry;
    private final AutoRouterService autoRouterService;

    public ProviderRouterService(GatewayRegistry registry, AutoRouterService autoRouterService) {
        this.registry = registry;
        this.autoRouterService = autoRouterService;
    }

    public RoutePlan resolve(String requestedModel, String prompt) {
        String model = requestedModel;
        String routeReason = "explicit model";

        if ("auto".equalsIgnoreCase(requestedModel)) {
            AutoRoutingDecision auto = autoRouterService.classify(prompt);
            model = auto.tier();
            routeReason = auto.reason();
        }

        ModelAliasConfig alias = registry.aliasesByName().get(model);
        if (alias != null) {
            List<String> order = new ArrayList<>();
            order.add(alias.primary());
            order.addAll(alias.fallbacks());
            return new RoutePlan(model, alias.primary(), order, routeReason);
        }

        if (registry.pricingByModel().containsKey(model)) {
            return new RoutePlan(model, model, List.of(model), routeReason);
        }

        throw new GatewayException(HttpStatus.NOT_FOUND, "not_found_error", "not_found_error", "Unknown model or alias: " + requestedModel);
    }

    public boolean isKnownModelOrAlias(String requestedModel) {
        return "auto".equalsIgnoreCase(requestedModel)
                || registry.aliasesByName().containsKey(requestedModel)
                || registry.pricingByModel().containsKey(requestedModel);
    }

    public record RoutePlan(String requestedModel, String resolvedModel, List<String> providerOrder, String routeReason) {}
}
