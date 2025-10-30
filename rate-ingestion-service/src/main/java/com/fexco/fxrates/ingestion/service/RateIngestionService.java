package com.fexco.fxrates.ingestion.service;

import com.fexco.fxrates.common.event.FxRateIngestionEvent;
import com.fexco.fxrates.common.model.FxRate;
import com.fexco.fxrates.ingestion.client.ExternalFxProviderClient;
import com.fexco.fxrates.ingestion.publisher.EventHubPublisher;
import com.fexco.fxrates.ingestion.writer.CosmosDbWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for ingesting FX rates from external providers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateIngestionService {

    private final ExternalFxProviderClient providerClient;
    private final EventHubPublisher eventHubPublisher;
    private final RateValidationService validationService;
    private final CosmosDbWriter cosmosDbWriter;

    @Value("#{'${app.ingestion.currency-pairs}'.split(',')}")
    private List<String> currencyPairs;

    /**
     * Ingest rates from all configured providers
     */
    public void ingestRatesFromAllProviders() {
        log.debug("Starting rate ingestion for {} currency pairs", currencyPairs.size());

        long startTime = System.currentTimeMillis();
        String batchId = UUID.randomUUID().toString();

        try {
            // Fetch rates from external provider
            List<FxRate> rates = providerClient.fetchRates(currencyPairs);

            if (rates.isEmpty()) {
                log.warn("No rates fetched from provider");
                publishIngestionEvent(batchId, "ExternalFXProvider", 0, "FAILED",
                        "No rates fetched", startTime);
                return;
            }

            // Validate rates
            List<FxRate> validatedRates = validationService.validateRates(rates);

            log.info("Validated {}/{} rates", validatedRates.size(), rates.size());

            // Write to Cosmos DB
            int savedCount = cosmosDbWriter.saveRates(validatedRates);
            log.info("Saved {}/{} rates to Cosmos DB", savedCount, validatedRates.size());

            // Publish to Event Hub
            eventHubPublisher.publishRateUpdates(validatedRates);

            // Publish ingestion event
            long duration = System.currentTimeMillis() - startTime;
            publishIngestionEvent(batchId, "ExternalFXProvider", validatedRates.size(),
                    "SUCCESS", null, startTime);

            log.info("Successfully ingested {} rates in {}ms", validatedRates.size(), duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Error during rate ingestion", e);

            publishIngestionEvent(batchId, "ExternalFXProvider", 0, "FAILED",
                    e.getMessage(), startTime);
        }
    }

    /**
     * Publish ingestion event for monitoring
     */
    private void publishIngestionEvent(String batchId, String providerName, int rateCount,
                                        String status, String errorMessage, long startTime) {
        try {
            FxRateIngestionEvent event = FxRateIngestionEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .batchId(batchId)
                    .providerName(providerName)
                    .rateCount(rateCount)
                    .currencyPairs(currencyPairs)
                    .ingestedAt(Instant.now())
                    .status(status)
                    .errorMessage(errorMessage)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();

            log.debug("Ingestion event: {}", event);
            // Could publish this to a separate audit topic

        } catch (Exception e) {
            log.warn("Failed to publish ingestion event", e);
        }
    }
}
