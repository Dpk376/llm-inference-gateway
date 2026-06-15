package com.example.gateway.controller;

import com.example.gateway.model.InferenceRequest;
import com.example.gateway.service.InferenceGatewayService;
import org.reactivestreams.Publisher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class GatewayController {

    private final InferenceGatewayService gatewayService;

    public GatewayController(InferenceGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @PostMapping("/infer")
    public ResponseEntity<Publisher<?>> infer(@RequestBody InferenceRequest request) {
        if (request.isStream()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(gatewayService.routeStream(request));
        } else {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(gatewayService.route(request));
        }
    }
}
