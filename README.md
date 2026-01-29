# Anthropic Proxy Java

<div align="center">

![Java](https://img.shields.io/badge/Java-17+-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.7-brightgreen)
![License](https://img.shields.io/badge/License-MIT-yellow)

**Anthropic API proxy service that converts Anthropic API requests to OpenAI format, with monitoring metrics and a visual Dashboard.**

</div>

## Table of Contents

- [Features](#features)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [API Usage](#api-usage)
- [Monitoring Metrics](#monitoring-metrics)
- [Docker Deployment](#docker-deployment)
- [Project Structure](#project-structure)
- [Tech Stack](#tech-stack)
- [License](#license)

## Features

- **API Proxy**: Converts Anthropic Messages API requests to OpenAI Chat Completions format
- **Streaming Support**: Full streaming response support (Server-Sent Events)
- **Tool Calling**: Support for Anthropic tools (Tools/Function Calling)
- **User Identification**: Multiple user identification methods (API Key, Header, IP)
- **Metrics Monitoring**: Prometheus integration + visual Dashboard
- **Session Tracking**: Granular Session/Turn/Message level metrics tracking
- **Tool Call Details**: Records parameters, duration, and code modification lines for each tool call
- **Reactive Architecture**: Non-blocking architecture based on Spring WebFlux

## Quick Start

### Requirements

- Java 17+
- Maven 3.6+

### Build

```bash
mvn clean package -DskipTests
```

### Run

```bash
# Set API Key
export OPENAI_API_KEY=your_api_key

# Start the service
java -jar target/anthropic-proxy-1.0.0-SNAPSHOT.jar
```

The service will start at `http://localhost:8080`.

## Configuration

Configure in `src/main/resources/application.yml`:

```yaml
server:
  port: 8080

proxy:
  openai:
    base-url: https://api.anthropic.com/v1  # OpenAI-compatible endpoint
    api-key: ${OPENAI_API_KEY:}             # API Key
    timeout: 300000                          # Timeout in milliseconds

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
```

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `OPENAI_API_KEY` | Anthropic API key | Yes |
| `SERVER_PORT` | Server port (default: 8080) | No |

## API Usage

### Send a Message

```bash
curl -X POST http://localhost:8080/anthropic/v1/messages \
  -H "Content-Type: application/json" \
  -H "x-api-key: your_api_key" \
  -d '{
    "model": "claude-sonnet-4-20250514",
    "max_tokens": 1024,
    "messages": [
      {"role": "user", "content": "Hello, Claude!"}
    ]
  }'
```

### Streaming Request

```bash
curl -X POST http://localhost:8080/anthropic/v1/messages \
  -H "Content-Type: application/json" \
  -H "x-api-key: your_api_key" \
  -H "anthropic-beta: prompt-caching-1" \
  -d '{
    "model": "claude-sonnet-4-20250514",
    "max_tokens": 1024,
    "stream": true,
    "messages": [
      {"role": "user", "content": "Tell me a story about AI."}
    ]
  }'
```

### Health Check

```bash
curl http://localhost:8080/anthropic/health
```

## Monitoring Metrics

### Dashboard

Access `http://localhost:8080/dashboard` to view the visual monitoring Dashboard with the following tabs:

- **Messages (Turns)**: View details of each message, including tool calls, latency, and token usage
- **Sessions**: User session overview with cumulative metrics and averages
- **Users**: User-level metrics statistics
- **Tool Distribution**: Tool call distribution charts

### API Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /metrics/api/turns` | Get recent Turn list |
| `GET /metrics/api/turns/{turnId}` | Get details of a specific Turn |
| `GET /metrics/api/sessions` | Get active session list |
| `GET /metrics/api/sessions/{sessionId}` | Get session details |
| `GET /metrics/api/sessions/{sessionId}/turns` | Get all messages in a session |
| `GET /metrics/api/users/{userId}/turns` | Get all messages for a user |
| `GET /metrics/api/users/{userId}/sessions` | Get all sessions for a user |
| `GET /actuator/prometheus` | Prometheus metrics endpoint |

### Prometheus Metrics

```bash
curl http://localhost:8080/actuator/prometheus
```

Key metrics:
- `claude_code.requests.total` - Total request count
- `claude_code.requests.by_model` - Request count by model
- `claude_code.tool_calls.total` - Total tool call count
- `claude_code.tool_calls.by_name` - Tool call count by name
- `claude_code.edit_tool_calls.total` - Edit tool call count
- `claude_code.lines_modified.total` - Total lines modified

## Docker Deployment

```bash
# Build the image
docker build -t anthropic-proxy .

# Run the container
docker run -d \
  -p 8080:8080 \
  -e OPENAI_API_KEY=your_api_key \
  --name anthropic-proxy \
  anthropic-proxy
```

## Project Structure

```
anthropic-proxy-java/
├── src/main/java/com/phodal/anthropicproxy/
│   ├── AnthropicProxyApplication.java    # Application entry point
│   ├── config/
│   │   ├── JacksonConfig.java            # JSON configuration
│   │   └── WebConfig.java                # Web configuration
│   ├── controller/
│   │   ├── AnthropicProxyController.java # API controller
│   │   └── MetricsDashboardController.java # Dashboard controller
│   ├── model/
│   │   ├── anthropic/                    # Anthropic API models
│   │   ├── openai/                       # OpenAI API models
│   │   └── metrics/                      # Metrics models
│   │       ├── SessionInfo.java          # Session information
│   │       ├── TurnLog.java              # Turn/Message level metrics
│   │       └── ToolCallLog.java          # Tool call details
│   └── service/
│       ├── MetricsService.java           # Metrics service
│       ├── OpenAISdkService.java         # OpenAI SDK service
│       ├── SessionManager.java           # Session management
│       └── UserIdentificationService.java # User identification service
├── src/main/resources/
│   ├── application.yml                   # Application configuration
│   └── templates/
│       └── dashboard.html                # Dashboard UI
└── pom.xml
```

## Tech Stack

- Spring Boot 3.3.7
- Spring WebFlux
- OpenAI Java SDK 4.16.1
- Micrometer Prometheus
- Thymeleaf
- Lombok

## License

MIT