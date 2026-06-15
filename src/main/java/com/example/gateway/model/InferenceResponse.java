package com.example.gateway.model;

import lombok.Builder;
import lombok.Data;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InferenceResponse {
    private String requestId;
    private String backend;
    private String generatedText;
    private int inputTokens;
    private int outputTokens;
    private long latencyMs;
}
