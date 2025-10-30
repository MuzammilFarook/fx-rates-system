package com.fexco.fxrates.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for FX Rates API Service
 *
 * This service provides REST and gRPC endpoints for partners to query real-time FX rates.
 * Features:
 * - Multi-layer caching (Redis + API Management)
 * - Circuit breakers for resilience
 * - Integration with Cosmos DB for persistence
 * - Real-time updates via Event Hubs
 * - Comprehensive monitoring with Application Insights
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
public class FxRatesApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FxRatesApiApplication.class, args);
    }
}
