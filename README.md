# LLM Inference Gateway

A production-grade LLM inference gateway built with **Java 17 / Spring Boot 3** that routes requests across multiple model-serving backends using **load-aware, p99-latency-driven routing** with health checks and circuit breaking.

## Features

| Feature | Detail |
|---------|--------|
| Intelligent routing | Composite score: 70% p99 latency + 30% active request count — always picks the fastest, least-loaded backend |
| Health checks | Periodic `/health` polls; unhealthy backends are silently removed from rotation |
| Circuit breaking | Per-backend Resilience4j circuit breakers — open after configurable failure threshold, preventing cascades |
| Prometheus metrics | `gateway_requests_total`, `gateway_request_latency` (p50/p95/p99), `gateway_active_requests` |
| Grafana dashboard | Pre-wired dashboard for request rate, latency percentiles, active requests, and circuit-breaker state |
| Admin API | Register/deregister backends at runtime without a restart |

## Architecture

```
Client
  │
  ▼
GatewayController  ──►  InferenceGatewayService
                               │
                         P99LatencyRouter
                               │  (picks min composite score)
                         BackendRegistry
                         ┌─────┼─────┐
                      Backend-A  Backend-B  Backend-C
                      (each wrapped in a Resilience4j CircuitBreaker)
                               │
                        BackendHealthService
                        (scheduled health polls)
```

## Quick Start

### Prerequisites
- Java 17+, Maven 3.8+
- Docker & Docker Compose (for full stack with Prometheus + Grafana)

### Run locally
```bash
mvn spring-boot:run
curl -X POST http://localhost:8080/v1/infer \
  -H "Content-Type: application/json" \
  -d '{"model":"llama3-8b","prompt":"Hello world","maxTokens":64}'
```

### Full stack (gateway + Prometheus + Grafana)
```bash
docker-compose up -d
```

| Service | URL |
|---------|-----|
| Gateway | http://localhost:8080 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / admin) |

## API

### `POST /v1/infer`
Route an inference request to the best available backend.

**Request:**
```json
{
  "model": "llama3-8b",
  "prompt": "Explain quantum entanglement.",
  "maxTokens": 256,
  "temperature": 0.7
}
```

**Response:**
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "backend": "backend-a",
  "generatedText": "...",
  "inputTokens": 4,
  "outputTokens": 256,
  "latencyMs": 312
}
```

### `GET /admin/backends`
List all registered backends with health status.

### `POST /admin/backends`
Register a backend at runtime.
```json
{ "id": "backend-c", "url": "http://backend-c:8000", "model": "llama3-8b" }
```

### `DELETE /admin/backends/{id}`
Deregister a backend.

## Configuration

`src/main/resources/application.yml`:

```yaml
gateway:
  routing:
    latency-weight: 0.7       # share of routing score driven by p99 latency
    load-weight: 0.3          # share driven by active request count
    health-check-interval-ms: 10000
  circuit-breaker:
    failure-rate-threshold: 50  # open circuit when 50% of last 20 calls fail
    slow-call-threshold-ms: 5000
    wait-duration-in-open-ms: 30000
  backends:
    - id: backend-a
      url: http://localhost:8001
      model: llama3-8b
    - id: backend-b
      url: http://localhost:8002
      model: llama3-8b
```

## Metrics

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `gateway_requests_total` | Counter | `backend`, `model`, `status` | Total requests routed |
| `gateway_request_latency_seconds` | Timer | `backend` | Latency histogram (p50/p95/p99) |
| `gateway_active_requests` | Gauge | `backend` | In-flight request count per backend |
| `resilience4j_circuitbreaker_state` | Gauge | `name` | Circuit state (0=CLOSED, 1=HALF_OPEN, 2=OPEN) |

## Project Structure

```
src/main/java/com/example/gateway/
├── GatewayApplication.java
├── GatewayStartupRunner.java          # seeds backends from config on startup
├── config/
│   ├── GatewayProperties.java         # typed config binding
│   ├── ResilienceConfig.java          # Resilience4j circuit breaker registry
│   └── WebClientConfig.java           # reactive WebClient bean
├── model/
│   ├── BackendInstance.java           # per-backend state: health, p99, active requests
│   ├── InferenceRequest.java
│   ├── InferenceResponse.java
│   └── BackendRegistration.java
├── routing/
│   ├── RoutingStrategy.java           # interface
│   ├── P99LatencyRouter.java          # composite-score implementation
│   └── BackendRegistry.java           # thread-safe backend store
├── service/
│   ├── InferenceGatewayService.java   # routing + circuit-breaking + metrics
│   └── BackendHealthService.java      # scheduled health polling
└── controller/
    ├── GatewayController.java
    └── AdminController.java
```

## Development

```bash
# Run tests
mvn test

# Build fat JAR
mvn package -DskipTests

# Build Docker image
docker build -t llm-inference-gateway:latest .
```

> **Note:** `callBackend()` in `InferenceGatewayService` is a simulation stub.
> Replace it with a real `WebClient` POST to `backend.getUrl() + "/v1/completions"`
> to connect actual model backends.

## License

MIT
