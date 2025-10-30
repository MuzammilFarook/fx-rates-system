package com.fexco.fxrates.common.dto;

import com.fexco.fxrates.common.model.FxRate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for batch FX rate queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchFxRateResponse {

    private Map<String, FxRate> rates;

    private List<String> failedPairs;

    private Integer totalRequested;

    private Integer totalSuccessful;

    private Integer totalFailed;

    private Instant retrievedAt;

    private Long latencyMs;
}
