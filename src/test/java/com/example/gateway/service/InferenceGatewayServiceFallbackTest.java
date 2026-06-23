package com.example.gateway.service;

import com.example.gateway.config.GatewayProperties;
import com.example.gateway.model.BackendInstance;
import com.example.gateway.model.InferenceRequest;
import com.example.gateway.routing.RoutingStrategy;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InferenceGatewayServiceFallbackTest {

    @Mock
    private RoutingStrategy router;
    @Mock
    private CircuitBreakerRegistry cbRegistry;
    @Mock
    private WebClient webClient;
    @Mock
    private ReactiveRedisTemplate<String, com.example.gateway.model.InferenceResponse> redisTemplate;
    @Mock
    private AuditLoggerService auditLoggerService;

    private GatewayProperties properties;
    private InferenceGatewayService service;

    @BeforeEach
    void setUp() {
        properties = new GatewayProperties();
        Map<String, String> fallbacks = new HashMap<>();
        fallbacks.put("llama3-8b", "mistral-7b");
        properties.getRouting().setFallbacks(fallbacks);

        service = new InferenceGatewayService(
                router, cbRegistry, new SimpleMeterRegistry(), webClient, redisTemplate, properties, auditLoggerService);
    }

    @Test
    void testSelectBackendWithFallback_SuccessPrimary() throws Exception {
        BackendInstance primary = BackendInstance.builder().id("primary").url("http://primary").model("llama3-8b").build();
        when(router.selectBackend(eq("llama3-8b"), anySet())).thenReturn(Optional.of(primary));

        InferenceRequest req = new InferenceRequest();
        req.setModel("llama3-8b");

        Method method = InferenceGatewayService.class.getDeclaredMethod("selectBackendWithFallback", String.class, Set.class, InferenceRequest.class);
        method.setAccessible(true);

        BackendInstance result = (BackendInstance) method.invoke(service, "llama3-8b", new HashSet<>(), req);
        assertEquals("primary", result.getId());
        assertEquals("llama3-8b", req.getModel());
    }

    @Test
    void testSelectBackendWithFallback_FallbackToMistral() throws Exception {
        when(router.selectBackend(eq("llama3-8b"), anySet())).thenReturn(Optional.empty());

        BackendInstance fallback = BackendInstance.builder().id("fallback").url("http://fallback").model("mistral-7b").build();
        when(router.selectBackend(eq("mistral-7b"), anySet())).thenReturn(Optional.of(fallback));

        InferenceRequest req = new InferenceRequest();
        req.setModel("llama3-8b");

        Method method = InferenceGatewayService.class.getDeclaredMethod("selectBackendWithFallback", String.class, Set.class, InferenceRequest.class);
        method.setAccessible(true);

        BackendInstance result = (BackendInstance) method.invoke(service, "llama3-8b", new HashSet<>(), req);
        assertEquals("fallback", result.getId());
        // The original request model should be updated to the fallback model
        assertEquals("mistral-7b", req.getModel());
    }

    @Test
    void testSelectBackendWithFallback_AllFail() throws Exception {
        when(router.selectBackend(eq("llama3-8b"), anySet())).thenReturn(Optional.empty());
        when(router.selectBackend(eq("mistral-7b"), anySet())).thenReturn(Optional.empty());

        InferenceRequest req = new InferenceRequest();
        req.setModel("llama3-8b");

        Method method = InferenceGatewayService.class.getDeclaredMethod("selectBackendWithFallback", String.class, Set.class, InferenceRequest.class);
        method.setAccessible(true);

        Exception exception = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> {
            method.invoke(service, "llama3-8b", new HashSet<>(), req);
        });

        assertEquals("No healthy backends available for model: llama3-8b (or its fallbacks)", exception.getCause().getMessage());
    }
}
