package com.airtribe.prism.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // Allow the Vite dev server and any local UI origin.
        // For production, replace "*" with explicit allowed origins.
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        // Expose the custom Prism diagnostic headers so the UI can read them.
        config.addExposedHeader("x-prism-provider");
        config.addExposedHeader("x-prism-cache");
        config.addExposedHeader("x-prism-fallback");
        config.addExposedHeader("x-prism-cost-usd");
        config.setAllowCredentials(false); // must be false when origin pattern is "*"

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
