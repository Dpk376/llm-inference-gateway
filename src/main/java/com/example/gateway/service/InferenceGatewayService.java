package com.example.gateway.service;

import com.example.gateway.model.BackendInstance;
import com.example.gateway.model.InferenceRequest;
import com.example.gateway.model.InferenceResponse;
import com.example.gateway.routing.RoutingStrategy;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class InferenceGatewayService {

    private static final Logger log = LoggerFactory.getLogger(InferenceGatewayService.class);

    private final RoutingStrategy router;
    private final CircuitBreakerRegistry cbRegistry;
    private final MeterRegistry meterRegistry;

    public InferenceGatewayService(RoutingStrategy router,
                                   CircuitBreakerRegistry cbRegistry,
                                   MeterRegistry meterRegistry) {
        this.router = router;
        this.cbRegistry = cbRegistry;
        this.meterRegistry = meterRegistry;
    }

    public InferenceResponse route(InferenceRequest request) {
        BackendInstance backend = router.selectBackend(request.getModel())
                .orElseThrow(() -> new IllegalStateException(
                        "No healthy backends available for model: " + request.getModel()));

        backend.getActiveRequests().incrementAndGet();
        long start = System.currentTimeMillis();
        try {
            CircuitBreaker cb = cbRegistry.circuitBreaker(backend.getId());
            InferenceResponse response = cb.executeSupplier(() -> callBackend(backend, request));
            long latencyMs = System.currentTimeMillis() - start;
            backend.recordLatency(latencyMs);
            recordSuccess(backend.getId(), request.getModel(), latencyMs);
            return response;
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            backend.recordLatency(latencyMs);
            recordError(backend.getId(), request.getModel());
            log.error("Backend {} failed after {}ms: {}", backend.getId(), latencyMs, e.getMessage());
            throw e;
        } finally {
            backend.getActiveRequests().decrementAndGet();
        }
    }

    // Simulation stub — replace with a real WebClient POST to backend.getUrl() + "/v1/completions"
    private InferenceResponse callBackend(BackendInstance backend, InferenceRequest request) {
        try {
            Thread.sleep(50 + (long) (Math.random() * 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return InferenceResponse.builder()
                .requestId(UUID.randomUUID().toString())
                .backend(backend.getId())
                .generatedText("[ simulated response from " + backend.getId() + " ]")
                .inputTokens(request.getPrompt().split("\\s+").length)
                .outputTokens(request.getMaxTokens())
                .latencyMs(50 + (long) (Math.random() * 200))
                .build();
    }

    private void recordSuccess(String backendId, String model, long latencyMs) {
        meterRegistry.counter("gateway.requests.total",
                "backend", backendId, "model", model, "status", "success").increment();
        meterRegistry.timer("gateway.request.latency", "backend", backendId)
                .record(Duration.ofMillis(latencyMs));
    }

    private void recordError(String backendId, String model) {
        meterRegistry.counter("gateway.requests.total",
                "backend", backendId, "model", model, "status", "error").increment();
    }
}
