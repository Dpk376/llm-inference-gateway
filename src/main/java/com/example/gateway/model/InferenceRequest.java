package com.example.gateway.model;

import lombok.Data;

@Data
public class InferenceRequest {
    private String model;
    private String prompt;
    private int maxTokens = 256;
    private boolean stream = false;
    private double temperature = 0.7;
}
