package com.airtribe.prism.config;

import com.airtribe.prism.domain.AutoRouteConfig;
import com.airtribe.prism.domain.ModelAliasConfig;
import com.airtribe.prism.domain.ModelPricing;
import com.airtribe.prism.domain.ProviderConfig;
import com.airtribe.prism.domain.VirtualKey;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class GatewayRegistry {

    private final ConcurrentHashMap<String, ModelPricing> pricingByModel = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VirtualKey> virtualKeysByKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ProviderConfig> providersByName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> providerByModel = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ModelAliasConfig> aliasesByName = new ConcurrentHashMap<>();
    private volatile AutoRouteConfig autoRouteConfig = new AutoRouteConfig("fast", "smart");

    public Map<String, ModelPricing> pricingByModel() {
        return Collections.unmodifiableMap(pricingByModel);
    }

    public Map<String, VirtualKey> virtualKeysByKey() {
        return Collections.unmodifiableMap(virtualKeysByKey);
    }

    public Map<String, ProviderConfig> providersByName() {
        return Collections.unmodifiableMap(providersByName);
    }

    public Map<String, String> providerByModel() {
        return Collections.unmodifiableMap(providerByModel);
    }

    public Map<String, ModelAliasConfig> aliasesByName() {
        return Collections.unmodifiableMap(aliasesByName);
    }

    public AutoRouteConfig autoRouteConfig() {
        return autoRouteConfig;
    }

    public void replaceAll(Map<String, ModelPricing> pricing,
                           Map<String, VirtualKey> keys,
                           Map<String, ProviderConfig> providers,
                           Map<String, String> modelProviders,
                           Map<String, ModelAliasConfig> aliases,
                           AutoRouteConfig autoRouteConfig) {
        pricingByModel.clear();
        pricingByModel.putAll(pricing);
        virtualKeysByKey.clear();
        virtualKeysByKey.putAll(keys);
        providersByName.clear();
        providersByName.putAll(providers);
        providerByModel.clear();
        providerByModel.putAll(modelProviders);
        aliasesByName.clear();
        aliasesByName.putAll(aliases);
        if (autoRouteConfig != null) {
            this.autoRouteConfig = autoRouteConfig;
        }
    }

    public void replaceAll(Map<String, ModelPricing> pricing,
                           Map<String, VirtualKey> keys,
                           Map<String, ProviderConfig> providers,
                           Map<String, ModelAliasConfig> aliases,
                           AutoRouteConfig autoRouteConfig) {
        replaceAll(pricing, keys, providers, Collections.emptyMap(), aliases, autoRouteConfig);
    }

    public String providerForModel(String model) {
        String provider = providerByModel.get(model);
        if (provider != null) {
            return provider;
        }

        int slash = model.indexOf('/');
        if (slash > 0) {
            String providerName = model.substring(0, slash);
            if (providersByName.containsKey(providerName)) {
                return providerName;
            }
            if (providersByName.containsKey("openrouter")) {
                return "openrouter";
            }
        }

        int dash = model.indexOf('-');
        if (dash > 0) {
            String providerName = model.substring(0, dash);
            if (providersByName.containsKey(providerName)) {
                return providerName;
            }
        }

        if (providersByName.containsKey("openrouter")) {
            return "openrouter";
        }

        return null;
    }
}
