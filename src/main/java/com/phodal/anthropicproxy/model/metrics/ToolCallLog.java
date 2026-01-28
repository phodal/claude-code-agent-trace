package com.phodal.anthropicproxy.model.metrics;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Tool call level log - each tool invocation in a turn
 */
@Data
@Builder
public class ToolCallLog {
    
    private String toolCallId;
    private String turnId;
    private String name;
    private String argsPreview;  // truncated/sanitized args for display
    private LocalDateTime timestamp;
    private Long durationMs;
    private String status;  // "ok", "error", "timeout"
    private int linesModified;
    private String errorMessage;
    
    /**
     * Create a preview of arguments (truncated and sanitized)
     */
    public static String createArgsPreview(String args, int maxLength) {
        if (args == null || args.isEmpty()) {
            return "";
        }
        // Remove sensitive content patterns
        String sanitized = args
                .replaceAll("\"(password|secret|token|key)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"[REDACTED]\"");
        
        if (sanitized.length() > maxLength) {
            return sanitized.substring(0, maxLength) + "...";
        }
        return sanitized;
    }
}
