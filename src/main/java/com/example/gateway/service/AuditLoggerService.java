package com.example.gateway.service;

import com.example.gateway.model.InferenceRequest;
import com.example.gateway.model.InferenceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuditLoggerService {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggerService.class);

    public void logAudit(String tenant, InferenceRequest request, InferenceResponse response, String status, long latencyMs) {
        Mono.fromRunnable(() -> {
            int inputTokens = response != null ? response.getInputTokens() : 0;
            int outputTokens = response != null ? response.getOutputTokens() : 0;
            String backend = response != null ? response.getBackend() : "none";
            String promptSnippet = request.getPrompt().length() > 50 
                    ? request.getPrompt().substring(0, 50).replace("\n", " ") + "..." 
                    : request.getPrompt().replace("\n", " ");

            log.info("[AUDIT] tenant={} | model={} | backend={} | status={} | latency={}ms | tokens_in={} | tokens_out={} | prompt=\"{}\"",
                    tenant, request.getModel(), backend, status, latencyMs, inputTokens, outputTokens, promptSnippet);
        }).subscribe(); // Fire and forget async log
    }
}
