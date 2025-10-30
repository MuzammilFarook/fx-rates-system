package com.fexco.fxrates.websocket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for WebSocket Service
 *
 * This service provides real-time FX rate updates to partners via WebSocket connections.
 * Features:
 * - Subscribe to specific currency pairs
 * - Consume rate updates from Event Hubs
 * - Broadcast updates to connected clients
 * - Connection management and heartbeat
 */
@SpringBootApplication
public class WebSocketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebSocketServiceApplication.class, args);
    }
}
