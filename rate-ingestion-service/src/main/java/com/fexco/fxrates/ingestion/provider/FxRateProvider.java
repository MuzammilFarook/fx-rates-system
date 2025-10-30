package com.fexco.fxrates.ingestion.provider;

import com.fexco.fxrates.common.model.FxRate;

import java.util.List;

/**
 * Abstraction layer for FX rate providers
 *
 * This interface allows the system to support multiple providers
 * (Alpha Vantage, Reuters, Bloomberg, etc.) without changing core logic.
 */
public interface FxRateProvider {

    /**
     * Fetch FX rates for specified currency pairs
     *
     * @param currencyPairs List of currency pairs (e.g., ["EURUSD", "GBPUSD"])
     * @return List of FX rates with bid/ask spreads
     * @throws Exception if provider is unavailable
     */
    List<FxRate> fetchRates(List<String> currencyPairs) throws Exception;

    /**
     * Get the provider name
     *
     * @return Provider name (e.g., "Alpha Vantage", "Reuters", "Mock")
     */
    String getProviderName();

    /**
     * Check if the provider is available/configured
     *
     * @return true if provider can be used, false otherwise
     */
    boolean isAvailable();

    /**
     * Get the confidence score for this provider
     * Higher score = more reliable
     *
     * @return Confidence score (0.0 to 1.0)
     */
    default double getConfidenceScore() {
        return 0.95;
    }
}
