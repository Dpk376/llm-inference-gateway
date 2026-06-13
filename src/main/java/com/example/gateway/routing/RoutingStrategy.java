package com.example.gateway.routing;

import com.example.gateway.model.BackendInstance;

import java.util.Optional;

public interface RoutingStrategy {
    Optional<BackendInstance> selectBackend(String model);
}
