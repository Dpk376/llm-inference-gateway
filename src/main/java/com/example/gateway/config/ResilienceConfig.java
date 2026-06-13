package com.example.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(GatewayProperties props) {
        GatewayProperties.CircuitBreaker cb = props.getCircuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(cb.getFailureRateThreshold())
                .slowCallDurationThreshold(Duration.ofMillis(cb.getSlowCallThresholdMs()))
                .waitDurationInOpenState(Duration.ofMillis(cb.getWaitDurationInOpenMs()))
                .slidingWindowSize(20)
                .build();
        return CircuitBreakerRegistry.of(config);
    }
}
