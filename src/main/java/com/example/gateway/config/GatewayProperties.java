package com.example.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Routing routing = new Routing();
    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    private Cache cache = new Cache();
    private Security security = new Security();
    private List<BackendConfig> backends = new ArrayList<>();
    private List<TenantConfig> tenants = new ArrayList<>();

    @Data
    public static class Security {
        private List<String> bannedWords = new ArrayList<>();
    }

    @Data
    public static class TenantConfig {
        private String apiKey;
        private String tier;
        private int requestsPerMinute;
    }

    @Data
    public static class Cache {
        private int ttlHours = 24;
    }

    @Data
    public static class Routing {
        private double latencyWeight = 0.7;
        private double loadWeight = 0.3;
        private long healthCheckIntervalMs = 10_000L;
        private java.util.Map<String, String> fallbacks = new java.util.HashMap<>();
    }

    @Data
    public static class CircuitBreaker {
        private float failureRateThreshold = 50f;
        private long slowCallThresholdMs = 5_000L;
        private long waitDurationInOpenMs = 30_000L;
    }

    @Data
    public static class BackendConfig {
        private String id;
        private String url;
        private String model;
    }
}
