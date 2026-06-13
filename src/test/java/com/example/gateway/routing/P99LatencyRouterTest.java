package com.example.gateway.routing;

import com.example.gateway.config.GatewayProperties;
import com.example.gateway.model.BackendInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class P99LatencyRouterTest {

    private BackendRegistry registry;
    private P99LatencyRouter router;

    @BeforeEach
    void setUp() {
        registry = new BackendRegistry();
        GatewayProperties props = new GatewayProperties();
        router = new P99LatencyRouter(registry, props);
    }

    @Test
    void selectsLowestScoringBackend() {
        BackendInstance fast = BackendInstance.builder().id("fast").url("http://fast:8000").model("llama3").build();
        BackendInstance slow = BackendInstance.builder().id("slow").url("http://slow:8000").model("llama3").build();

        for (int i = 0; i < 20; i++) fast.recordLatency(50);
        for (int i = 0; i < 20; i++) slow.recordLatency(500);

        registry.register(fast);
        registry.register(slow);

        Optional<BackendInstance> selected = router.selectBackend("llama3");
        assertThat(selected).isPresent();
        assertThat(selected.get().getId()).isEqualTo("fast");
    }

    @Test
    void returnsEmptyWhenNoHealthyBackends() {
        BackendInstance unhealthy = BackendInstance.builder()
                .id("b1").url("http://b1:8000").model("llama3").healthy(false).build();
        registry.register(unhealthy);

        assertThat(router.selectBackend("llama3")).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoBackendsMatchModel() {
        BackendInstance backend = BackendInstance.builder()
                .id("b1").url("http://b1:8000").model("gpt-4").build();
        registry.register(backend);

        assertThat(router.selectBackend("llama3")).isEmpty();
    }
}
