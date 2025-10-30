package com.fexco.fxrates.common.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for batch FX rate queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchFxRateRequest {

    @NotEmpty(message = "Currency pairs list cannot be empty")
    @Size(max = 100, message = "Maximum 100 currency pairs per request")
    private List<String> currencyPairs;

    /**
     * Optional: Include historical data
     */
    private Boolean includeHistory;
}
