package com.example.gateway.service;

import com.example.gateway.config.GatewayProperties;
import com.example.gateway.exception.PromptFilterException;
import com.example.gateway.model.BackendInstance;
import com.example.gateway.model.InferenceRequest;
import com.example.gateway.model.InferenceResponse;
import com.example.gateway.model.OpenAiChatRequest;
import com.example.gateway.model.OpenAiChatResponse;
import com.example.gateway.routing.RoutingStrategy;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class InferenceGatewayService {

    private static final Logger log = LoggerFactory.getLogger(InferenceGatewayService.class);

    private final RoutingStrategy router;
    private final CircuitBreakerRegistry cbRegistry;
    private final MeterRegistry meterRegistry;
    private final WebClient webClient;
    private final ReactiveRedisTemplate<String, InferenceResponse> redisTemplate;
    private final GatewayProperties properties;
    private final AuditLoggerService auditLoggerService;

    public InferenceGatewayService(RoutingStrategy router,
                                   CircuitBreakerRegistry cbRegistry,
                                   MeterRegistry meterRegistry,
                                   WebClient webClient,
                                   ReactiveRedisTemplate<String, InferenceResponse> redisTemplate,
                                   GatewayProperties properties,
                                   AuditLoggerService auditLoggerService) {
        this.router = router;
        this.cbRegistry = cbRegistry;
        this.meterRegistry = meterRegistry;
        this.webClient = webClient;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.auditLoggerService = auditLoggerService;
    }

    private void validatePrompt(String prompt) {
        String lowerPrompt = prompt.toLowerCase();
        for (String banned : properties.getSecurity().getBannedWords()) {
            if (lowerPrompt.contains(banned.toLowerCase())) {
                throw new PromptFilterException("Prompt contains restricted content");
            }
        }
    }

    private String computeCacheKey(InferenceRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = request.getModel() + "|" + request.getPrompt() + "|" + request.getTemperature() + "|" + request.getMaxTokens();
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return "inference:cache:" + hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public Mono<InferenceResponse> route(InferenceRequest request) {
        return Mono.deferContextual(ctx -> {
            String tenant = ctx.getOrDefault("tenant", "unknown");
            long start = System.currentTimeMillis();

            try {
                validatePrompt(request.getPrompt());
            } catch (PromptFilterException e) {
                auditLoggerService.logAudit(tenant, request, null, "BLOCKED", System.currentTimeMillis() - start);
                return Mono.error(e);
            }

            String cacheKey = computeCacheKey(request);

            return redisTemplate.opsForValue().get(cacheKey)
                    .doOnNext(resp -> {
                        meterRegistry.counter("gateway.cache.hits", "model", request.getModel()).increment();
                        resp.setBackend("cache");
                        auditLoggerService.logAudit(tenant, request, resp, "SUCCESS_CACHE", System.currentTimeMillis() - start);
                    })
                    .switchIfEmpty(Mono.defer(() -> executeRouteWithRetries(request))
                            .doOnNext(resp -> {
                                meterRegistry.counter("gateway.cache.misses", "model", request.getModel()).increment();
                                redisTemplate.opsForValue()
                                        .set(cacheKey, resp, Duration.ofHours(properties.getCache().getTtlHours()))
                                        .subscribe(
                                                success -> log.debug("Cached response for {}", cacheKey),
                                                err -> log.error("Failed to cache response: {}", err.getMessage())
                                        );
                                auditLoggerService.logAudit(tenant, request, resp, "SUCCESS", System.currentTimeMillis() - start);
                            })
                            .doOnError(e -> {
                                if (!(e instanceof PromptFilterException)) {
                                    auditLoggerService.logAudit(tenant, request, null, "ERROR", System.currentTimeMillis() - start);
                                }
                            })
                    );
        });
    }

    private Mono<InferenceResponse> executeRouteWithRetries(InferenceRequest request) {
        Set<String> excludedBackends = new CopyOnWriteArraySet<>();
        long start = System.currentTimeMillis();

        return Mono.defer(() -> {
            BackendInstance backend = router.selectBackend(request.getModel(), excludedBackends)
                    .orElseThrow(() -> new IllegalStateException(
                            "No healthy backends available for model: " + request.getModel()));

            excludedBackends.add(backend.getId());
            CircuitBreaker cb = cbRegistry.circuitBreaker(backend.getId());
            OpenAiChatRequest openAiReq = buildOpenAiRequest(request, false);

            return Mono.defer(() -> {
                backend.getActiveRequests().incrementAndGet();
                return webClient.post()
                        .uri(backend.getUrl() + "/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(openAiReq)
                        .retrieve()
                        .bodyToMono(OpenAiChatResponse.class)
                        .map(openAiResp -> mapResponse(openAiResp, backend.getId(), request, start))
                        .doOnSuccess(resp -> {
                            long latencyMs = System.currentTimeMillis() - start;
                            backend.recordLatency(latencyMs);
                            recordSuccess(backend.getId(), request.getModel(), latencyMs);
                        })
                        .doOnError(e -> {
                            long latencyMs = System.currentTimeMillis() - start;
                            backend.recordLatency(latencyMs);
                            recordError(backend.getId(), request.getModel());
                            log.error("Backend {} failed after {}ms: {}", backend.getId(), latencyMs, e.getMessage());
                        })
                        .doFinally(signalType -> backend.getActiveRequests().decrementAndGet());
            }).transformDeferred(CircuitBreakerOperator.of(cb));
        }).retryWhen(Retry.max(3)
                .filter(this::isRetryable)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }

    public Flux<String> routeStream(InferenceRequest request) {
        return Flux.deferContextual(ctx -> {
            String tenant = ctx.getOrDefault("tenant", "unknown");
            long start = System.currentTimeMillis();

            try {
                validatePrompt(request.getPrompt());
            } catch (PromptFilterException e) {
                auditLoggerService.logAudit(tenant, request, null, "BLOCKED", System.currentTimeMillis() - start);
                return Flux.error(e);
            }

            Set<String> excludedBackends = new CopyOnWriteArraySet<>();

            return Flux.defer(() -> {
                BackendInstance backend = router.selectBackend(request.getModel(), excludedBackends)
                        .orElseThrow(() -> new IllegalStateException(
                                "No healthy backends available for model: " + request.getModel()));

                excludedBackends.add(backend.getId());
                CircuitBreaker cb = cbRegistry.circuitBreaker(backend.getId());
                OpenAiChatRequest openAiReq = buildOpenAiRequest(request, true);

                return Flux.defer(() -> {
                    backend.getActiveRequests().incrementAndGet();
                    return webClient.post()
                            .uri(backend.getUrl() + "/v1/chat/completions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(openAiReq)
                            .retrieve()
                            .bodyToFlux(String.class)
                            .doOnComplete(() -> {
                                long latencyMs = System.currentTimeMillis() - start;
                                backend.recordLatency(latencyMs);
                                recordSuccess(backend.getId(), request.getModel(), latencyMs);
                                
                                // Best effort audit log for stream completion
                                InferenceResponse dummyResponse = InferenceResponse.builder()
                                        .backend(backend.getId())
                                        .build();
                                auditLoggerService.logAudit(tenant, request, dummyResponse, "SUCCESS_STREAM", latencyMs);
                            })
                            .doOnError(e -> {
                                long latencyMs = System.currentTimeMillis() - start;
                                backend.recordLatency(latencyMs);
                                recordError(backend.getId(), request.getModel());
                                log.error("Backend stream {} failed after {}ms: {}", backend.getId(), latencyMs, e.getMessage());
                                auditLoggerService.logAudit(tenant, request, null, "ERROR_STREAM", latencyMs);
                            })
                            .doFinally(signalType -> backend.getActiveRequests().decrementAndGet());
                }).transformDeferred(CircuitBreakerOperator.of(cb));
            }).retryWhen(Retry.max(3)
                    .filter(this::isRetryable)
                    .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
        });
    }

    private boolean isRetryable(Throwable t) {
        if (t instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
            return true;
        }
        if (t instanceof WebClientResponseException ex) {
            return ex.getStatusCode().is5xxServerError() || ex.getStatusCode().value() == 429;
        }
        if (t instanceof java.util.concurrent.TimeoutException || t instanceof io.netty.handler.timeout.TimeoutException) {
            return true;
        }
        if (t instanceof java.net.ConnectException || t instanceof java.net.UnknownHostException) {
            return true;
        }
        return false;
    }

    private OpenAiChatRequest buildOpenAiRequest(InferenceRequest request, boolean stream) {
        return OpenAiChatRequest.builder()
                .model(request.getModel())
                .messages(List.of(OpenAiChatRequest.Message.builder()
                        .role("user")
                        .content(request.getPrompt())
                        .build()))
                .max_tokens(request.getMaxTokens())
                .temperature(request.getTemperature())
                .stream(stream)
                .build();
    }

    private InferenceResponse mapResponse(OpenAiChatResponse openAiResp, String backendId, InferenceRequest req, long start) {
        String text = "";
        if (openAiResp.getChoices() != null && !openAiResp.getChoices().isEmpty()) {
            OpenAiChatRequest.Message msg = openAiResp.getChoices().get(0).getMessage();
            if (msg != null && msg.getContent() != null) {
                text = msg.getContent();
            }
        }

        int inputTokens = req.getPrompt().split("\\s+").length; // Fallback
        int outputTokens = 0;
        if (openAiResp.getUsage() != null) {
            inputTokens = openAiResp.getUsage().getPrompt_tokens();
            outputTokens = openAiResp.getUsage().getCompletion_tokens();
        }

        return InferenceResponse.builder()
                .requestId(openAiResp.getId() != null ? openAiResp.getId() : UUID.randomUUID().toString())
                .backend(backendId)
                .generatedText(text)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .latencyMs(System.currentTimeMillis() - start)
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
