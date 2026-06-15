package com.example.gateway.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OpenAiChatRequest {
    private String model;
    private List<Message> messages;
    private Integer max_tokens;
    private Boolean stream;
    private Double temperature;

    @Data
    @Builder
    public static class Message {
        private String role;
        private String content;
    }
}
