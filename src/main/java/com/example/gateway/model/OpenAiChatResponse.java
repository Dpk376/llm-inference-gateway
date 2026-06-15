package com.example.gateway.model;

import lombok.Data;

import java.util.List;

@Data
public class OpenAiChatResponse {
    private String id;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    @Data
    public static class Choice {
        private int index;
        private OpenAiChatRequest.Message message;
        private String finish_reason;
    }

    @Data
    public static class Usage {
        private int prompt_tokens;
        private int completion_tokens;
        private int total_tokens;
    }
}
