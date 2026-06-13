package com.example.gateway;

import com.example.gateway.config.GatewayProperties;
import com.example.gateway.model.BackendInstance;
import com.example.gateway.routing.BackendRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class GatewayStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GatewayStartupRunner.class);

    private final BackendRegistry registry;
    private final GatewayProperties properties;

    public GatewayStartupRunner(BackendRegistry registry, GatewayProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (GatewayProperties.BackendConfig cfg : properties.getBackends()) {
            BackendInstance instance = BackendInstance.builder()
                    .id(cfg.getId())
                    .url(cfg.getUrl())
                    .model(cfg.getModel())
                    .build();
            registry.register(instance);
            log.info("Registered backend: {} -> {} (model={})", cfg.getId(), cfg.getUrl(), cfg.getModel());
        }
    }
}
