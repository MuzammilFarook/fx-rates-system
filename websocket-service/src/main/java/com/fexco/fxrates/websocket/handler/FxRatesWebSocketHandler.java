package com.fexco.fxrates.websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fexco.fxrates.websocket.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * WebSocket handler for FX rate subscriptions
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FxRatesWebSocketHandler extends TextWebSocketHandler {

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established: {}", session.getId());
        subscriptionService.registerSession(session);

        // Send welcome message
        Map<String, String> welcome = Map.of(
                "type", "connected",
                "message", "Connected to FX Rates WebSocket",
                "sessionId", session.getId()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcome)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.debug("Received message from {}: {}", session.getId(), message.getPayload());

        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String action = (String) payload.get("action");

            switch (action) {
                case "subscribe":
                    handleSubscribe(session, payload);
                    break;

                case "unsubscribe":
                    handleUnsubscribe(session, payload);
                    break;

                case "ping":
                    handlePing(session);
                    break;

                default:
                    sendError(session, "Unknown action: " + action);
            }

        } catch (Exception e) {
            log.error("Error handling message from {}", session.getId(), e);
            sendError(session, "Invalid message format");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket connection closed: {} with status: {}", session.getId(), status);
        subscriptionService.unregisterSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}", session.getId(), exception);
        subscriptionService.unregisterSession(session);
    }

    /**
     * Handle subscribe request
     */
    private void handleSubscribe(WebSocketSession session, Map<String, Object> payload) throws Exception {
        Object currencyPairsObj = payload.get("currencyPairs");

        if (currencyPairsObj instanceof java.util.List) {
            java.util.List<String> currencyPairs = (java.util.List<String>) currencyPairsObj;
            subscriptionService.subscribe(session, currencyPairs);

            Map<String, Object> response = Map.of(
                    "type", "subscribed",
                    "currencyPairs", currencyPairs,
                    "message", "Successfully subscribed to " + currencyPairs.size() + " currency pairs"
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));

            log.info("Session {} subscribed to: {}", session.getId(), currencyPairs);
        } else {
            sendError(session, "Invalid currencyPairs format");
        }
    }

    /**
     * Handle unsubscribe request
     */
    private void handleUnsubscribe(WebSocketSession session, Map<String, Object> payload) throws Exception {
        Object currencyPairsObj = payload.get("currencyPairs");

        if (currencyPairsObj instanceof java.util.List) {
            java.util.List<String> currencyPairs = (java.util.List<String>) currencyPairsObj;
            subscriptionService.unsubscribe(session, currencyPairs);

            Map<String, Object> response = Map.of(
                    "type", "unsubscribed",
                    "currencyPairs", currencyPairs
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));

            log.info("Session {} unsubscribed from: {}", session.getId(), currencyPairs);
        } else {
            sendError(session, "Invalid currencyPairs format");
        }
    }

    /**
     * Handle ping request
     */
    private void handlePing(WebSocketSession session) throws Exception {
        Map<String, String> pong = Map.of(
                "type", "pong",
                "timestamp", String.valueOf(System.currentTimeMillis())
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
    }

    /**
     * Send error message
     */
    private void sendError(WebSocketSession session, String error) throws Exception {
        Map<String, String> errorMsg = Map.of(
                "type", "error",
                "message", error
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMsg)));
    }
}
