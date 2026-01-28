package com.phodal.anthropicproxy.controller;

import com.phodal.anthropicproxy.model.anthropic.AnthropicRequest;
import com.phodal.anthropicproxy.model.anthropic.AnthropicResponse;
import com.phodal.anthropicproxy.service.MetricsService;
import com.phodal.anthropicproxy.service.OpenAISdkService;
import com.phodal.anthropicproxy.service.UserIdentificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Controller to handle Anthropic API proxy requests
 * Uses official OpenAI Java SDK for API calls
 */
@Slf4j
@RestController
@RequestMapping("/anthropic")
@RequiredArgsConstructor
public class AnthropicProxyController {

    private final OpenAISdkService sdkService;
    private final MetricsService metricsService;
    private final UserIdentificationService userIdentificationService;

    /**
     * Handle Anthropic Messages API requests
     */
    @PostMapping(value = "/v1/messages")
    public ResponseEntity<?> handleMessages(
            @RequestBody AnthropicRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) throws IOException {

        String userId = userIdentificationService.identifyUser(httpRequest);
        String apiKey = userIdentificationService.extractApiKey(httpRequest);
        Map<String, String> headers = userIdentificationService.collectHeaders(httpRequest);

        log.info("Received request from user: {}, model: {}, stream: {}", userId, request.getModel(), request.getStream());

        // Record the request and get turnId
        String turnId = metricsService.recordRequest(userId, request, headers);

        if (apiKey == null || apiKey.isEmpty()) {
            log.error("No API key provided");
            return ResponseEntity.status(401).body(Map.of(
                    "type", "error",
                    "error", Map.of(
                            "type", "authentication_error",
                            "message", "No API key provided"
                    )
            ));
        }

        // Handle streaming vs non-streaming
        if (Boolean.TRUE.equals(request.getStream())) {
            // Debug-only: allow mocking a deterministic SSE stream without upstream calls
            // Enable via header: x-debug-mock-stream: true
            // Or by using a dummy API key (useful for testing with claude CLI env vars)
            if ("true".equalsIgnoreCase(httpRequest.getHeader("x-debug-mock-stream"))
                    || "dummy".equalsIgnoreCase(apiKey)) {
                handleMockStreamingRequest(request, userId, turnId, httpResponse);
            } else {
                handleStreamingRequest(request, userId, turnId, apiKey, httpResponse);
            }
            return null;
        } else {
            return handleNonStreamingRequest(request, userId, turnId, apiKey);
        }
    }

