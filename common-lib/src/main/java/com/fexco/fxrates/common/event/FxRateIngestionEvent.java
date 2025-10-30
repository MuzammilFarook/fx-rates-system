package com.fexco.fxrates.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Event published when FX rates are ingested from external providers
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxRateIngestionEvent {

    /**
     * Unique event ID
     */
    private String eventId;

    /**
     * Batch ID for tracking
     */
    private String batchId;

    /**
     * Provider name
     */
    private String providerName;

    /**
     * Number of rates ingested
     */
    private Integer rateCount;

    /**
     * Currency pairs processed
     */
    private List<String> currencyPairs;

    /**
     * Ingestion timestamp
     */
    private Instant ingestedAt;

    /**
     * Status: SUCCESS, PARTIAL_SUCCESS, FAILED
     */
    private String status;

    /**
     * Optional error message
     */
    private String errorMessage;

    /**
     * Processing time in milliseconds
     */
    private Long processingTimeMs;
}
