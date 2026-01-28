package com.phodal.anthropicproxy.service;

import com.phodal.anthropicproxy.model.metrics.SessionInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages user sessions with automatic expiration after inactivity
 */
@Slf4j
@Service
public class SessionManager {
    
    // Session timeout in minutes
    private static final int SESSION_TIMEOUT_MINUTES = 30;
    
    // userId -> current SessionInfo
    private final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();
    
    // sessionId -> SessionInfo (for lookup by sessionId)
    private final Map<String, SessionInfo> sessionsByIdMap = new ConcurrentHashMap<>();
    
    // Historical sessions (keep last N per user for dashboard)
    private final Map<String, LinkedList<SessionInfo>> userSessionHistory = new ConcurrentHashMap<>();
    private static final int MAX_SESSIONS_PER_USER = 50;
    
    /**
     * Get or create a session for user
     * Creates new session if:
     * 1. No existing session
     * 2. Existing session has expired (30min inactivity)
     */
    public SessionInfo getOrCreateSession(String userId) {
        SessionInfo currentSession = activeSessions.get(userId);
        
        if (currentSession == null || isSessionExpired(currentSession)) {
            // Archive old session if exists
            if (currentSession != null) {
                archiveSession(userId, currentSession);
            }
            
            // Create new session
            String sessionId = generateSessionId(userId);
            SessionInfo newSession = new SessionInfo(sessionId, userId);
            activeSessions.put(userId, newSession);
            sessionsByIdMap.put(sessionId, newSession);
            
            log.debug("Created new session {} for user {}", sessionId, userId);
            return newSession;
        }
        
        currentSession.recordActivity();
        return currentSession;
    }
    
    /**
     * Get session by ID
     */
    public SessionInfo getSessionById(String sessionId) {
        return sessionsByIdMap.get(sessionId);
    }
    
    /**
     * Get all sessions for a user (active + historical)
     */
    public List<SessionInfo> getUserSessions(String userId) {
        List<SessionInfo> sessions = new ArrayList<>();
        
        // Add active session
        SessionInfo active = activeSessions.get(userId);
        if (active != null) {
            sessions.add(active);
        }
        
        // Add historical sessions
        LinkedList<SessionInfo> history = userSessionHistory.get(userId);
        if (history != null) {
            sessions.addAll(history);
        }
        
        // Sort by start time descending
        sessions.sort((a, b) -> b.getStartTime().compareTo(a.getStartTime()));
        return sessions;
    }
    
    /**
     * Get all active sessions
     */
    public Collection<SessionInfo> getActiveSessions() {
        return activeSessions.values();
    }
    
    /**
     * Get recent sessions across all users
     */
    public List<SessionInfo> getRecentSessions(int limit) {
        List<SessionInfo> allSessions = new ArrayList<>();
        
        // Collect active sessions
        allSessions.addAll(activeSessions.values());
        
        // Collect historical sessions
        userSessionHistory.values().forEach(allSessions::addAll);
        
        // Sort by last activity and limit
        return allSessions.stream()
                .sorted((a, b) -> b.getLastActivityTime().compareTo(a.getLastActivityTime()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    private boolean isSessionExpired(SessionInfo session) {
        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(session.getLastActivityTime(), now);
        return duration.toMinutes() >= SESSION_TIMEOUT_MINUTES;
    }
    
    private void archiveSession(String userId, SessionInfo session) {
        LinkedList<SessionInfo> history = userSessionHistory.computeIfAbsent(userId, k -> new LinkedList<>());
        history.addFirst(session);
        
        // Keep only recent sessions
        while (history.size() > MAX_SESSIONS_PER_USER) {
            SessionInfo removed = history.removeLast();
            sessionsByIdMap.remove(removed.getSessionId());
        }
        
        log.debug("Archived session {} for user {}", session.getSessionId(), userId);
    }
    
    private String generateSessionId(String userId) {
        return String.format("%s-%d-%s", 
                userId.length() > 8 ? userId.substring(0, 8) : userId,
                System.currentTimeMillis(),
                UUID.randomUUID().toString().substring(0, 8));
    }
    
    /**
     * Cleanup expired sessions periodically
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanupExpiredSessions() {
        List<String> expiredUsers = new ArrayList<>();
        
        activeSessions.forEach((userId, session) -> {
            if (isSessionExpired(session)) {
                expiredUsers.add(userId);
            }
        });
        
        for (String userId : expiredUsers) {
            SessionInfo expired = activeSessions.remove(userId);
            if (expired != null) {
                archiveSession(userId, expired);
                log.debug("Auto-archived expired session for user {}", userId);
            }
        }
    }
}
