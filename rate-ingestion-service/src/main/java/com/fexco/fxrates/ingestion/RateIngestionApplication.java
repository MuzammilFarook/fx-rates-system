package com.fexco.fxrates.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Rate Ingestion Service
 *
 * This service is responsible for:
 * - Fetching FX rates from external providers on a scheduled basis
 * - Validating and enriching rate data
 * - Publishing validated rates to Azure Event Hubs
 * - Monitoring ingestion health and metrics
 */
@SpringBootApplication
@EnableScheduling
public class RateIngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(RateIngestionApplication.class, args);
    }
}
