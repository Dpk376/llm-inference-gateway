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
    private List<BackendConfig> backends = new ArrayList<>();

    @Data
    public static class Routing {
        private double latencyWeight = 0.7;
        private double loadWeight = 0.3;
        private long healthCheckIntervalMs = 10_000L;
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
