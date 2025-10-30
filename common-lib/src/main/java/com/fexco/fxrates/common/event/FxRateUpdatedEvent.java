package com.fexco.fxrates.common.event;

import com.fexco.fxrates.common.model.FxRate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when an FX rate is updated
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxRateUpdatedEvent {

    /**
     * Unique event ID for idempotency
     */
    private String eventId;

    /**
     * Event type
     */
    private String eventType;

    /**
     * The updated FX rate
     */
    private FxRate fxRate;

    /**
     * Timestamp when the event was created
     */
    private Instant eventTimestamp;

    /**
     * Source service that generated this event
     */
    private String source;

    /**
     * Optional: Previous rate for comparison
     */
    private FxRate previousRate;

    /**
     * Change percentage (if previous rate exists)
     */
    private Double changePercentage;

    public static FxRateUpdatedEvent from(FxRate fxRate, String source) {
        return FxRateUpdatedEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType("FX_RATE_UPDATED")
                .fxRate(fxRate)
                .eventTimestamp(Instant.now())
                .source(source)
                .build();
    }
}
