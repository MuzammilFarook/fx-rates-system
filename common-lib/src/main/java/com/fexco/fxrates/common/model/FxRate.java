package com.fexco.fxrates.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain model representing an FX exchange rate
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxRate {

    /**
     * Unique identifier for the rate record
     */
    private String id;

    /**
     * Source currency code (ISO 4217) e.g., "EUR"
     */
    private String fromCurrency;

    /**
     * Target currency code (ISO 4217) e.g., "USD"
     */
    private String toCurrency;

    /**
     * Currency pair representation e.g., "EURUSD"
     */
    private String currencyPair;

    /**
     * Exchange rate value
     */
    private BigDecimal rate;

    /**
     * Bid price (buy price)
     */
    private BigDecimal bid;

    /**
     * Ask price (sell price)
     */
    private BigDecimal ask;

    /**
     * Timestamp when the rate was published by the provider
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;

    /**
     * Source provider of the rate e.g., "ExternalFXProvider"
     */
    private String source;

    /**
     * Confidence score (0.0 - 1.0) based on validation
     */
    private Double confidenceScore;

    /**
     * Timestamp when this record was created in our system
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant createdAt;

    /**
     * Timestamp when this record was last updated
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant updatedAt;

    /**
     * Time to live in seconds (for caching purposes)
     */
    private Integer ttlSeconds;

    /**
     * Helper method to generate currency pair from currencies
     */
    public static String generateCurrencyPair(String from, String to) {
        return from + to;
    }

    /**
     * Calculate mid rate from bid and ask
     */
    public BigDecimal getMidRate() {
        if (bid != null && ask != null) {
            return bid.add(ask).divide(BigDecimal.valueOf(2));
        }
        return rate;
    }

    /**
     * Calculate spread (difference between ask and bid)
     */
    public BigDecimal getSpread() {
        if (bid != null && ask != null) {
            return ask.subtract(bid);
        }
        return BigDecimal.ZERO;
    }
}
