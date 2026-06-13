package com.example.gateway.routing;

import com.example.gateway.model.BackendInstance;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BackendRegistry {

    private final Map<String, BackendInstance> backends = new ConcurrentHashMap<>();

    public void register(BackendInstance backend) {
        backends.put(backend.getId(), backend);
    }

    public boolean deregister(String id) {
        return backends.remove(id) != null;
    }

    public Optional<BackendInstance> getById(String id) {
        return Optional.ofNullable(backends.get(id));
    }

    public List<BackendInstance> getHealthyBackendsForModel(String model) {
        return backends.values().stream()
                .filter(BackendInstance::isHealthy)
                .filter(b -> model == null || model.equals(b.getModel()))
                .toList();
    }

    public List<BackendInstance> getAll() {
        return new ArrayList<>(backends.values());
    }
}
