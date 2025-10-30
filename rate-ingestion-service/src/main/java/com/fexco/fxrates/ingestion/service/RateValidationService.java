package com.fexco.fxrates.ingestion.service;

import com.fexco.fxrates.common.model.FxRate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for validating and enriching FX rates
 */
@Service
@Slf4j
public class RateValidationService {

    @Value("${app.ingestion.validation.enabled:true}")
    private boolean validationEnabled;

    @Value("${app.ingestion.validation.max-deviation-percent:5.0}")
    private double maxDeviationPercent;

    // In-memory cache of previous rates for validation
    private final Map<String, BigDecimal> previousRates = new ConcurrentHashMap<>();

    /**
     * Validate and enrich FX rates
     */
    public List<FxRate> validateRates(List<FxRate> rates) {
        if (!validationEnabled) {
            log.debug("Validation disabled, returning all rates");
            return rates;
        }

        List<FxRate> validatedRates = new ArrayList<>();

        for (FxRate rate : rates) {
            try {
                // Basic validation
                if (!isValidRate(rate)) {
                    log.warn("Invalid rate data for {}: rate={}", rate.getCurrencyPair(), rate.getRate());
                    continue;
                }

                // Deviation check
                if (hasExcessiveDeviation(rate)) {
                    log.warn("Excessive deviation detected for {}: current={}, previous={}",
                            rate.getCurrencyPair(), rate.getRate(), previousRates.get(rate.getCurrencyPair()));
                    // Still include the rate but with lower confidence
                    rate.setConfidenceScore(0.7);
                }

                // Enrich rate
                enrichRate(rate);

                // Store for next validation
                previousRates.put(rate.getCurrencyPair(), rate.getRate());

                validatedRates.add(rate);

            } catch (Exception e) {
                log.error("Error validating rate for {}", rate.getCurrencyPair(), e);
            }
        }

        log.info("Validated {} out of {} rates", validatedRates.size(), rates.size());
        return validatedRates;
    }

    /**
     * Basic validation checks
     */
    private boolean isValidRate(FxRate rate) {
        if (rate.getRate() == null || rate.getRate().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        if (rate.getCurrencyPair() == null || rate.getCurrencyPair().length() != 6) {
            return false;
        }

        if (rate.getTimestamp() == null) {
            return false;
        }

        return true;
    }

    /**
     * Check for excessive deviation from previous rate
     */
    private boolean hasExcessiveDeviation(FxRate rate) {
        BigDecimal previousRate = previousRates.get(rate.getCurrencyPair());

        if (previousRate == null) {
            return false; // No previous rate to compare
        }

        BigDecimal difference = rate.getRate().subtract(previousRate).abs();
        BigDecimal percentChange = difference.divide(previousRate, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return percentChange.doubleValue() > maxDeviationPercent;
    }

    /**
     * Enrich rate with additional metadata
     */
    private void enrichRate(FxRate rate) {
        // Add default confidence score if not set
        if (rate.getConfidenceScore() == null) {
            rate.setConfidenceScore(0.95);
        }

        // Add TTL if not set
        if (rate.getTtlSeconds() == null) {
            rate.setTtlSeconds(5);
        }
    }
}
