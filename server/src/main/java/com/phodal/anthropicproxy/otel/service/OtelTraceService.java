package com.phodal.anthropicproxy.otel.service;

import com.phodal.anthropicproxy.otel.model.Span;
import com.phodal.anthropicproxy.otel.model.SpanKind;
import com.phodal.anthropicproxy.otel.model.SpanStatus;
import com.phodal.anthropicproxy.otel.model.Trace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing OTEL traces and spans.
 * Handles trace lifecycle, span creation, and trace storage.
 */
@Slf4j
@Service
public class OtelTraceService {
    
    private final Map<String, Trace> activeTraces = new ConcurrentHashMap<>();
    
    private final List<Trace> completedTraces = Collections.synchronizedList(new ArrayList<>());
    
    private static final int MAX_COMPLETED_TRACES = 1000;
    
    /**
     * Generate a unique trace ID (32 hex chars).
     */
    public String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * Generate a unique span ID (16 hex chars).
     */
    public String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    /**
     * Start a new trace.
     * 
     * @param traceId The trace ID (will be generated if null)
     * @return The created Trace object
     */
    public Trace startTrace(String traceId) {
        if (traceId == null) {
            traceId = generateTraceId();
        }
        
        Trace trace = Trace.builder()
                .traceId(traceId)
                .build();
        
        activeTraces.put(traceId, trace);
        log.debug("Started new trace: {}", traceId);
        
        return trace;
    }
    
    /**
     * Start a new span within a trace.
     * 
     * @param traceId The parent trace ID
     * @param name The span name/operation
     * @param kind The span kind (SERVER, CLIENT, etc.)
     * @param parentSpanId The parent span ID (null for root spans)
     * @return The created Span object
     */
    public Span startSpan(String traceId, String name, SpanKind kind, String parentSpanId) {
        Span span = Span.builder()
                .spanId(generateSpanId())
                .traceId(traceId)
                .parentSpanId(parentSpanId)
                .name(name)
                .kind(kind)
                .startTime(Instant.now())
                .status(SpanStatus.unset())
                .build();
        
        // Add to active trace
        Trace trace = activeTraces.get(traceId);
        if (trace != null) {
            trace.addSpan(span);
        }
        
        log.debug("Started span: {} in trace: {}", span.getSpanId(), traceId);
        
        return span;
    }
    
    /**
     * End a span with the given status.
     * 
     * @param span The span to end
     * @param status The final status (use SpanStatus.ok(), SpanStatus.error(), etc.)
     */
    public void endSpan(Span span, SpanStatus status) {
        if (span == null) {
            return;
        }
        span.setEndTime(Instant.now());
        if (status != null) {
            span.setStatus(status);
        } else if (span.getStatus() == null) {
            span.setStatus(SpanStatus.unset());
        }
        
        log.debug("Ended span: {} with status: {}", span.getSpanId(), 
                span.getStatus() != null ? span.getStatus().getCode() : "UNSET");
    }
    
    /**
     * Complete a trace and move it to completed traces.
     * The trace is removed from active traces and stored for later retrieval.
     * 
     * @param traceId The trace ID to complete
     */
    public void completeTrace(String traceId) {
        Trace trace = activeTraces.remove(traceId);
        if (trace != null) {
            synchronized (completedTraces) {
                completedTraces.add(trace);

                // Limit size of completed traces (FIFO eviction)
                while (completedTraces.size() > MAX_COMPLETED_TRACES) {
                    completedTraces.remove(0);
                }
            }
            
            log.debug("Completed trace: {} with {} spans", traceId, trace.getSpanCount());
        }
    }
    
    /**
     * Get trace by ID (from active or completed).
     * 
     * @param traceId The trace ID to look up
     * @return The Trace object, or null if not found
     */
    public Trace getTrace(String traceId) {
        Trace trace = activeTraces.get(traceId);
        if (trace == null) {
            synchronized (completedTraces) {
                for (Trace t : completedTraces) {
                    if (t.getTraceId().equals(traceId)) {
                        trace = t;
                        break;
                    }
                }
            }
        }
        return trace;
    }
    
    /**
     * Get recent completed traces.
     * 
     * @param limit Maximum number of traces to return
     * @return List of recent traces (newest first)
     */
    public List<Trace> getRecentTraces(int limit) {
        synchronized (completedTraces) {
            int size = completedTraces.size();
            int fromIndex = Math.max(0, size - limit);
            List<Trace> recent = new ArrayList<>(completedTraces.subList(fromIndex, size));
            Collections.reverse(recent);
            return recent;
        }
    }

    /**
     * Expose an unmodifiable view of active traces.
     */
    public Map<String, Trace> getActiveTraces() {
        return Collections.unmodifiableMap(activeTraces);
    }

    /**
     * Expose an immutable snapshot of completed traces.
     */
    public List<Trace> getCompletedTraces() {
        synchronized (completedTraces) {
            return Collections.unmodifiableList(new ArrayList<>(completedTraces));
        }
    }
    
    /**
     * Clear all traces (for testing/reset).
     */
    public void clearAllTraces() {
        activeTraces.clear();
        synchronized (completedTraces) {
            completedTraces.clear();
        }
        log.info("Cleared all traces");
    }
}
