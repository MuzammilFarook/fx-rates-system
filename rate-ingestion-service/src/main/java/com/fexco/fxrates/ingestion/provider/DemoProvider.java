package com.fexco.fxrates.ingestion.provider;

import com.fexco.fxrates.common.model.FxRate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Demo Provider - Uses free exchangerate-api.com
 *
 * Simple provider for quick demos and testing
 * No authentication required, but:
 * - Limited currency pairs
 * - No real bid/ask spreads (calculated)
 * - May have delayed data
 *
 * Use this for:
 * - Quick demos
 * - Initial testing
 * - When other providers are unavailable
 */
@Component
@ConditionalOnProperty(name = "app.ingestion.provider.type", havingValue = "demo", matchIfMissing = true)
@Slf4j
public class DemoProvider implements FxRateProvider {

    private static final String BASE_URL = "https://api.exchangerate-api.com/v4/latest/";

    private final WebClient webClient;

    public DemoProvider(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(BASE_URL)
                .build();
        log.info("Demo provider initialized (exchangerate-api.com)");
    }

    @Override
    public List<FxRate> fetchRates(List<String> currencyPairs) throws Exception {
        log.info("Fetching {} rates from Demo provider (exchangerate-api.com)", currencyPairs.size());

        // Fetch EUR as base currency
        Map<String, Object> response = webClient
                .get()
                .uri("EUR")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .block();

        if (response == null || !response.containsKey("rates")) {
            throw new Exception("Invalid response from Demo provider");
        }

        List<FxRate> rates = convertToFxRates(response, currencyPairs);

        log.info("Successfully fetched {}/{} rates from Demo provider",
                rates.size(), currencyPairs.size());

        return rates;
    }

    /**
     * Convert API response to FxRate objects
     */
    private List<FxRate> convertToFxRates(Map<String, Object> response, List<String> requestedPairs) {
        List<FxRate> fxRates = new ArrayList<>();
        Map<String, Double> rates = (Map<String, Double>) response.get("rates");
        Instant timestamp = Instant.now();

        for (String pair : requestedPairs) {
            try {
                String from = pair.substring(0, 3);
                String to = pair.substring(3, 6);

                // Calculate cross rate
                Double rate = calculateRate(from, to, rates);

                if (rate != null) {
                    // Calculate mock bid/ask (0.05% spread)
                    BigDecimal midRate = BigDecimal.valueOf(rate);
                    BigDecimal bid = midRate.multiply(BigDecimal.valueOf(0.9995));
                    BigDecimal ask = midRate.multiply(BigDecimal.valueOf(1.0005));

                    FxRate fxRate = FxRate.builder()
                            .fromCurrency(from)
                            .toCurrency(to)
                            .currencyPair(pair)
                            .rate(midRate)
                            .bid(bid)
                            .ask(ask)
                            .timestamp(timestamp)
                            .source("Demo Provider")
                            .confidenceScore(0.85)  // Lower confidence (demo data)
                            .ttlSeconds(5)
                            .createdAt(timestamp)
                            .build();

                    fxRates.add(fxRate);

                    log.debug("Converted rate: {} = {} (bid: {}, ask: {})",
                            pair, midRate, bid, ask);
                }
            } catch (Exception e) {
                log.warn("Error processing pair {}: {}", pair, e.getMessage());
            }
        }

        return fxRates;
    }

    /**
     * Calculate cross rate from EUR-based rates
     */
    private Double calculateRate(String from, String to, Map<String, Double> rates) {
        if (from.equals("EUR")) {
            return rates.get(to);
        } else if (to.equals("EUR")) {
            return 1.0 / rates.get(from);
        } else {
            // Cross rate: from/to = (EUR/to) / (EUR/from)
            Double fromRate = rates.get(from);
            Double toRate = rates.get(to);
            if (fromRate != null && toRate != null) {
                return toRate / fromRate;
            }
        }
        return null;
    }

    @Override
    public String getProviderName() {
        return "Demo Provider (exchangerate-api.com)";
    }

    @Override
    public boolean isAvailable() {
        return true;  // No configuration required
    }

    @Override
    public double getConfidenceScore() {
        return 0.85;  // Lower confidence (demo data)
    }
}
