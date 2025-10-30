package com.fexco.fxrates.ingestion.writer;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemResponse;
import com.fexco.fxrates.common.model.FxRate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Service for writing FX rates to Azure Cosmos DB
 */
@Service
@Slf4j
public class CosmosDbWriter {

    private final CosmosContainer container;

    public CosmosDbWriter(CosmosContainer container) {
        this.container = container;
        if (container != null) {
            log.info("CosmosDbWriter initialized successfully");
        } else {
            log.warn("CosmosDbWriter initialized but Cosmos DB container is null. Writes will be skipped.");
        }
    }

    /**
     * Save FX rates to Cosmos DB
     *
     * @param rates List of FX rates to save
     * @return Number of successfully saved rates
     */
    public int saveRates(List<FxRate> rates) {
        if (container == null) {
            log.warn("Cosmos DB container not available. Skipping write of {} rates", rates.size());
            return 0;
        }

        if (rates == null || rates.isEmpty()) {
            log.debug("No rates to save");
            return 0;
        }

        log.info("Writing {} rates to Cosmos DB", rates.size());

        int successCount = 0;
        int failureCount = 0;

        for (FxRate rate : rates) {
            try {
                // Set timestamps
                if (rate.getCreatedAt() == null) {
                    rate.setCreatedAt(Instant.now());
                }
                rate.setUpdatedAt(Instant.now());

                // Generate ID if not present
                if (rate.getId() == null) {
                    rate.setId(generateId(rate));
                }

                // Write to Cosmos DB
                CosmosItemResponse<FxRate> response = container.createItem(rate);

                log.debug("Saved rate: {} (RU charge: {})",
                        rate.getId(),
                        response.getRequestCharge());

                successCount++;

            } catch (Exception e) {
                log.error("Error saving rate to Cosmos DB: {} - {}",
                        rate.getCurrencyPair(),
                        e.getMessage(),
                        e);
                failureCount++;
            }
        }

        log.info("Cosmos DB write completed. Success: {}, Failed: {}, Total RU: calculated",
                successCount, failureCount);

        return successCount;
    }

    /**
     * Generate a unique ID for an FX rate
     * Format: {currencyPair}_{source}_{timestamp}
     *
     * Example: EURUSD_ExternalFXProvider_1705315800000
     */
    private String generateId(FxRate rate) {
        return String.format("%s_%s_%d",
                rate.getCurrencyPair(),
                rate.getSource(),
                rate.getTimestamp().toEpochMilli()
        );
    }
}
