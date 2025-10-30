package com.fexco.fxrates.websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fexco.fxrates.common.event.FxRateUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Service for managing WebSocket subscriptions
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionService {

    private final ObjectMapper objectMapper;

    // Map of sessionId -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // Map of currencyPair -> Set of sessionIds subscribed to it
    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();

    // Map of sessionId -> Set of currencyPairs
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    /**
     * Register a new WebSocket session
     */
    public void registerSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
        sessionSubscriptions.put(session.getId(), new CopyOnWriteArraySet<>());
        log.info("Registered session: {}. Total sessions: {}", session.getId(), sessions.size());
    }

    /**
     * Unregister a WebSocket session
     */
    public void unregisterSession(WebSocketSession session) {
        String sessionId = session.getId();

        // Remove all subscriptions for this session
        Set<String> pairs = sessionSubscriptions.remove(sessionId);
        if (pairs != null) {
            pairs.forEach(pair -> {
                Set<String> subscribers = subscriptions.get(pair);
                if (subscribers != null) {
                    subscribers.remove(sessionId);
                    if (subscribers.isEmpty()) {
                        subscriptions.remove(pair);
                    }
                }
            });
        }

        sessions.remove(sessionId);
        log.info("Unregistered session: {}. Total sessions: {}", sessionId, sessions.size());
    }

    /**
     * Subscribe session to currency pairs
     */
    public void subscribe(WebSocketSession session, List<String> currencyPairs) {
        String sessionId = session.getId();

        for (String pair : currencyPairs) {
            // Add to subscriptions map
            subscriptions.computeIfAbsent(pair, k -> new CopyOnWriteArraySet<>()).add(sessionId);

            // Add to session subscriptions
            sessionSubscriptions.get(sessionId).add(pair);
        }

        log.debug("Session {} subscribed to {} pairs. Total pairs: {}",
                sessionId, currencyPairs.size(), sessionSubscriptions.get(sessionId).size());
    }

    /**
     * Unsubscribe session from currency pairs
     */
    public void unsubscribe(WebSocketSession session, List<String> currencyPairs) {
        String sessionId = session.getId();

        for (String pair : currencyPairs) {
            Set<String> subscribers = subscriptions.get(pair);
            if (subscribers != null) {
                subscribers.remove(sessionId);
                if (subscribers.isEmpty()) {
                    subscriptions.remove(pair);
                }
            }

            sessionSubscriptions.get(sessionId).remove(pair);
        }

        log.debug("Session {} unsubscribed from {} pairs", sessionId, currencyPairs.size());
    }

    /**
     * Broadcast FX rate update to subscribed sessions
     */
    public void broadcastRateUpdate(FxRateUpdatedEvent event) {
        String currencyPair = event.getFxRate().getCurrencyPair();
        Set<String> subscribers = subscriptions.get(currencyPair);

        if (subscribers == null || subscribers.isEmpty()) {
            log.trace("No subscribers for {}", currencyPair);
            return;
        }

        log.debug("Broadcasting {} update to {} subscribers", currencyPair, subscribers.size());

        String message;
        try {
            Map<String, Object> payload = Map.of(
                    "type", "rateUpdate",
                    "event", event
            );
            message = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Error serializing rate update", e);
            return;
        }

        TextMessage textMessage = new TextMessage(message);
        List<String> failedSessions = new ArrayList<>();

        for (String sessionId : subscribers) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    log.warn("Failed to send message to session {}", sessionId, e);
                    failedSessions.add(sessionId);
                }
            } else {
                failedSessions.add(sessionId);
            }
        }

        // Clean up failed sessions
        failedSessions.forEach(sessionId -> {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null) {
                unregisterSession(session);
            }
        });
    }

    /**
     * Get subscription statistics
     */
    public Map<String, Object> getStats() {
        return Map.of(
                "totalSessions", sessions.size(),
                "totalCurrencyPairs", subscriptions.size(),
                "subscriptionDetails", subscriptions.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().size()
                        ))
        );
    }
}
