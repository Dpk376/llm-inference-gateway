package com.example.gateway.controller;

import com.example.gateway.model.InferenceRequest;
import com.example.gateway.model.InferenceResponse;
import com.example.gateway.service.InferenceGatewayService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class GatewayController {

    private final InferenceGatewayService gatewayService;

    public GatewayController(InferenceGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @PostMapping("/infer")
    public ResponseEntity<InferenceResponse> infer(@RequestBody InferenceRequest request) {
        return ResponseEntity.ok(gatewayService.route(request));
    }
}
