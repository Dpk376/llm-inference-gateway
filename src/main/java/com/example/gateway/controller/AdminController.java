package com.example.gateway.controller;

import com.example.gateway.model.BackendInstance;
import com.example.gateway.model.BackendRegistration;
import com.example.gateway.routing.BackendRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/backends")
public class AdminController {

    private final BackendRegistry registry;

    public AdminController(BackendRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<BackendInstance> listBackends() {
        return registry.getAll();
    }

    @PostMapping
    public ResponseEntity<Void> registerBackend(@RequestBody BackendRegistration reg) {
        BackendInstance instance = BackendInstance.builder()
                .id(reg.getId())
                .url(reg.getUrl())
                .model(reg.getModel())
                .build();
        registry.register(instance);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deregisterBackend(@PathVariable String id) {
        return registry.deregister(id)
                ? ResponseEntity.noContent().<Void>build()
                : ResponseEntity.notFound().build();
    }
}
