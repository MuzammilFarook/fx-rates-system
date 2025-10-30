package com.fexco.fxrates.ingestion.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Factory for selecting the appropriate FX Rate Provider
 *
 * Supports multiple providers with automatic fallback:
 * 1. Alpha Vantage (professional, requires API key)
 * 2. Mock Reuters (realistic simulation, offline)
 * 3. Demo Provider (simple, always available)
 *
 * Configuration via application.yml:
 * app.ingestion.provider.type = alpha-vantage | mock-reuters | demo
 */
@Service
@Slf4j
public class FxProviderFactory {

    @Value("${app.ingestion.provider.type:demo}")
    private String providerType;

    @Value("${app.ingestion.provider.fallback-enabled:true}")
    private boolean fallbackEnabled;

    private final List<FxRateProvider> availableProviders;

    public FxProviderFactory(List<FxRateProvider> availableProviders) {
        this.availableProviders = availableProviders;
        log.info("FxProviderFactory initialized with {} providers", availableProviders.size());
    }

    /**
     * Get the configured provider with automatic fallback
     *
     * @return FxRateProvider instance
     * @throws IllegalStateException if no provider is available
     */
    public FxRateProvider getProvider() {
        log.debug("Selecting provider. Configured type: {}", providerType);

        // Try to get the configured provider
        Optional<FxRateProvider> primaryProvider = availableProviders.stream()
                .filter(FxRateProvider::isAvailable)
                .findFirst();

        if (primaryProvider.isPresent()) {
            log.info("Using provider: {}", primaryProvider.get().getProviderName());
            return primaryProvider.get();
        }

        // Fallback if enabled
        if (fallbackEnabled) {
            log.warn("Primary provider not available. Attempting fallback...");

            Optional<FxRateProvider> fallbackProvider = availableProviders.stream()
                    .filter(FxRateProvider::isAvailable)
                    .findFirst();

            if (fallbackProvider.isPresent()) {
                log.info("Using fallback provider: {}", fallbackProvider.get().getProviderName());
                return fallbackProvider.get();
            }
        }

        // No provider available
        throw new IllegalStateException(
                "No FX rate provider available! Configured: " + providerType +
                        ", Available providers: " + availableProviders.size()
        );
    }

    /**
     * Get provider info for monitoring
     */
    public String getProviderInfo() {
        try {
            FxRateProvider provider = getProvider();
            return String.format("Provider: %s, Confidence: %.2f",
                    provider.getProviderName(),
                    provider.getConfidenceScore());
        } catch (Exception e) {
            return "No provider available";
        }
    }

    /**
     * List all available providers
     */
    public List<String> listAvailableProviders() {
        return availableProviders.stream()
                .filter(FxRateProvider::isAvailable)
                .map(FxRateProvider::getProviderName)
                .toList();
    }
}
