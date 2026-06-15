package com.example.gateway.routing;

import com.example.gateway.model.BackendInstance;

import java.util.Optional;
import java.util.Set;

public interface RoutingStrategy {
    Optional<BackendInstance> selectBackend(String model, Set<String> excludedBackendIds);
    
    default Optional<BackendInstance> selectBackend(String model) {
        return selectBackend(model, Set.of());
    }
}
