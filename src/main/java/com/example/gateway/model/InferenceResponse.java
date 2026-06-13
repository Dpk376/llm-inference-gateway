package com.example.gateway.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InferenceResponse {
    private String requestId;
    private String backend;
    private String generatedText;
    private int inputTokens;
    private int outputTokens;
    private long latencyMs;
}
