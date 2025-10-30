package com.fexco.fxrates.ingestion.provider;

import com.fexco.fxrates.common.model.FxRate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock Reuters Provider - For offline demos and testing
 *
 * Generates realistic FX rates with proper bid/ask spreads
 * Simulates market volatility and realistic price movements
 *
 * Use this when:
 * - No internet connection
 * - Demo without external dependencies
 * - Testing the system
 */
@Component
@ConditionalOnProperty(name = "app.ingestion.provider.type", havingValue = "mock-reuters")
@Slf4j
public class MockReutersProvider implements FxRateProvider {

    private final Random random = new Random();

    // Store last rates for realistic incremental changes
    private final Map<String, BigDecimal> lastRates = new ConcurrentHashMap<>();

    // Base rates (approximate market rates as of 2024)
    private static final Map<String, BigDecimal> BASE_RATES = Map.of(
            "EURUSD", new BigDecimal("1.0850"),
            "GBPUSD", new BigDecimal("1.2650"),
            "USDJPY", new BigDecimal("156.42"),
            "AUDUSD", new BigDecimal("0.6234"),
            "USDCAD", new BigDecimal("1.3523"),
            "EURGBP", new BigDecimal("0.8575"),
            "EURJPY", new BigDecimal("169.71"),
            "GBPJPY", new BigDecimal("197.87")
    );

    // Typical spreads for each pair (in basis points)
    private static final Map<String, Integer> TYPICAL_SPREADS = Map.of(
            "EURUSD", 2,      // 0.0002 = 2 pips
            "GBPUSD", 3,      // 0.0003 = 3 pips
            "USDJPY", 2,      // 0.02 = 2 pips (JPY pairs)
            "AUDUSD", 3,
            "USDCAD", 3,
            "EURGBP", 2,
            "EURJPY", 3,
            "GBPJPY", 4
    );

    public MockReutersProvider() {
        log.info("Mock Reuters provider initialized - generating realistic synthetic rates");
    }

    @Override
    public List<FxRate> fetchRates(List<String> currencyPairs) throws Exception {
        log.info("Generating {} mock rates (Reuters simulation)", currencyPairs.size());

        // Simulate network latency
        Thread.sleep(100 + random.nextInt(200));

        List<FxRate> rates = new ArrayList<>();

        for (String pair : currencyPairs) {
            FxRate rate = generateRealisticRate(pair);
            if (rate != null) {
                rates.add(rate);
            }
        }

        log.info("Generated {}/{} mock rates successfully", rates.size(), currencyPairs.size());
        return rates;
    }

    /**
     * Generate a realistic FX rate with proper bid/ask spread
     */
    private FxRate generateRealisticRate(String currencyPair) {
        // Get base rate or use default
        BigDecimal baseRate = BASE_RATES.getOrDefault(currencyPair, new BigDecimal("1.0000"));

        // Get last rate for this pair (for realistic incremental changes)
        BigDecimal lastRate = lastRates.getOrDefault(currencyPair, baseRate);

        // Generate realistic price movement (-0.1% to +0.1%)
        double volatility = 0.001; // 0.1%
        double change = (random.nextDouble() - 0.5) * 2 * volatility;

        BigDecimal newRate = lastRate.multiply(BigDecimal.valueOf(1 + change))
                .setScale(5, RoundingMode.HALF_UP);

        // Store for next iteration
        lastRates.put(currencyPair, newRate);

        // Calculate realistic bid/ask spread
        int spreadBasisPoints = TYPICAL_SPREADS.getOrDefault(currencyPair, 3);
        BigDecimal spread = calculateSpread(newRate, spreadBasisPoints, currencyPair);

        BigDecimal bidPrice = newRate.subtract(spread).setScale(5, RoundingMode.HALF_UP);
        BigDecimal askPrice = newRate.add(spread).setScale(5, RoundingMode.HALF_UP);

        // Extract currencies
        String fromCurrency = currencyPair.substring(0, 3);
        String toCurrency = currencyPair.substring(3, 6);

        // Build FxRate
        FxRate fxRate = FxRate.builder()
                .fromCurrency(fromCurrency)
                .toCurrency(toCurrency)
                .currencyPair(currencyPair)
                .rate(newRate)
                .bid(bidPrice)
                .ask(askPrice)
                .timestamp(Instant.now())
                .source("Reuters (Mock)")
                .confidenceScore(0.99)  // High confidence for mock data
                .ttlSeconds(5)
                .createdAt(Instant.now())
                .build();

        log.debug("Generated mock rate: {} = {} (bid: {}, ask: {})",
                currencyPair, newRate, bidPrice, askPrice);

        return fxRate;
    }

    /**
     * Calculate realistic spread based on currency pair
     */
    private BigDecimal calculateSpread(BigDecimal rate, int basisPoints, String pair) {
        // For JPY pairs, spread is in different scale
        if (pair.contains("JPY")) {
            // JPY pairs: spread = rate × (basisPoints / 10000) × 100
            return rate.multiply(BigDecimal.valueOf(basisPoints))
                    .divide(BigDecimal.valueOf(100), 5, RoundingMode.HALF_UP);
        } else {
            // Other pairs: spread = rate × (basisPoints / 10000)
            return rate.multiply(BigDecimal.valueOf(basisPoints))
                    .divide(BigDecimal.valueOf(10000), 5, RoundingMode.HALF_UP);
        }
    }

    @Override
    public String getProviderName() {
        return "Reuters (Mock)";
    }

    @Override
    public boolean isAvailable() {
        return true;  // Always available (offline)
    }

    @Override
    public double getConfidenceScore() {
        return 0.99;  // High confidence for mock data
    }
}
