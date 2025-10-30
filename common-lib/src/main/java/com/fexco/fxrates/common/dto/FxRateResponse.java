package com.fexco.fxrates.common.dto;

import com.fexco.fxrates.common.model.FxRate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for FX rate queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxRateResponse {

    private FxRate rate;

    private String message;

    private Instant retrievedAt;

    private String source;

    /**
     * Indicates if the response was served from cache
     */
    private Boolean fromCache;

    /**
     * Latency in milliseconds
     */
    private Long latencyMs;

    public static FxRateResponse success(FxRate rate, boolean fromCache) {
        return FxRateResponse.builder()
                .rate(rate)
                .message("Success")
                .retrievedAt(Instant.now())
                .fromCache(fromCache)
                .build();
    }

    public static FxRateResponse error(String message) {
        return FxRateResponse.builder()
                .message(message)
                .retrievedAt(Instant.now())
                .build();
    }
}
