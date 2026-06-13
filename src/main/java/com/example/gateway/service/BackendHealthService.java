package com.example.gateway.service;

import com.example.gateway.model.BackendInstance;
import com.example.gateway.routing.BackendRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Service
public class BackendHealthService {

    private static final Logger log = LoggerFactory.getLogger(BackendHealthService.class);

    private final BackendRegistry registry;
    private final WebClient webClient;

    public BackendHealthService(BackendRegistry registry, WebClient webClient) {
        this.registry = registry;
        this.webClient = webClient;
    }

    @Scheduled(fixedDelayString = "${gateway.routing.health-check-interval-ms:10000}")
    public void checkAll() {
        registry.getAll().forEach(this::check);
    }

    private void check(BackendInstance backend) {
        try {
            webClient.get()
                    .uri(backend.getUrl() + "/health")
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(3));
            if (!backend.isHealthy()) {
                log.info("Backend {} recovered", backend.getId());
                backend.setHealthy(true);
            }
        } catch (Exception e) {
            if (backend.isHealthy()) {
                log.warn("Backend {} unhealthy: {}", backend.getId(), e.getMessage());
                backend.setHealthy(false);
            }
        }
    }
}
