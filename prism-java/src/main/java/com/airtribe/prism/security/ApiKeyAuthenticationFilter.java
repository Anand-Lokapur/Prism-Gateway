package com.airtribe.prism.security;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class ApiKeyAuthenticationFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // TODO: extract bearer token, validate the virtual key, and short-circuit unauthorized requests.
        return chain.filter(exchange);
    }
}
