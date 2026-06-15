package com.example.gateway.filter;

import com.example.gateway.config.GatewayProperties;
import com.example.gateway.service.RateLimiterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-1)
public class SecurityWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityWebFilter.class);
    
    private final GatewayProperties properties;
    private final RateLimiterService rateLimiterService;

    public SecurityWebFilter(GatewayProperties properties, RateLimiterService rateLimiterService) {
        this.properties = properties;
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // Only protect inference endpoints
        if (!path.startsWith("/v1/infer")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        String apiKeyHeader = exchange.getRequest().getHeaders().getFirst("X-API-Key");
        
        String apiKey = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            apiKey = authHeader.substring(7);
        } else if (apiKeyHeader != null) {
            apiKey = apiKeyHeader;
        }

        if (apiKey == null) {
            return Mono.error(new com.example.gateway.exception.UnauthorizedException("Missing or invalid API Key"));
        }

        final String finalApiKey = apiKey;
        GatewayProperties.TenantConfig tenant = properties.getTenants().stream()
                .filter(t -> t.getApiKey().equals(finalApiKey))
                .findFirst()
                .orElse(null);

        if (tenant == null) {
            return Mono.error(new com.example.gateway.exception.UnauthorizedException("Invalid API Key"));
        }

        return rateLimiterService.isAllowed(tenant.getApiKey(), tenant.getRequestsPerMinute())
                .flatMap(allowed -> {
                    if (!allowed) {
                        log.warn("Rate limit exceeded for tenant tier: {}", tenant.getTier());
                        return Mono.error(new com.example.gateway.exception.RateLimitException("Rate limit exceeded"));
                    }
                    return chain.filter(exchange).contextWrite(ctx -> ctx.put("tenant", tenant.getTier()));
                });
    }
}
