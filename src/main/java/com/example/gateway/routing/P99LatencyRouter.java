package com.example.gateway.routing;

import com.example.gateway.config.GatewayProperties;
import com.example.gateway.model.BackendInstance;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class P99LatencyRouter implements RoutingStrategy {

    private final BackendRegistry registry;
    private final GatewayProperties properties;

    public P99LatencyRouter(BackendRegistry registry, GatewayProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @Override
    public Optional<BackendInstance> selectBackend(String model) {
        List<BackendInstance> candidates = registry.getHealthyBackendsForModel(model);
        if (candidates.isEmpty()) return Optional.empty();

        double lw = properties.getRouting().getLatencyWeight();
        double aw = properties.getRouting().getLoadWeight();

        return candidates.stream()
                .min(Comparator.comparingDouble(b -> b.routingScore(lw, aw)));
    }
}