    /**
     * Handle non-streaming request
     */
    private ResponseEntity<?> handleNonStreamingRequest(
            AnthropicRequest request, String userId, String turnId, String apiKey) {
        try {
            AnthropicResponse response = sdkService.sendRequest(request, userId, turnId, apiKey).block();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error handling non-streaming request: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "type", "error",
                    "error", Map.of(
                            "type", "api_error",
                            "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
                    )
            ));
        }
    }

    /**
     * Handle streaming request - writes directly to response
     */
    private void handleStreamingRequest(
            AnthropicRequest request, String userId, String turnId, String apiKey,
            HttpServletResponse httpResponse) throws IOException {

        httpResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        httpResponse.setCharacterEncoding("UTF-8");
        httpResponse.setHeader("Cache-Control", "no-cache");
        httpResponse.setHeader("Connection", "keep-alive");
        httpResponse.setHeader("X-Accel-Buffering", "no");

        PrintWriter writer = httpResponse.getWriter();

        try {
            sdkService.sendStreamingRequest(request, userId, turnId, apiKey)
                    .doOnNext(chunk -> {
                        writer.print(chunk);
                        writer.flush();
                    })
                    .doOnError(e -> {
                        log.error("Error in streaming: {}", e.getMessage());
                        writer.print("event: error\ndata: {\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}}\n\n");
                        writer.flush();
                    })
                    .blockLast();
        } catch (Exception e) {
            log.error("Error handling streaming request: {}", e.getMessage(), e);
            writer.print("event: error\ndata: {\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Unknown error") + "\"}}\n\n");
            writer.flush();
        }
    }

    /**
     * Debug-only deterministic streaming response to reproduce client rendering issues.
     * Enabled via request header: x-debug-mock-stream: true
     */
    private void handleMockStreamingRequest(
            AnthropicRequest request, String userId, String turnId,
            HttpServletResponse httpResponse) throws IOException {

        httpResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        httpResponse.setCharacterEncoding("UTF-8");
        httpResponse.setHeader("Cache-Control", "no-cache");
        httpResponse.setHeader("Connection", "keep-alive");
        httpResponse.setHeader("X-Accel-Buffering", "no");

        PrintWriter writer = httpResponse.getWriter();

        // Simulate a Claude Code flow across multiple turns:
        // 1) Ask to Read README.md
        // 2) Ask to Write README.md
        // 3) Final assistant text
        String messageId = "msg_mock_" + turnId;
        String model = request.getModel() != null ? request.getModel() : "mock";

        String messageStartJson = "{\"type\":\"message_start\",\"message\":{\"id\":\"" + messageId + "\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[],\"model\":\"" + model + "\",\"stop_reason\":null,\"stop_sequence\":null,\"usage\":{\"input_tokens\":0,\"cache_creation_input_tokens\":0,\"cache_read_input_tokens\":0,\"output_tokens\":0}}}";

        int toolResultCount = countToolResults(request);
        String msgStopJson = "{\"type\":\"message_stop\"}";

        try {
            writer.print("event: message_start\ndata: " + messageStartJson + "\n\n");
            // Match Python/Anthropic: always start with empty text block + ping
            writer.print("event: content_block_start\ndata: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n");
            writer.print("event: ping\ndata: {\"type\":\"ping\"}\n\n");
            if (toolResultCount <= 0) {
                // Turn 1: ask to Read README.md
                // Close text block before tool_use
                // Emit a single whitespace delta to avoid Claude Code rendering an empty "step" line.
                writer.print("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"\\u00A0\"}}\n\n");
                writer.print("event: content_block_stop\ndata: {\"type\":\"content_block_stop\",\"index\":0}\n\n");
                String toolStartJson = "{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_mock_read\",\"name\":\"Read\",\"input\":{}}}";
                String toolDeltaJson = "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"path\\\":\\\"README.md\\\"}\"}}";
                String toolStopJson = "{\"type\":\"content_block_stop\",\"index\":1}";
                writer.print("event: content_block_start\ndata: " + toolStartJson + "\n\n");
                writer.print("event: content_block_delta\ndata: " + toolDeltaJson + "\n\n");
                writer.print("event: content_block_stop\ndata: " + toolStopJson + "\n\n");
                String msgDeltaJson = "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":0}}";
                writer.print("event: message_delta\ndata: " + msgDeltaJson + "\n\n");
            } else if (toolResultCount == 1) {
                // Turn 2: ask to Write README.md
                writer.print("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"\\u00A0\"}}\n\n");
                writer.print("event: content_block_stop\ndata: {\"type\":\"content_block_stop\",\"index\":0}\n\n");
                String toolStartJson = "{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_mock_write\",\"name\":\"Write\",\"input\":{}}}";
                String toolDeltaJson = "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"path\\\":\\\"README.md\\\",\\\"content\\\":\\\"(translated content here)\\\"}\"}}";
                String toolStopJson = "{\"type\":\"content_block_stop\",\"index\":1}";
                writer.print("event: content_block_start\ndata: " + toolStartJson + "\n\n");
                writer.print("event: content_block_delta\ndata: " + toolDeltaJson + "\n\n");
                writer.print("event: content_block_stop\ndata: " + toolStopJson + "\n\n");
                String msgDeltaJson = "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":0}}";
                writer.print("event: message_delta\ndata: " + msgDeltaJson + "\n\n");
            } else {
                // Turn 3+: final assistant text
                String textDeltaJson = "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"README 文件已经是英文版本了。\\n\\n# Anthropic Proxy Java\\n\"}}";
                String textStopJson = "{\"type\":\"content_block_stop\",\"index\":0}";
                writer.print("event: content_block_delta\ndata: " + textDeltaJson + "\n\n");
                writer.print("event: content_block_stop\ndata: " + textStopJson + "\n\n");
                String msgDeltaJson = "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":0}}";
                writer.print("event: message_delta\ndata: " + msgDeltaJson + "\n\n");
            }

            writer.print("event: message_stop\ndata: " + msgStopJson + "\n\n");
            writer.print("data: [DONE]\n\n");
            writer.flush();
        } catch (Exception e) {
            log.error("Error handling mock streaming request: {}", e.getMessage(), e);
            writer.print("event: error\ndata: {\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Unknown error") + "\"}}\n\n");
            writer.print("data: [DONE]\n\n");
            writer.flush();
        }
    }

    @SuppressWarnings("unchecked")
    private int countToolResults(AnthropicRequest request) {
        if (request == null || request.getMessages() == null) return 0;
        int count = 0;
        for (var msg : request.getMessages()) {
            Object content = msg != null ? msg.getContent() : null;
            if (!(content instanceof List<?> list)) continue;
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) continue;
                Object type = map.get("type");
                if ("tool_result".equals(type)) count++;
            }
        }
        return count;
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }
}
