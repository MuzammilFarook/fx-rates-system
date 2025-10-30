package com.fexco.fxrates.ingestion.client;

import com.fexco.fxrates.common.exception.ExternalProviderException;
import com.fexco.fxrates.common.model.FxRate;
import com.fexco.fxrates.ingestion.provider.FxProviderFactory;
import com.fexco.fxrates.ingestion.provider.FxRateProvider;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Client for fetching FX rates from external providers
 *
 * Uses provider abstraction to support multiple providers:
 * - Alpha Vantage (professional, real bid/ask)
 * - Mock Reuters (realistic simulation)
 * - Demo Provider (simple fallback)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ExternalFxProviderClient {

    private final FxProviderFactory providerFactory;

    @CircuitBreaker(name = "externalProvider", fallbackMethod = "fetchRatesFallback")
    @Retry(name = "externalProvider")
    public List<FxRate> fetchRates(List<String> currencyPairs) {
        try {
            // Get the configured provider
            FxRateProvider provider = providerFactory.getProvider();

            log.info("Fetching rates from {} for {} pairs",
                    provider.getProviderName(), currencyPairs.size());

            // Fetch rates using the provider
            List<FxRate> rates = provider.fetchRates(currencyPairs);

            log.info("Successfully fetched {} rates from {} (confidence: {})",
                    rates.size(),
                    provider.getProviderName(),
                    provider.getConfidenceScore());

            return rates;

        } catch (Exception e) {
            log.error("Error fetching rates: {}", e.getMessage());
            throw new ExternalProviderException("FxProvider", "Failed to fetch rates", e);
        }
    }

    /**
     * Fallback method for circuit breaker
     */
    private List<FxRate> fetchRatesFallback(List<String> currencyPairs, Exception ex) {
        log.error("Circuit breaker triggered for FX provider", ex);
        log.warn("Returning empty list. No rates available from provider.");
        return List.of(); // Return empty list in fallback
    }
}
