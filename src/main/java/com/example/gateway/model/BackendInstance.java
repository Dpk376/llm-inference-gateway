package com.example.gateway.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Builder
@EqualsAndHashCode(of = "id")
public class BackendInstance {

    private final String id;
    private final String url;
    private final String model;

    @Setter
    @Builder.Default
    private volatile boolean healthy = true;

    @Builder.Default
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    @Builder.Default
    private volatile double p99LatencyMs = 100.0;

    @Builder.Default
    private final Deque<Long> latencyWindow = new ArrayDeque<>(100);

    public synchronized void recordLatency(long latencyMs) {
        if (latencyWindow.size() >= 100) {
            latencyWindow.poll();
        }
        latencyWindow.offer(latencyMs);
        p99LatencyMs = computeP99();
    }

    private double computeP99() {
        long[] sorted = latencyWindow.stream().mapToLong(Long::longValue).sorted().toArray();
        int idx = Math.max(0, (int) Math.ceil(0.99 * sorted.length) - 1);
        return sorted[idx];
    }

    /**
     * Composite routing score: lower is better.
     * Weights are configurable; defaults to 70% latency + 30% load.
     */
    public double routingScore(double latencyWeight, double loadWeight) {
        double normLatency = p99LatencyMs / 10_000.0;
        double normLoad = activeRequests.get() / 100.0;
        return latencyWeight * normLatency + loadWeight * normLoad;
    }

    @Override
    public String toString() {
        return "BackendInstance{id='" + id + "', healthy=" + healthy
                + ", active=" + activeRequests.get() + ", p99=" + p99LatencyMs + "ms}";
    }
}
