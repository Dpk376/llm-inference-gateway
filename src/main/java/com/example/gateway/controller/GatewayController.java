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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/v1")
public class GatewayController {

    private final InferenceGatewayService gatewayService;

    public GatewayController(InferenceGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @Operation(
            summary = "Perform LLM Inference",
            description = "Routes the inference request to the optimal backend based on p99 latency. Supports JSON and SSE streaming.",
            security = { @SecurityRequirement(name = "ApiKeyAuth") },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful inference response", 
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = com.example.gateway.model.InferenceResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Bad Request (e.g. invalid prompt)", content = @Content),
                    @ApiResponse(responseCode = "401", description = "Unauthorized (Invalid or missing API key)", content = @Content),
                    @ApiResponse(responseCode = "429", description = "Too Many Requests (Rate limit exceeded)", content = @Content)
            }
    )
    @PostMapping(value = "/infer", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
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
