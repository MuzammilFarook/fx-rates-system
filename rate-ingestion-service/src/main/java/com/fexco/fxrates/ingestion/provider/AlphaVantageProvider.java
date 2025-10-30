package com.fexco.fxrates.ingestion.provider;

import com.fexco.fxrates.common.model.FxRate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Alpha Vantage FX Rate Provider
 *
 * Professional-grade FX data provider with real bid/ask spreads.
 * Free tier: 500 requests/day
 * Paid tier: Higher limits
 *
 * API Documentation: https://www.alphavantage.co/documentation/
 */
@Component
@ConditionalOnProperty(name = "app.ingestion.provider.type", havingValue = "alpha-vantage")
@Slf4j
public class AlphaVantageProvider implements FxRateProvider {

    private static final String BASE_URL = "https://www.alphavantage.co";
    private static final String FUNCTION = "CURRENCY_EXCHANGE_RATE";

    private final WebClient webClient;
    private final String apiKey;

    public AlphaVantageProvider(
            WebClient.Builder webClientBuilder,
            @Value("${app.ingestion.provider.alpha-vantage.api-key:}") String apiKey
    ) {
        this.apiKey = apiKey;
        this.webClient = webClientBuilder
                .baseUrl(BASE_URL)
                .build();

        if (isAvailable()) {
            log.info("Alpha Vantage provider initialized successfully");
        } else {
            log.warn("Alpha Vantage provider initialized but API key is not configured");
        }
    }

    @Override
    public List<FxRate> fetchRates(List<String> currencyPairs) throws Exception {
        log.info("Fetching {} rates from Alpha Vantage", currencyPairs.size());

        List<FxRate> rates = new ArrayList<>();

        for (String pair : currencyPairs) {
            try {
                // Split currency pair (e.g., "EURUSD" -> "EUR", "USD")
                String fromCurrency = pair.substring(0, 3);
                String toCurrency = pair.substring(3, 6);

                // Fetch from Alpha Vantage API
                FxRate rate = fetchSingleRate(fromCurrency, toCurrency, pair);

                if (rate != null) {
                    rates.add(rate);
                }

                // Rate limiting: Alpha Vantage free tier = 5 API calls/minute
                // Sleep 12 seconds between calls to stay under limit
                if (currencyPairs.indexOf(pair) < currencyPairs.size() - 1) {
                    Thread.sleep(12000);
                }

            } catch (Exception e) {
                log.error("Error fetching rate for {}: {}", pair, e.getMessage());
            }
        }

        log.info("Successfully fetched {}/{} rates from Alpha Vantage",
                rates.size(), currencyPairs.size());

        return rates;
    }

    /**
     * Fetch a single FX rate from Alpha Vantage
     */
    private FxRate fetchSingleRate(String fromCurrency, String toCurrency, String pair) {
        try {
            log.debug("Fetching {}/{} from Alpha Vantage", fromCurrency, toCurrency);

            // API call
            Map<String, Object> response = webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/query")
                            .queryParam("function", FUNCTION)
                            .queryParam("from_currency", fromCurrency)
                            .queryParam("to_currency", toCurrency)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response == null || !response.containsKey("Realtime Currency Exchange Rate")) {
                log.warn("Invalid response from Alpha Vantage for {}", pair);
                return null;
            }

            // Parse response
            Map<String, String> rateData = (Map<String, String>) response.get("Realtime Currency Exchange Rate");

            BigDecimal exchangeRate = new BigDecimal(rateData.get("5. Exchange Rate"));
            BigDecimal bidPrice = new BigDecimal(rateData.get("8. Bid Price"));
            BigDecimal askPrice = new BigDecimal(rateData.get("9. Ask Price"));
            String lastRefreshed = rateData.get("6. Last Refreshed");

            // Build FxRate object
            FxRate fxRate = FxRate.builder()
                    .fromCurrency(fromCurrency)
                    .toCurrency(toCurrency)
                    .currencyPair(pair)
                    .rate(exchangeRate)
                    .bid(bidPrice)
                    .ask(askPrice)
                    .timestamp(Instant.now())
                    .source("Alpha Vantage")
                    .confidenceScore(0.95)
                    .ttlSeconds(5)
                    .createdAt(Instant.now())
                    .build();

            log.debug("Successfully fetched {} = {} (bid: {}, ask: {})",
                    pair, exchangeRate, bidPrice, askPrice);

            return fxRate;

        } catch (Exception e) {
            log.error("Error fetching {} from Alpha Vantage: {}", pair, e.getMessage());
            return null;
        }
    }

    @Override
    public String getProviderName() {
        return "Alpha Vantage";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("demo");
    }

    @Override
    public double getConfidenceScore() {
        return 0.95;
    }
}
