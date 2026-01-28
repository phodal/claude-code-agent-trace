package com.phodal.anthropicproxy.model.metrics;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Session information for a user
 * Session expires after 30 minutes of inactivity
 */
@Data
public class SessionInfo {
    
    private String sessionId;
    private String userId;
    private LocalDateTime startTime;
    private LocalDateTime lastActivityTime;
    
    private final AtomicInteger turnCount = new AtomicInteger(0);
    private final AtomicInteger totalToolCalls = new AtomicInteger(0);
    private final AtomicInteger editToolCalls = new AtomicInteger(0);
    private final AtomicLong totalLinesModified = new AtomicLong(0);
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    
    // Tool usage distribution in this session
    private final Map<String, AtomicInteger> toolUsageMap = new HashMap<>();
    
    public SessionInfo(String sessionId, String userId) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.startTime = LocalDateTime.now();
        this.lastActivityTime = LocalDateTime.now();
    }
    
    public void recordActivity() {
        this.lastActivityTime = LocalDateTime.now();
    }
    
    public void incrementTurns() {
        turnCount.incrementAndGet();
        recordActivity();
    }
    
    public void addToolCall(String toolName) {
        totalToolCalls.incrementAndGet();
        toolUsageMap.computeIfAbsent(toolName, k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    public void addEditToolCall() {
        editToolCalls.incrementAndGet();
    }
    
    public void addLinesModified(int lines) {
        totalLinesModified.addAndGet(lines);
    }
    
    public void addTokens(int prompt, int completion) {
        totalPromptTokens.addAndGet(prompt);
        totalCompletionTokens.addAndGet(completion);
    }
    
    public void addLatency(long latencyMs) {
        totalLatencyMs.addAndGet(latencyMs);
    }
    
    public void incrementErrors() {
        errorCount.incrementAndGet();
    }
    
    public double getAvgToolCallsPerTurn() {
        int turns = turnCount.get();
        return turns > 0 ? (double) totalToolCalls.get() / turns : 0;
    }
    
    public double getAvgLatencyMs() {
        int turns = turnCount.get();
        return turns > 0 ? (double) totalLatencyMs.get() / turns : 0;
    }
    
    public Map<String, Integer> getToolUsageSnapshot() {
        Map<String, Integer> snapshot = new HashMap<>();
        toolUsageMap.forEach((k, v) -> snapshot.put(k, v.get()));
        return snapshot;
    }
}
